#!/usr/bin/env python3
"""Compara a calibração atual com a anterior e confere as verdades conhecidas.

Uso: comparar-calibracao.py <pasta-atual> <pasta-anterior|-> <esperado.json>
Falha (exit 1) se uma regra de `deve_achar` sumiu, uma de `nao_deve_achar` voltou ou `max_total` foi estourado.
As diferenças em relação à execução anterior viram avisos (achados novos, sumidos, tempo).
"""
import json, os, sys


def carregar(pasta):
    saida = {}
    if pasta and pasta != "-" and os.path.isdir(pasta):
        for nome in sorted(os.listdir(pasta)):
            if nome.endswith(".json"):
                d = json.load(open(os.path.join(pasta, nome), encoding="utf-8"))
                saida[d["repo"]] = d
    return saida


def tem_regra(por_regra, sufixo):
    return any(chave.endswith(sufixo) for chave in por_regra)


def aviso(titulo, linhas):
    texto = "%0A".join(linhas)
    print("::notice title=%s::%s" % (titulo, texto[:3500]))


def main():
    atual, anterior = carregar(sys.argv[1]), carregar(sys.argv[2])
    esperado = json.load(open(sys.argv[3], encoding="utf-8")).get("repositorios", {})
    falhas = []
    resumo = []
    for repo, d in atual.items():
        linha = "%s: %d achados, nota %s, %ds" % (repo, d["total"], d["nota"], d["segundos"])
        ant = anterior.get(repo)
        if ant:
            novas = sorted(set(d["porRegra"]) - set(ant["porRegra"]))
            sumidas = sorted(set(ant["porRegra"]) - set(d["porRegra"]))
            linha += " (antes: %d achados, nota %s, %ds)" % (ant["total"], ant["nota"], ant["segundos"])
            if novas:
                linha += "; regras novas: " + ", ".join(n.split(".")[-1] for n in novas[:6])
            if sumidas:
                linha += "; regras que sumiram: " + ", ".join(n.split(".")[-1] for n in sumidas[:6])
            if ant["total"] and d["total"] > ant["total"] * 1.5 and d["total"] - ant["total"] > 30:
                falhas.append("%s: achados subiram de %d para %d (+50%%): possível regressão de ruído" % (repo, ant["total"], d["total"]))
            if ant["segundos"] and d["segundos"] > ant["segundos"] * 2 and d["segundos"] - ant["segundos"] > 120:
                falhas.append("%s: auditoria ficou 2x mais lenta (%ds -> %ds)" % (repo, ant["segundos"], d["segundos"]))
        resumo.append(linha)
        regras = esperado.get(repo, {})
        for r in regras.get("deve_achar", []):
            if not tem_regra(d["porRegra"], r):
                falhas.append("%s: a regra %s deveria achar algo e não achou (falso negativo)" % (repo, r))
        for r in regras.get("nao_deve_achar", []):
            if tem_regra(d["porRegra"], r):
                falhas.append("%s: a regra %s voltou a achar (falso positivo já corrigido)" % (repo, r))
        if "max_total" in regras and d["total"] > regras["max_total"]:
            falhas.append("%s: %d achados, acima do teto de %d" % (repo, d["total"], regras["max_total"]))
    if not atual:
        falhas.append("nenhum resultado de calibração encontrado")
    aviso("Calibração em vários repositórios", resumo or ["sem dados"])
    if falhas:
        print("::error title=Calibração reprovada::%s" % "%0A".join(falhas)[:3500])
        sys.exit(1)


main()
