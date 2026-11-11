#!/usr/bin/env python3
"""Roda os testes Semgrep de UM pacote de regras e garante que NENHUMA regra ficou sem ser executada.

Uso: testar-pacote.py <pasta-do-pacote>        (ex.: audit-rules/packs/core-security)

O `semgrep --test` só liga um arquivo de teste ao arquivo de regras de mesmo nome (`x.yml` <-> `x.java`) e ignora em
silêncio o resto: teste "órfão" (sem `x.yml`) e teste de alvo YAML (`x.yml` precisa virar `x.test.yaml`) nunca rodavam, e
o CI mostrava "passou". Este script prepara uma configuração temporária em que:
  - arquivo de teste sem arquivo de regras de mesmo nome é pareado com TODAS as regras do pacote;
  - arquivo de teste YAML é copiado como `<nome>.test.yaml`;
e depois falha se alguma regra do pacote não aparece entre as verificações executadas, se não tem nenhum `ruleid:`
(caso que deve casar) ou se não tem nenhum `ok:` (caso parecido que NÃO deve casar: sem ele ninguém percebe quando a
regra passa a marcar código correto).
Regras de arquivos `*-sql.yml` têm verificador próprio (testar-regex-sql.py) e ficam fora daqui, junto com os testes
que só falam delas. Arquivo de teste com apenas `ok:` (sem nenhum `ruleid:`) não é executado pelo `semgrep --test`
("No unit tests found"); aqui ele é conferido à parte: nenhuma regra citada em `ok:` pode casar nele.
"""
import glob
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile

import yaml

ANOTACAO = re.compile(r"(ruleid|ok):\s*([\w.\-]+)")


def ids_do_arquivo(caminho):
    return [r["id"] for r in (yaml.safe_load(open(caminho, encoding="utf-8")) or {}).get("rules", []) or []]


def anotacoes(texto):
    """Devolve (ids com `ruleid:`, ids com `ok:`) de um arquivo de teste."""
    achados = ANOTACAO.findall(texto)
    return ({i.split(".")[-1] for k, i in achados if k == "ruleid"}, {i.split(".")[-1] for k, i in achados if k == "ok"})


def prepara(pacote, cfg, testes, negativos):
    """Monta as pastas temporárias; devolve (ids_das_regras, ids_com_ruleid, ids_com_ok, {arquivo_so_ok: ids_ok})."""
    todos = [f for f in sorted(glob.glob(os.path.join(pacote, "rules", "*.yml")))]
    sql_ids = {i for f in todos if f.endswith("-sql.yml") for i in ids_do_arquivo(f)}
    arquivos = [f for f in todos if not f.endswith("-sql.yml")]
    todas, por_arquivo = [], {}
    for f in arquivos:
        shutil.copy(f, cfg)
        stem = os.path.basename(f)[:-4]
        por_arquivo[stem] = ids_do_arquivo(f)
        todas += (yaml.safe_load(open(f, encoding="utf-8")) or {}).get("rules", []) or []
    anotados, com_ok, so_negativos = set(), set(), {}
    for nome in sorted(os.listdir(os.path.join(pacote, "tests"))):
        origem = os.path.join(pacote, "tests", nome)
        if not os.path.isfile(origem):
            continue
        stem, ext = os.path.splitext(nome)
        positivos, oks = anotacoes(open(origem, encoding="utf-8", errors="ignore").read())
        com_ok |= oks
        if (positivos | oks) and (positivos | oks) <= sql_ids:
            continue                                       # verificado por testar-regex-sql.py
        if oks and not positivos:
            shutil.copy(origem, os.path.join(negativos, nome))
            so_negativos[nome] = oks
            continue
        anotados |= positivos
        if stem not in por_arquivo:                       # teste órfão: vale para todas as regras do pacote
            with open(os.path.join(cfg, stem + ".yml"), "w", encoding="utf-8") as fh:
                yaml.safe_dump({"rules": todas}, fh, allow_unicode=True, sort_keys=False)
            por_arquivo[stem] = []
        destino = f"{stem}.test{ext}" if ext in (".yml", ".yaml") else nome
        shutil.copy(origem, os.path.join(testes, destino))
    ids = [i for stem, lista in por_arquivo.items() for i in lista]
    return ids, anotados, com_ok, so_negativos


def confere_negativos(cfg, negativos, esperados):
    """Roda as regras nos arquivos só-`ok:` e devolve os problemas (regra citada em `ok:` que casou)."""
    if not esperados:
        return []
    env = dict(os.environ, SEMGREP_SEND_METRICS="off")
    saida = subprocess.run(["semgrep", "--json", "--metrics=off", "--disable-version-check", "--config", cfg, negativos],
                           capture_output=True, text=True, env=env)
    try:
        achados = json.loads(saida.stdout).get("results", [])
    except ValueError:
        return [f"o Semgrep não devolveu JSON ao conferir os casos `ok:` ({saida.stderr[-300:]})"]
    problemas = []
    for r in achados:
        arquivo, regra = os.path.basename(r["path"]), r["check_id"].split(".")[-1]
        if regra in esperados.get(arquivo, set()):
            problemas.append(f"{regra} casou em {arquivo}:{r['start']['line']}, que só tem casos `ok:`")
    return problemas


def executa(cfg, testes):
    env = dict(os.environ, SEMGREP_SEND_METRICS="off")
    saida = subprocess.run(["semgrep", "--test", "--json", "--metrics=off", "--disable-version-check",
                            "--config", cfg, testes], capture_output=True, text=True, env=env)
    try:
        dados = json.loads(saida.stdout)
    except ValueError:
        print(saida.stdout[-2000:], saida.stderr[-2000:])
        return None, None
    executadas, falhas = set(), []
    for arquivo, v in (dados.get("results") or {}).items():
        for cid, c in (v.get("checks") or {}).items():
            executadas.add(cid.split(".")[-1])
            if not c.get("passed", False):
                falhas.append((cid.split(".")[-1], arquivo, c.get("matches", {}) and {k: m for k, m in c["matches"].items()}))
    return executadas, falhas


def verifica(pacote):
    """Devolve a lista de problemas do pacote (vazia = ok)."""
    problemas = []
    if not os.path.isdir(os.path.join(pacote, "tests")) or not os.path.isdir(os.path.join(pacote, "rules")):
        return problemas
    with tempfile.TemporaryDirectory() as tmp:
        cfg, testes, negativos = (os.path.join(tmp, d) for d in ("cfg", "casos", "negativos"))   # sem "tests/": o Semgrep ignora essa pasta
        for d in (cfg, testes, negativos):
            os.makedirs(d)
        ids, anotados, com_ok, so_negativos = prepara(pacote, cfg, testes, negativos)
        executadas, falhas = executa(cfg, testes)
        problemas += [f"{pacote}: {p}" for p in confere_negativos(cfg, negativos, so_negativos)]
    if executadas is None:
        return [f"{pacote}: o Semgrep não devolveu JSON"]
    for cid, arquivo, detalhe in falhas:
        problemas.append(f"{pacote}: teste falhou: {cid} em {os.path.basename(arquivo)} {detalhe}")
    for i in sorted(set(ids) - executadas):
        problemas.append(f"{pacote}: a regra {i} não foi executada por nenhum teste")
    for i in sorted(set(ids) - anotados):
        problemas.append(f"{pacote}: a regra {i} não tem nenhum caso `ruleid:` nos testes")
    for i in sorted(set(ids) - com_ok):
        problemas.append(f"{pacote}: a regra {i} não tem nenhum caso `ok:` (código parecido que não deve ser marcado)")
    return problemas


def main():
    if len(sys.argv) != 2:
        print(__doc__)
        return 2
    problemas = verifica(sys.argv[1].rstrip("/"))
    for p in problemas:
        print(" -", p)
    print(f"{sys.argv[1]}: {len(problemas)} problema(s)")
    return 1 if problemas else 0


if __name__ == "__main__":
    sys.exit(main())
