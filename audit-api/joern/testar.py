#!/usr/bin/env python3
"""Roda o taint do Joern nos casos de testes/ e confere o resultado.

Pasta 'vulneravel-<classe>-*': precisa achar ao menos um resultado da classe (sqli, cmdi ou path).
Pasta 'seguro-*': não pode achar nada.
Uso: testar.py [pasta-dos-casos]   (JOERN_HOME aponta para o joern-cli)
"""
import json
import subprocess
import sys
import tempfile
from pathlib import Path

aqui = Path(__file__).resolve().parent
casos = Path(sys.argv[1]) if len(sys.argv) > 1 else aqui / "testes"
CLASSES = {"sqli": "sqli-entre-arquivos", "cmdi": "cmdi-entre-arquivos", "path": "path-traversal-entre-arquivos"}

falhas = 0
for caso in sorted(p for p in casos.iterdir() if p.is_dir()):
    with tempfile.TemporaryDirectory() as tmp:
        saida = Path(tmp) / "saida.sarif"
        r = subprocess.run([str(aqui / "rodar-joern.sh"), str(caso), str(saida)], capture_output=True, text=True)
        if r.returncode != 0 or not saida.exists():
            print(f"FALHA  {caso.name}: o Joern não gerou o SARIF\n{r.stderr}")
            falhas += 1
            continue
        achados = json.loads(saida.read_text())["runs"][0]["results"]
    ids = sorted({a["ruleId"] for a in achados})
    if caso.name.startswith("seguro-"):
        ok = not achados
        esperado = "nenhum achado"
    else:
        classe = caso.name.split("-")[1]
        ok = CLASSES[classe] in ids
        esperado = CLASSES[classe]
    print(("ok     " if ok else "FALHA  ") + f"{caso.name}: esperado {esperado}; obtido {ids or 'nenhum'}")
    falhas += 0 if ok else 1
sys.exit(1 if falhas else 0)
