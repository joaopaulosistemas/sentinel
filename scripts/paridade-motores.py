#!/usr/bin/env python3
"""Confere que o Opengrep e o Semgrep devolvem os mesmos achados para as regras dos pacotes.

Uso: paridade-motores.py [pasta-dos-pacotes]   (padrão: audit-rules/packs)

Roda os dois motores sobre os arquivos de teste de cada pacote (regras de SQL/XML ficam de fora: têm verificador
próprio) e compara o conjunto (regra, arquivo, linha). Sai com 1 se houver diferença ou erro de leitura de regra
em algum dos motores. Serve de guarda contra o afastamento dos dois projetos: o Sentinel roda o Opengrep, e os
testes `semgrep --test` das regras só valem se os dois enxergarem o mesmo.
"""
import json, os, shutil, subprocess, sys, tempfile

AMBIENTE = dict(os.environ, LC_ALL="C.UTF-8", PYTHONUTF8="1", SEMGREP_SEND_METRICS="off")


def preparar(pacotes, regras, corpus):
    for nome in sorted(os.listdir(pacotes)):
        base = os.path.join(pacotes, nome)
        r, t = os.path.join(base, "rules"), os.path.join(base, "tests")
        if not (os.path.isdir(r) and os.path.isdir(t)):
            continue
        os.makedirs(os.path.join(regras, nome))
        for f in os.listdir(r):
            if f.endswith((".yml", ".yaml")) and not f.endswith("-sql.yml"):
                shutil.copy(os.path.join(r, f), os.path.join(regras, nome, f))
        # a pasta não pode se chamar "tests": os dois motores ignoram esse nome por padrão
        shutil.copytree(t, os.path.join(corpus, nome))


def rodar(binario, regras, corpus):
    cmd = [binario, "--config", regras, "--json", "--quiet", "--disable-version-check"]
    if binario == "semgrep":
        cmd.append("--metrics=off")
    p = subprocess.run(cmd + [corpus], capture_output=True, text=True, env=AMBIENTE)
    try:
        d = json.loads(p.stdout)
    except ValueError:
        sys.exit(f"{binario} não devolveu JSON (código {p.returncode}): {p.stderr[-400:]}")
    achados = {(r["check_id"].split(".")[-1], os.path.relpath(r["path"], corpus), r["start"]["line"]) for r in d["results"]}
    erros = [e.get("message", "")[:160] for e in d.get("errors", []) if e.get("level") == "error"]
    return achados, erros


def main():
    pacotes = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), "..", "audit-rules", "packs")
    for b in ("opengrep", "semgrep"):
        if not shutil.which(b):
            sys.exit(f"{b} não está no PATH")
    with tempfile.TemporaryDirectory() as tmp:
        regras, corpus = os.path.join(tmp, "regras"), os.path.join(tmp, "corpus")
        os.makedirs(regras), os.makedirs(corpus)
        preparar(pacotes, regras, corpus)
        og, og_erros = rodar("opengrep", regras, corpus)
        sg, sg_erros = rodar("semgrep", regras, corpus)
    print(f"Opengrep: {len(og)} achados, {len(og_erros)} erro(s) de regra | Semgrep: {len(sg)} achados, {len(sg_erros)} erro(s) de regra")
    falhou = False
    for nome, erros in (("Opengrep", og_erros), ("Semgrep", sg_erros)):
        for e in erros:
            print(f"::error title=Regra não lida pelo {nome}::{e.splitlines()[0] if e else ''}")
            falhou = True
    for nome, so in (("só no Opengrep", og - sg), ("só no Semgrep", sg - og)):
        for regra, arquivo, linha in sorted(so):
            print(f"::error title=Motores divergem ({nome})::{regra} em {arquivo}:{linha}")
            falhou = True
    if not falhou:
        print("Os dois motores concordam em todos os achados.")
    return 1 if falhou else 0


if __name__ == "__main__":
    sys.exit(main())
