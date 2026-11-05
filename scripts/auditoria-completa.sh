#!/usr/bin/env bash
# Auditoria completa de um repositório público: sobe Postgres + API (imagem sentinel-audit-api:ci), cadastra o
# sistema, roda a auditoria com todos os analisadores e grava os resultados em uma pasta.
# Uso: scripts/auditoria-completa.sh <url-do-repositorio> [branch] [stack] [pasta-de-saida]
#   stack: JAVA | JAVASCRIPT | MIXED (padrão MIXED)
# Saída: auditoria.json, achados.json, achados.csv, achados-compactos.tsv, relatorio-tecnico.pdf, relatorio-tecnico.html,
#        relatorio-executivo.pdf, sbom.json, insights.json, log-api.txt e resumo.md
set -u
REPO_URL="${1:?informe a URL do repositório}"
BRANCH="${2:-main}"
STACK="${3:-MIXED}"
OUT="${4:-saida-auditoria}"
KEY="auditoria-$(date +%s)"
NET="sentinel-auditoria"
API="http://127.0.0.1:18080"
H=(-H "X-API-Key: $KEY" -H "Content-Type: application/json")
mkdir -p "$OUT"

falhar() {
  echo "::error title=Auditoria completa falhou::$1"
  docker logs sentinel-aud-api > "$OUT/log-api.txt" 2>&1 || true
  docker rm -f sentinel-aud-api sentinel-aud-db >/dev/null 2>&1
  exit 1
}

docker network create "$NET" >/dev/null 2>&1
docker run -d --name sentinel-aud-db --network "$NET" \
  -e POSTGRES_DB=sentinel_audit -e POSTGRES_USER=sentinel_audit -e POSTGRES_PASSWORD=aud \
  postgres:16-alpine >/dev/null || falhar "Postgres não subiu"
for _ in $(seq 1 30); do docker exec sentinel-aud-db pg_isready -U sentinel_audit >/dev/null 2>&1 && break; sleep 2; done

docker run -d --name sentinel-aud-api --network "$NET" -p 127.0.0.1:18080:8080 \
  -e DB_URL=jdbc:postgresql://sentinel-aud-db:5432/sentinel_audit -e DB_USER=sentinel_audit -e DB_PASSWORD=aud \
  -e API_KEY="$KEY" -e SCHEDULER_ENABLED=false \
  -e OSV_ENABLED=true -e TRIVY_ENABLED=true -e GITLEAKS_ENABLED=true \
  sentinel-audit-api:ci >/dev/null || falhar "API não subiu"

ok=0
for _ in $(seq 1 60); do
  if curl -fsS "${H[@]}" "$API/api/v1/health" >/dev/null 2>&1; then ok=1; break; fi
  sleep 3
done
[ "$ok" = 1 ] || falhar "A API não respondeu ao health em 3 minutos"

nome="$(basename "$REPO_URL" .git)"
sistema="$(curl -sS "${H[@]}" -X POST "$API/api/v1/systems" \
  -d "{\"name\":\"$nome\",\"repoUrl\":\"$REPO_URL\",\"stack\":\"$STACK\"}")"
sid="$(printf '%s' "$sistema" | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])' 2>/dev/null)" \
  || falhar "Cadastro do sistema falhou: ${sistema:0:300}"

auditoria="$(curl -sS "${H[@]}" -X POST "$API/api/v1/audits" -d "{\"systemId\":$sid,\"branch\":\"$BRANCH\"}")"
aid="$(printf '%s' "$auditoria" | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])' 2>/dev/null)" \
  || falhar "Pedido de auditoria falhou: ${auditoria:0:300}"
echo "sistema $sid, auditoria $aid"

status=""
for _ in $(seq 1 720); do   # até 60 minutos
  resp="$(curl -sS "${H[@]}" "$API/api/v1/audits/$aid")"
  status="$(printf '%s' "$resp" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("status",""))' 2>/dev/null)"
  case "$status" in COMPLETED|FAILED) break;; esac
  sleep 5
done
printf '%s' "$resp" > "$OUT/auditoria.json"
[ "$status" = "COMPLETED" ] || falhar "Auditoria terminou como '$status': ${resp:0:400}"

curl -sS "${H[@]}" "$API/api/v1/audits/$aid/findings" > "$OUT/achados.json"
curl -sS "${H[@]}" "$API/api/v1/audits/$aid/findings.csv" > "$OUT/achados.csv"
curl -sS "${H[@]}" "$API/api/v1/audits/$aid/report?type=tecnico" > "$OUT/relatorio-tecnico.html"
curl -sS "${H[@]}" "$API/api/v1/audits/$aid/report.pdf?type=tecnico" > "$OUT/relatorio-tecnico.pdf"
curl -sS "${H[@]}" "$API/api/v1/audits/$aid/report.pdf?type=executivo" > "$OUT/relatorio-executivo.pdf"
curl -sS "${H[@]}" "$API/api/v1/audits/$aid/sbom" > "$OUT/sbom.json"
curl -sS "${H[@]}" "$API/api/v1/audits/$aid/insights" > "$OUT/insights.json"
docker logs sentinel-aud-api > "$OUT/log-api.txt" 2>&1 || true

python3 -I - "$OUT" "$REPO_URL" "$BRANCH" <<'PY'
import json, sys, collections
out, repo, branch = sys.argv[1:4]
a = json.load(open(f"{out}/auditoria.json"))
f = json.load(open(f"{out}/achados.json"))
sev = collections.Counter(x.get("severity") for x in f)
fer = collections.Counter(x.get("toolSource") for x in f)
reg = collections.Counter((x.get("toolSource"), x.get("ruleId")) for x in f)
linhas = [f"# Auditoria completa: {repo} ({branch})", "",
          f"- Nota: {(a.get('riskScore') or {}).get('overall')}",
          f"- Dimensões: {json.dumps(a.get('riskScore') or {})}",
          f"- Achados: {len(f)} | severidade: {dict(sev)}",
          f"- Por ferramenta: {dict(fer)}", "", "## Regras mais frequentes"]
linhas += [f"- {n} × {t}/{r}" for (t, r), n in reg.most_common(25)]
open(f"{out}/resumo.md", "w").write("\n".join(linhas) + "\n")
# versão compacta (uma linha por achado) para ler direto no log do GitHub Actions, onde o artefato não chega
ordem = {"CRITICAL": 0, "HIGH": 1, "MEDIUM": 2, "LOW": 3, "INFO": 4}
f.sort(key=lambda x: (ordem.get(x.get("severity"), 9), str(x.get("ruleId")), str(x.get("filePath")), x.get("lineNumber") or 0))
with open(f"{out}/achados-compactos.tsv", "w") as t:
    t.write("severidade\tferramenta\tregra\tarquivo:linha\ttitulo\n")
    for x in f[:5000]:
        titulo = (x.get("title") or "").replace("\t", " ").replace("\n", " ")[:140]
        t.write("%s\t%s\t%s\t%s:%s\t%s\n" % (x.get("severity"), x.get("toolSource"), x.get("ruleId"), x.get("filePath"), x.get("lineNumber"), titulo))
print("\n".join(linhas))
PY
docker rm -f sentinel-aud-api sentinel-aud-db >/dev/null 2>&1
exit 0
