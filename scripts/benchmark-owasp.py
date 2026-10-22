#!/usr/bin/env python3
"""Mede recall e precisão do Sentinel no OWASP Benchmark (Java) a partir de achados em JSON.

Uso: benchmark-owasp.py <achados.json> [--baseline arquivo.json] [--grava-baseline arquivo.json] [--markdown saida.md]

Entrada aceita: lista de achados da API do Sentinel (campos ruleId, filePath) ou o JSON do `semgrep --json`.
O gabarito é o `expectedresults-1.2.csv` oficial do OWASP Benchmark (licença GPL; só o CSV de rótulos é copiado).
Métrica por categoria: TPR (casos vulneráveis detectados), FPR (casos seguros marcados) e pontuação = TPR - FPR
(a "pontuação de Youden" do próprio Benchmark: 0 = chute, 1 = perfeito). Um caso conta como detectado quando algum achado
de uma regra mapeada para a categoria cai no arquivo do caso. O mapa regra -> categoria está em MAPA_REGRAS.
Sai com 1 se a pontuação média cair mais que a tolerância contra o baseline.
"""
import argparse, csv, json, os, re, sys

AQUI = os.path.dirname(os.path.abspath(__file__))
CSV = os.path.join(AQUI, "..", "audit-rules", "calibracao", "benchmark", "expectedresults-1.2.csv")

# sufixo do ruleId -> categorias do Benchmark que ele cobre. Regra fora do mapa não conta (ruído de qualidade etc.).
MAPA_REGRAS = {
    "sentinel-sql-injection": ["sqli"],
    "sentinel-taint-sql-parametro-http": ["sqli"],
    "sentinel-xss-response-write": ["xss"],
    "sentinel-taint-html-parametro-url": ["xss"],
    "sentinel-ldap-injection": ["ldapi"],
    "sentinel-xpath-injection": ["xpathi"],
    "sentinel-command-injection": ["cmdi"],
    "sentinel-command-injection-variable": ["cmdi"],
    "sentinel-command-injection-shell": ["cmdi"],
    "sentinel-command-injection-requisicao": ["cmdi"],
    "sentinel-crypto-hash-fraco": ["hash"],
    "sentinel-crypto-cifra-fraca": ["crypto"],
    "sentinel-crypto-random-previsivel": ["weakrand"],
    "sentinel-crypto-random-servidor": ["weakrand"],
    "sentinel-cookie-sem-secure": ["securecookie"],
    "sentinel-cookie-secure-desligado": ["securecookie"],
    "sentinel-servlet-path-traversal": ["pathtraver"],
    "sentinel-api-path-traversal": ["pathtraver"],
    "sentinel-servlet-trust-boundary": ["trustbound"],
}
# Conjuntos: os casos 1..1370 foram usados para escrever as regras ("treino"); 1371..2740 só medem ("validacao").
LIMITE_TREINO = 1370


def carregar_gabarito(conjunto="todos"):
    casos = {}
    with open(CSV, encoding="utf-8") as f:
        for linha in csv.reader(f):
            if not linha or linha[0].startswith("#") or len(linha) < 3:
                continue
            n = int(linha[0][-5:])
            if conjunto == "treino" and n > LIMITE_TREINO or conjunto == "validacao" and n <= LIMITE_TREINO:
                continue
            casos[linha[0]] = (linha[1], linha[2].strip().lower() == "true")
    return casos


def carregar_achados(caminho):
    d = json.load(open(caminho, encoding="utf-8"))
    achados = []
    if isinstance(d, dict) and "results" in d:  # semgrep --json
        for r in d["results"]:
            achados.append((r["check_id"].split(".")[-1], r["path"]))
    else:
        for x in d:
            achados.append(((x.get("ruleId") or "").split(".")[-1], x.get("filePath") or ""))
    return achados


def medir(achados, casos):
    detectado = {}  # caso -> categorias detectadas
    for regra, caminho in achados:
        m = re.search(r"(BenchmarkTest\d{5})", caminho)
        if not m or regra not in MAPA_REGRAS:
            continue
        detectado.setdefault(m.group(1), set()).update(MAPA_REGRAS[regra])
    tabela = {}
    for caso, (cat, vulneravel) in casos.items():
        t = tabela.setdefault(cat, {"vuln": 0, "seguro": 0, "tp": 0, "fp": 0})
        achou = cat in detectado.get(caso, set())
        if vulneravel:
            t["vuln"] += 1; t["tp"] += achou
        else:
            t["seguro"] += 1; t["fp"] += achou
    for cat, t in tabela.items():
        t["tpr"] = round(t["tp"] / t["vuln"], 3) if t["vuln"] else 0.0
        t["fpr"] = round(t["fp"] / t["seguro"], 3) if t["seguro"] else 0.0
        t["pontuacao"] = round(t["tpr"] - t["fpr"], 3)
    media = round(sum(t["pontuacao"] for t in tabela.values()) / len(tabela), 3)
    return tabela, media


def markdown(tabela, media, base=None):
    linhas = ["| Categoria | Vulneráveis | Seguros | TPR | FPR | Pontuação | Antes |", "|---|---:|---:|---:|---:|---:|---:|"]
    for cat in sorted(tabela):
        t = tabela[cat]
        antes = "" if not base or cat not in base["categorias"] else "%.3f" % base["categorias"][cat]["pontuacao"]
        linhas.append("| %s | %d | %d | %.0f%% | %.0f%% | %.3f | %s |" % (cat, t["vuln"], t["seguro"], t["tpr"] * 100, t["fpr"] * 100, t["pontuacao"], antes))
    linhas.append("| **média** | | | | | **%.3f** | %s |" % (media, "" if not base else "%.3f" % base["media"]))
    return "\n".join(linhas)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("achados"); ap.add_argument("--baseline"); ap.add_argument("--grava-baseline")
    ap.add_argument("--markdown"); ap.add_argument("--conjunto", default="todos", choices=["todos", "treino", "validacao"]); ap.add_argument("--tolerancia", type=float, default=0.02)
    a = ap.parse_args()
    casos = carregar_gabarito(a.conjunto)
    tabela, media = medir(carregar_achados(a.achados), casos)
    # a linha de base é do conjunto completo; nos subconjuntos ela só confundiria a leitura
    base = json.load(open(a.baseline)) if a.baseline and a.conjunto == "todos" and os.path.exists(a.baseline) else None
    texto = markdown(tabela, media, base)
    print(texto)
    if a.markdown:
        open(a.markdown, "w", encoding="utf-8").write(texto + "\n")
    if a.grava_baseline:
        json.dump({"media": media, "categorias": tabela}, open(a.grava_baseline, "w"), indent=1, ensure_ascii=False)
    resumo = "; ".join("%s %.2f" % (c, tabela[c]["pontuacao"]) for c in sorted(tabela))
    print("::notice title=OWASP Benchmark (média %.3f)::%s" % (media, resumo))
    if base and media < base["media"] - a.tolerancia:
        print("::error title=Regressão no OWASP Benchmark::média caiu de %.3f para %.3f" % (base["media"], media))
        sys.exit(1)


if __name__ == "__main__":
    main()
