#!/usr/bin/env python3
"""Resume as auditorias da descoberta e cruza os achados entre repositórios.
Uso: relatorio-descoberta.py <pasta-com-json-e-achados> > relatorio-bruto.md
Sinaliza regras suspeitas de ruído (dominam um repositório ou aparecem em quase todos com contagem alta),
repositórios sem achado de alguma categoria, e dá amostras (arquivo:linha) para revisão manual."""
import collections, glob, json, os, sys

pasta = sys.argv[1]
repos = []
for caminho in sorted(glob.glob(os.path.join(pasta, "*.json"))):
    if caminho.endswith(".achados.json"):
        continue
    r = json.load(open(caminho))
    ach = os.path.join(pasta, os.path.basename(caminho)[:-5] + ".achados.json")
    r["achados"] = json.load(open(ach)) if os.path.exists(ach) else []
    repos.append(r)

ok = [r for r in repos if "erro" not in r]
print("# Relatório bruto da descoberta\n")
print("| Repositório | Achados | Nota | Tempo | Regras distintas |")
print("|---|---:|---:|---:|---:|")
for r in repos:
    if "erro" in r:
        print("| %s | falhou (%s) | | | |" % (r["repo"], r["erro"])); continue
    print("| %s | %d | %s | %ds | %d |" % (r["repo"], r["total"], r["nota"], r["segundos"], len(r["porRegra"])))

por_regra = collections.defaultdict(lambda: {"n": 0, "repos": set(), "amostra": [], "sev": collections.Counter()})
for r in ok:
    for x in r["achados"]:
        k = "%s/%s" % (x.get("toolSource"), x.get("ruleId"))
        d = por_regra[k]; d["n"] += 1; d["repos"].add(r["repo"]); d["sev"][str(x.get("severity"))] += 1
        if len(d["amostra"]) < 4 and x.get("filePath"):
            d["amostra"].append("%s `%s:%s`" % (r["repo"], x["filePath"], x.get("lineNumber")))

total = sum(d["n"] for d in por_regra.values()) or 1
print("\n## Regras mais frequentes (cruzado entre os repositórios)\n")
print("| Regra | Achados | % do total | Repositórios | Severidade | Amostras para revisão |")
print("|---|---:|---:|---:|---|---|")
for k, d in sorted(por_regra.items(), key=lambda kv: -kv[1]["n"])[:25]:
    print("| `%s` | %d | %.1f%% | %d/%d | %s | %s |" % (k, d["n"], 100.0 * d["n"] / total, len(d["repos"]), len(ok),
          ", ".join("%s %d" % (s, c) for s, c in d["sev"].most_common()), "<br>".join(d["amostra"])))

print("\n## Sinais de ruído (suspeitas, a confirmar lendo o código)\n")
sus = False
for r in ok:
    for k, n in r["porRegra"].items():
        if r["total"] >= 30 and n / r["total"] > 0.4:
            sus = True; print("- `%s` responde por %d%% dos %d achados de %s" % (k, 100 * n // r["total"], r["total"], r["repo"]))
for k, d in por_regra.items():
    if len(ok) >= 3 and len(d["repos"]) >= max(3, int(0.8 * len(ok))) and d["n"] >= 15 * len(ok):
        sus = True; print("- `%s` aparece em %d/%d repositórios com %d achados" % (k, len(d["repos"]), len(ok), d["n"]))
if not sus: print("- nenhum")

print("\n## Repositórios sem achados de segurança (possível falso negativo)\n")
alguns = False
for r in ok:
    seg = [x for x in r["achados"] if str(x.get("category")).upper() in ("SECURITY", "SECRETS", "DEPENDENCY")]
    if not seg:
        alguns = True; print("- %s: %d achados, nenhum de segurança" % (r["repo"], r["total"]))
if not alguns: print("- nenhum")
