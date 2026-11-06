#!/usr/bin/env python3
"""Mede uma volta do ciclo a partir do log do job da `Auditoria completa` (ou de um achados-compactos.tsv).

Uso:
  medir-volta.py extrair <log-ou-tsv> <dono/repo> > medida.json
  medir-volta.py recall  <medida.json> <gabarito.json>            # recall por falha conhecida
  medir-volta.py comparar <atual.json> <baseline.json> [--gravar]  # regressão por regra (e atualiza o baseline com --gravar)

Gabarito (audit-rules/calibracao/gabaritos/<dono>__<repo>.json): lista `falhas`, cada uma com `arquivo`
(sufixo do caminho), `linha` (opcional, tolerância `tolerancia`, padrão 3), `regras` (sufixos de ruleId que
valem como acerto) e `cwe` (só descrição). Só repositório de TREINO ganha gabarito; validação segue só por contagem.
Código de saída 1 em `comparar` quando uma regra dobra sem motivo ou o total cresce mais de 25 %.
"""
import json, re, sys, collections

LINHA = re.compile(r"^(?:\S+Z )?(CRITICAL|HIGH|MEDIUM|LOW|INFO)\t([^\t]*)\t([^\t]*)\t(.*?):(\w+)\t(.*)$")


def extrair(caminho, repo):
    texto = open(caminho, encoding="utf-8", errors="replace").read()
    nota = re.search(r"- Nota: ([\d.]+)", texto)
    achados = []
    for ln in texto.splitlines():
        m = LINHA.match(ln.strip("\r"))
        if m:
            sev, ferr, regra, arq, lin, titulo = m.groups()
            achados.append({"sev": sev, "ferramenta": ferr, "regra": regra.split(".")[-1],
                            "arquivo": arq, "linha": int(lin) if lin.isdigit() else None})
    por_regra = collections.Counter(f"{a['ferramenta']}/{a['regra']}" for a in achados)
    por_sev = collections.Counter(a["sev"] for a in achados)
    return {"repo": repo, "nota": float(nota.group(1)) if nota else None, "total": len(achados),
            "porSeveridade": dict(por_sev), "porRegra": dict(por_regra), "achados": achados}


def recall(medida, gabarito):
    falhas = gabarito["falhas"]
    acertos, perdidas = [], []
    for f in falhas:
        tol = f.get("tolerancia", 3)
        ok = any(
            a["arquivo"].endswith(f["arquivo"])
            and (f.get("linha") is None or (a["linha"] is not None and abs(a["linha"] - f["linha"]) <= tol))
            and (not f.get("regras") or any(a["regra"].endswith(r) for r in f["regras"]))
            for a in medida["achados"])
        (acertos if ok else perdidas).append(f)
    total = len(falhas)
    print(f"Recall {medida['repo']}: {len(acertos)}/{total} = {100 * len(acertos) / max(total, 1):.0f} %")
    for f in perdidas:
        print(f"  PERDIDA {f['arquivo']}:{f.get('linha', '*')} ({f.get('cwe', '?')}) {f.get('nota', '')}")
    return len(perdidas)


def comparar(atual, base, gravar):
    problemas = []
    print(f"{atual['repo']}: {base['total']} -> {atual['total']} achados, nota {base['nota']} -> {atual['nota']}")
    if base["total"] and atual["total"] > base["total"] * 1.25:
        problemas.append(f"total subiu {base['total']} -> {atual['total']} (mais de 25 %)")
    chaves = sorted(set(base["porRegra"]) | set(atual["porRegra"]))
    for k in chaves:
        a, b = atual["porRegra"].get(k, 0), base["porRegra"].get(k, 0)
        if a == b:
            continue
        print(f"  {k}: {b} -> {a}")
        if b >= 4 and a >= 2 * b:
            problemas.append(f"{k} dobrou ({b} -> {a}): conferir falso positivo")
        if k.startswith("Opengrep/") and "sentinel-" in k and a == 0 and b >= 1 and "seguran" not in k:
            problemas.append(f"{k} sumiu ({b} -> 0): conferir se foi proposital")
    for p in problemas:
        print("ATENÇÃO:", p)
    if gravar:
        json.dump({k: v for k, v in atual.items() if k != "achados"}, open(sys.argv[3], "w"), indent=1, ensure_ascii=False)
        print("baseline atualizado:", sys.argv[3])
    return len(problemas)


def main():
    if len(sys.argv) < 4:
        print(__doc__); return 2
    cmd = sys.argv[1]
    if cmd == "extrair":
        json.dump(extrair(sys.argv[2], sys.argv[3]), sys.stdout, ensure_ascii=False)
        return 0
    if cmd == "recall":
        return 1 if recall(json.load(open(sys.argv[2])), json.load(open(sys.argv[3]))) else 0
    if cmd == "comparar":
        return 1 if comparar(json.load(open(sys.argv[2])), json.load(open(sys.argv[3])), "--gravar" in sys.argv) else 0
    print(__doc__); return 2


if __name__ == "__main__":
    sys.exit(main())
