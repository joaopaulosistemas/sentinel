#!/usr/bin/env python3
"""Testa regras Semgrep de SQL/XML (languages: [regex]), que o `semgrep --test` não avalia.

Uso: testar-regex-sql.py <arquivo-de-regras.yml> <pasta-de-testes>

Cada arquivo de teste usa comentários na linha anterior à linha casada:
  -- ruleid: <id>   |  -- ok: <id>        (SQL)
  <!-- ruleid: <id> -->  |  <!-- ok: <id> -->   (XML)
Falha (exit 1) se um `ruleid` não casar, ou se uma regra casar numa linha sem `ruleid`.
"""
import json, os, re, shutil, subprocess, sys, tempfile

ANOT = re.compile(r"(?:--|<!--)\s*(ruleid|ok):\s*([\w.\-]+)")


def main():
    regras, pasta = sys.argv[1], sys.argv[2]
    env = dict(os.environ, SEMGREP_SEND_METRICS="off")
    # O Semgrep ignora por padrão pastas chamadas tests/ dentro de um repositório git; por isso os casos
    # são copiados para uma pasta temporária com outro nome antes da análise.
    temporaria = tempfile.mkdtemp(prefix="casos-")
    try:
        for nome in os.listdir(pasta):
            origem = os.path.join(pasta, nome)
            if os.path.isfile(origem):
                shutil.copy(origem, os.path.join(temporaria, nome))
        saida = subprocess.run(
            ["semgrep", "--json", "--metrics=off", "--disable-version-check", "--config", regras, temporaria],
            capture_output=True, text=True, env=env)
    finally:
        shutil.rmtree(temporaria, ignore_errors=True)
    dados = json.loads(saida.stdout)
    if dados.get("errors"):
        for e in dados["errors"][:5]:
            print("ERRO DO SEMGREP:", str(e.get("message", e))[:300])
    print(f"semgrep {dados.get('version', '?')}: {len(dados['results'])} achado(s)")
    achados = {}
    for r in dados["results"]:
        regra = r["check_id"].split(".")[-1]
        achados.setdefault((os.path.basename(r["path"]), regra), set()).add(r["start"]["line"])
    erros = 0
    esperados = {}
    for nome in sorted(os.listdir(pasta)):
        caminho = os.path.join(pasta, nome)
        if not os.path.isfile(caminho):
            continue
        linhas = open(caminho, encoding="utf-8").read().split("\n")
        for i, l in enumerate(linhas):
            m = ANOT.search(l)
            if m and m.group(1) == "ruleid":
                esperados.setdefault((nome, m.group(2)), set()).add(i + 2)
    for chave, linhas in esperados.items():
        faltou = linhas - achados.get(chave, set())
        for ln in sorted(faltou):
            print(f"FALHOU: {chave[0]}:{ln} regra {chave[1]} deveria casar")
            erros += 1
    for chave, linhas in achados.items():
        a_mais = linhas - esperados.get(chave, set())
        for ln in sorted(a_mais):
            print(f"FALHOU: {chave[0]}:{ln} regra {chave[1]} casou sem `ruleid`")
            erros += 1
    total = sum(len(v) for v in esperados.values())
    print(f"{total - erros if erros < total else 0}/{total} casos esperados conferem" if erros == 0 else f"{erros} divergência(s)")
    sys.exit(1 if erros else 0)


main()
