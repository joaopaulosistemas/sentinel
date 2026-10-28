#!/usr/bin/env python3
"""Liga o peso de uma regra no score à precisão MEDIDA (docs/evolucao/precisao.md).

Uso:
  precisao-regras.py                 lista regras com amostra suficiente e precisão baixa (candidatas a sombra/severidade menor)
  precisao-regras.py --verifica      falha (exit 1) se um perfil marcar uma regra com "# precisao.md" sem a evidência exigida

Evidência exigida: pelo menos --minimo achados com veredito (VP+FP) e precisão VP/(VP+FP) <= --max-precisao,
e a regra não pode ter sido "corrigida" depois da amostra (a medida antiga não vale para a regra nova).
No perfil, a sobrescrita leva um comentário "# precisao.md" na mesma linha. Rebaixar uma regra sem medida é palpite;
manter uma regra de 0 em 6 pesando no score é ruído: este script separa os dois casos.
"""
import argparse, os, re, sys

AQUI = os.path.dirname(os.path.abspath(__file__))
RAIZ = os.path.join(AQUI, "..")
PRECISAO = os.path.join(RAIZ, "docs", "evolucao", "precisao.md")
PERFIS = os.path.join(RAIZ, "audit-rules", "packs", "profiles")


def ler_precisao(caminho=PRECISAO):
    """regra -> dict(revisados, vp, fp, duv, corrigida)"""
    out = {}
    for linha in open(caminho, encoding="utf-8"):
        m = re.match(r"\|\s*`([^`]+)`\s*\|\s*(\d+)\s*\|\s*(\d+)\s*\|\s*(\d+)\s*\|\s*(\d+)\s*\|(.*)\|\s*$", linha)
        if not m:
            continue
        regra, rev, vp, fp, duv, resto = m.groups()
        out[regra] = {"revisados": int(rev), "vp": int(vp), "fp": int(fp), "duv": int(duv),
                      "corrigida": "corrigida" in resto.lower()}
    return out


def precisao(d):
    n = d["vp"] + d["fp"]
    return None if n == 0 else d["vp"] / n


def atende(d, minimo, max_prec):
    n = d["vp"] + d["fp"]
    p = precisao(d)
    return n >= minimo and p is not None and p <= max_prec and not d["corrigida"]


def regras_marcadas():
    """(arquivo, regra) de cada linha de perfil com o comentário '# precisao.md'."""
    out = []
    for nome in sorted(os.listdir(PERFIS)):
        if not nome.endswith(".yml"):
            continue
        for linha in open(os.path.join(PERFIS, nome), encoding="utf-8"):
            if "# precisao.md" not in linha:
                continue
            m = re.match(r'\s*"?([^":\s]+)"?\s*:', linha)
            if m:
                out.append((nome, m.group(1)))
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--verifica", action="store_true")
    ap.add_argument("--minimo", type=int, default=5)
    ap.add_argument("--max-precisao", type=float, default=0.2)
    a = ap.parse_args()
    dados = ler_precisao()
    if a.verifica:
        erros = 0
        for arq, regra in regras_marcadas():
            d = dados.get(regra)
            if d is None or not atende(d, a.minimo, a.max_precisao):
                print("ERRO %s: '%s' está marcada com '# precisao.md' mas a evidência não atende (>= %d veredictos, precisão <= %.0f%%, regra não corrigida depois)"
                      % (arq, regra, a.minimo, a.max_precisao * 100))
                erros += 1
            else:
                print("ok   %s: %s (VP %d / FP %d)" % (arq, regra, d["vp"], d["fp"]))
        sys.exit(1 if erros else 0)
    marcadas = {r for _, r in regras_marcadas()}
    for regra, d in sorted(dados.items()):
        if atende(d, a.minimo, a.max_precisao):
            print("%-60s VP %2d  FP %2d  %s" % (regra, d["vp"], d["fp"], "já em sombra" if regra in marcadas else "CANDIDATA"))


if __name__ == "__main__":
    main()
