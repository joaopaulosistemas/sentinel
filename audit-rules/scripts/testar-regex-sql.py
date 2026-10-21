#!/usr/bin/env python3
"""Testa regras Semgrep de SQL/XML (languages: [regex]), que o `semgrep --test` não avalia.

Uso: testar-regex-sql.py <arquivo-de-regras.yml> <pasta-de-testes>

Cada arquivo de teste usa comentários na linha anterior à linha casada:
  -- ruleid: <id>   |  -- ok: <id>        (SQL)
  <!-- ruleid: <id> -->  |  <!-- ok: <id> -->   (XML)
Falha (exit 1) se um `ruleid` não casar, ou se uma regra casar numa linha sem `ruleid`.
"""
import json, os, re, subprocess, sys

ANOT = re.compile(r"(?:--|<!--)\s*(ruleid|ok):\s*([\w.\-]+)")


def main():
    regras, pasta = sys.argv[1], sys.argv[2]
    env = dict(os.environ, SEMGREP_SEND_METRICS="off")
    saida = subprocess.run(
        ["semgrep", "--json", "--metrics=off", "--disable-version-check", "--config", regras, pasta],
        capture_output=True, text=True, env=env)
    dados = json.loads(saida.stdout)
    achados = {}
    for r in dados["results"]:
        regra = r["check_id"].split(".")[-1]
        achados.setdefault((os.path.abspath(r["path"]), regra), set()).add(r["start"]["line"])
    erros = 0
    esperados = {}
    for nome in sorted(os.listdir(pasta)):
        caminho = os.path.abspath(os.path.join(pasta, nome))
        if not os.path.isfile(caminho):
            continue
        linhas = open(caminho, encoding="utf-8").read().split("\n")
        for i, l in enumerate(linhas):
            m = ANOT.search(l)
            if m and m.group(1) == "ruleid":
                esperados.setdefault((caminho, m.group(2)), set()).add(i + 2)
    for chave, linhas in esperados.items():
        faltou = linhas - achados.get(chave, set())
        for ln in sorted(faltou):
            print(f"FALHOU: {os.path.basename(chave[0])}:{ln} regra {chave[1]} deveria casar")
            erros += 1
    for chave, linhas in achados.items():
        a_mais = linhas - esperados.get(chave, set())
        for ln in sorted(a_mais):
            print(f"FALHOU: {os.path.basename(chave[0])}:{ln} regra {chave[1]} casou sem `ruleid`")
            erros += 1
    total = sum(len(v) for v in esperados.values())
    print(f"{total - erros if erros < total else 0}/{total} casos esperados conferem" if erros == 0 else f"{erros} divergência(s)")
    sys.exit(1 if erros else 0)


main()
