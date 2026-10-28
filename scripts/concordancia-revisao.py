#!/usr/bin/env python3
"""Concordância entre o veredito do agente e o veredito humano em docs/evolucao/revisao-humana.md.

Uso: concordancia-revisao.py [arquivo.md] [--minimo 0.8]

Só conta linhas em que a pessoa preencheu o veredito (VP, FP ou ?). Linhas vazias são "pendentes" e não entram no cálculo.
Sai com 1 se a concordância (entre as linhas preenchidas) ficar abaixo de --minimo: nesse caso o precisao.md
deve ser lido com desconfiança e corrigido nas linhas divergentes (o script lista cada uma).
"""
import argparse, os, re, sys

PADRAO = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "docs", "evolucao", "revisao-humana.md")
VEREDITOS = {"VP", "FP", "?"}


def normaliza(v):
    v = v.strip().strip("*`").upper()
    return "?" if v in ("D", "DUVIDOSO", "DÚVIDA") else v


def ler_linhas(caminho):
    """[(rodada, repositório, regra, local, agente, humano)] das linhas da tabela."""
    out = []
    for linha in open(caminho, encoding="utf-8"):
        if not linha.startswith("| 20"):
            continue
        c = [x.strip() for x in linha.strip().strip("|").split("|")]
        if len(c) < 6:
            continue
        out.append((c[0], c[1], c[2], c[3], normaliza(c[4]), normaliza(c[5])))
    return out


def calcula(linhas):
    preenchidas = [l for l in linhas if l[5] in VEREDITOS]
    iguais = [l for l in preenchidas if l[4] == l[5]]
    divergentes = [l for l in preenchidas if l[4] != l[5]]
    return {"total": len(linhas), "preenchidas": len(preenchidas), "pendentes": len(linhas) - len(preenchidas),
            "iguais": len(iguais), "divergentes": divergentes,
            "taxa": (len(iguais) / len(preenchidas)) if preenchidas else None}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("arquivo", nargs="?", default=PADRAO)
    ap.add_argument("--minimo", type=float, default=0.8)
    a = ap.parse_args()
    r = calcula(ler_linhas(a.arquivo))
    print("Linhas: %d | preenchidas pela pessoa: %d | pendentes: %d" % (r["total"], r["preenchidas"], r["pendentes"]))
    if r["taxa"] is None:
        print("Concordância: não calculável (nenhum veredito humano preenchido).")
        return
    print("Concordância agente x pessoa: %d/%d = %.0f%% (mínimo %.0f%%)" % (r["iguais"], r["preenchidas"], r["taxa"] * 100, a.minimo * 100))
    for rodada, repo, regra, local, ag, hu in r["divergentes"]:
        print("  diverge: %s %s %s — agente %s, pessoa %s" % (repo, regra, local.split("]")[0].lstrip("["), ag, hu))
    if r["taxa"] < a.minimo:
        print("Abaixo do mínimo: leia as taxas de precisao.md com desconfiança e corrija as linhas divergentes.")
        sys.exit(1)


if __name__ == "__main__":
    main()
