#!/usr/bin/env bash
# Teste ponta a ponta: sobe Postgres + API (imagem sentinel-audit-api:ci), cadastra um repositório público,
# roda uma auditoria de verdade e confere o resultado. Falhas viram anotações do GitHub Actions.
# Uso: scripts/e2e.sh [url-do-repositorio]
set -u
REPO_URL="${1:-https://github.com/spring-projects/spring-petclinic}"
KEY="e2e-$(date +%s)"
NET="sentinel-e2e"
API="http://127.0.0.1:18080"
H=(-H "X-API-Key: $KEY" -H "Content-Type: application/json")

falhar() {
  local msg="$1"
  local logs; logs="$(docker logs sentinel-e2e-api 2>&1 | tail -30)"
  local r="$msg%0A--- API ---%0A$(printf '%s' "$logs" | grep -E "ERROR|WARN|Exception|falhou" | tail -12)"
  r="${r//$'\n'/%0A}"
  echo "::error title=Teste ponta a ponta falhou::${r:0:3500}"
  docker rm -f sentinel-e2e-api sentinel-e2e-db >/dev/null 2>&1
  exit 1
}

docker network create "$NET" >/dev/null 2>&1
docker run -d --name sentinel-e2e-db --network "$NET" \
  -e POSTGRES_DB=sentinel_audit -e POSTGRES_USER=sentinel_audit -e POSTGRES_PASSWORD=e2e \
  postgres:16-alpine >/dev/null || falhar "Postgres não subiu"
for _ in $(seq 1 30); do docker exec sentinel-e2e-db pg_isready -U sentinel_audit >/dev/null 2>&1 && break; sleep 2; done

docker run -d --name sentinel-e2e-api --network "$NET" -p 127.0.0.1:18080:8080 \
  -e DB_URL=jdbc:postgresql://sentinel-e2e-db:5432/sentinel_audit -e DB_USER=sentinel_audit -e DB_PASSWORD=e2e \
  -e API_KEY="$KEY" -e GIT_ALLOWED_HOSTS=github.com -e SCHEDULER_ENABLED=false \
  -e OSV_ENABLED=false -e TRIVY_ENABLED=false -e GITLEAKS_ENABLED=true \
  sentinel-audit-api:ci >/dev/null || falhar "API não subiu"

ok=0
for _ in $(seq 1 60); do
  if curl -fsS "${H[@]}" "$API/api/v1/health" >/dev/null 2>&1; then ok=1; break; fi
  sleep 3
done
[ "$ok" = 1 ] || falhar "A API não respondeu ao health em 3 minutos"
echo "API no ar"

sistema="$(curl -sS "${H[@]}" -X POST "$API/api/v1/systems" \
  -d "{\"name\":\"e2e\",\"repoUrl\":\"$REPO_URL\",\"stack\":\"JAVA\"}")"
sid="$(printf '%s' "$sistema" | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])' 2>/dev/null)" \
  || falhar "Cadastro do sistema falhou: ${sistema:0:300}"
echo "sistema $sid"

auditoria="$(curl -sS "${H[@]}" -X POST "$API/api/v1/audits" -d "{\"systemId\":$sid,\"branch\":\"main\"}")"
aid="$(printf '%s' "$auditoria" | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])' 2>/dev/null)" \
  || falhar "Pedido de auditoria falhou: ${auditoria:0:300}"
echo "auditoria $aid"

status=""
for _ in $(seq 1 120); do
  resp="$(curl -sS "${H[@]}" "$API/api/v1/audits/$aid")"
  status="$(printf '%s' "$resp" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("status",""))' 2>/dev/null)"
  case "$status" in COMPLETED|FAILED) break;; esac
  sleep 5
done
[ "$status" = "COMPLETED" ] || falhar "Auditoria terminou como '$status' (esperado COMPLETED): ${resp:0:400}"

total="$(printf '%s' "$resp" | python3 -c 'import json,sys; d=json.load(sys.stdin); print(sum((d.get("findingsSummary") or {}).values()))')"
score="$(printf '%s' "$resp" | python3 -c 'import json,sys; d=json.load(sys.stdin); print((d.get("riskScore") or {}).get("overall"))')"
echo "findings=$total score=$score"
[ "${total:-0}" -gt 0 ] || falhar "Auditoria concluída sem nenhum achado (esperava ao menos 1): ${resp:0:400}"

for ep in report sbom insights findings; do
  code="$(curl -sS -o /dev/null -w '%{http_code}' "${H[@]}" "$API/api/v1/audits/$aid/$ep")"
  [ "$code" = "200" ] || falhar "GET /audits/$aid/$ep devolveu HTTP $code"
done
sem_chave="$(curl -sS -o /dev/null -w '%{http_code}' "$API/api/v1/systems")"
[ "$sem_chave" = "401" ] || falhar "Sem chave de API o acesso deveria dar 401, deu $sem_chave"

cors="$(curl -sS -i -X OPTIONS "$API/api/v1/systems" -H "Origin: http://localhost:3000" -H "Access-Control-Request-Method: GET" -H "Access-Control-Request-Headers: X-API-Key" | tr -d '\r' | grep -i '^access-control-allow-origin:')"
[ -n "$cors" ] || falhar "CORS desligado: o preflight não devolveu Access-Control-Allow-Origin"

echo "::notice title=Teste ponta a ponta::auditoria $aid concluída com $total achados e nota $score; relatório, SBOM, insights e findings responderam 200; sem chave devolve 401"
docker rm -f sentinel-e2e-api sentinel-e2e-db >/dev/null 2>&1
