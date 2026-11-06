#!/usr/bin/env python3
"""Mede uma volta do ciclo a partir do log do job da `Auditoria completa` (ou de um achados-compactos.tsv).

Uso:
  medir-volta.py extrair <log-ou-tsv> <dono/repo> > medida.json
  medir-volta.py recall  <medida.json> <gabarito.json>            # recall por falha conhecida
  medir-volta.py comparar <atual.json> <baseline.json> [--gravar]  # regressão por regra (e atualiza o baseline com --gravar)
  medir-volta.py wilson <acertos> <total>                          # intervalo de confiança de 95 % (Wilson) para uma proporção

Gabarito (audit-rules/calibracao/gabaritos/<dono>__<repo>.json, ou gabaritos/validacao/ para validação): lista `falhas`,
cada uma com `arquivo` (opcional; sufixo do caminho, sem ele vale qualquer arquivo), `linha` (opcional, tolerância
`tolerancia`, padrão 3), `regras` (sufixos de ruleId que valem como acerto), `cwe` (só descrição) e `fonte` (URL ou
documento de onde a falha foi tirada; obrigatória no gabarito de validação). Gabarito de validação nunca é montado
lendo o código do repositório.
Códigos de saída: `recall` 1 quando há falha perdida e 3 quando o gabarito não tem nenhuma falha (não é medida);
`comparar` 1 quando uma regra dobra sem motivo ou o total cresce mais de 25 %.
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


def wilson(k, n, z=1.96):
    """Intervalo de confiança de Wilson para k acertos em n; com amostra pequena (n <= 100) o de Wald engana."""
    if n <= 0:
        return 0.0, 0.0
    p = k / n
    den = 1 + z * z / n
    centro = (p + z * z / (2 * n)) / den
    meia = z * ((p * (1 - p) / n + z * z / (4 * n * n)) ** 0.5) / den
    return max(0.0, centro - meia), min(1.0, centro + meia)


def recall(medida, gabarito):
    falhas = gabarito.get("falhas") or []
    if not falhas:
        print(f"Gabarito de {gabarito.get('repo', '?')} sem nenhuma falha: não há recall a medir (0/0 não é 0 %).")
        return None
    acertos, perdidas = [], []
    for f in falhas:
        tol = f.get("tolerancia", 3)
        ok = any(
            (not f.get("arquivo") or a["arquivo"].endswith(f["arquivo"]))
            and (f.get("linha") is None or (a["linha"] is not None and abs(a["linha"] - f["linha"]) <= tol))
            and (not f.get("regras") or any(a["regra"].endswith(r) for r in f["regras"]))
            for a in medida["achados"])
        (acertos if ok else perdidas).append(f)
    total = len(falhas)
    lo, hi = wilson(len(acertos), total)
    print(f"Recall {medida['repo']}: {len(acertos)}/{total} = {100 * len(acertos) / total:.0f} % (IC 95 % de Wilson: {100 * lo:.0f} a {100 * hi:.0f} %)")
    for f in perdidas:
        print(f"  PERDIDA {f.get('arquivo', '*')}:{f.get('linha', '*')} ({f.get('cwe', '?')}) {f.get('nota', '')}")
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
    cmd = sys.argv[1] if len(sys.argv) > 1 else ""
    if cmd == "wilson" and len(sys.argv) == 4:
        lo, hi = wilson(int(sys.argv[2]), int(sys.argv[3]))
        print(f"{sys.argv[2]}/{sys.argv[3]} = {100 * int(sys.argv[2]) / max(int(sys.argv[3]), 1):.0f} % (IC 95 % de Wilson: {100 * lo:.0f} a {100 * hi:.0f} %)")
        return 0
    if len(sys.argv) < 4:
        print(__doc__); return 2
    if cmd == "extrair":
        json.dump(extrair(sys.argv[2], sys.argv[3]), sys.stdout, ensure_ascii=False)
        return 0
    if cmd == "recall":
        perdidas = recall(json.load(open(sys.argv[2])), json.load(open(sys.argv[3])))
        return 3 if perdidas is None else (1 if perdidas else 0)
    if cmd == "comparar":
        return 1 if comparar(json.load(open(sys.argv[2])), json.load(open(sys.argv[3])), "--gravar" in sys.argv) else 0
    print(__doc__); return 2


if __name__ == "__main__":
    sys.exit(main())
