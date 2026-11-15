#!/usr/bin/env bash
# Mede o ruído dos pacotes de arquitetura em projetos públicos: audita cada "dono/repo:perfil" com a API real
# (imagem sentinel-audit-api:ci) e lista só os achados do analisador de arquitetura (pacotes).
# Uso: scripts/calibracao-arquitetura.sh dono/repo:perfil [...]
set -u
KEY="cal-$(date +%s)"; NET="sentinel-calarq"; API="http://127.0.0.1:18082"
H=(-H "X-API-Key: $KEY" -H "Content-Type: application/json")
SAIDA="${SAIDA:-calibracao-arq-saida}"; mkdir -p "$SAIDA"
docker network create "$NET" >/dev/null 2>&1
docker run -d --name calarq-db --network "$NET" -e POSTGRES_DB=sentinel_audit -e POSTGRES_USER=sentinel_audit -e POSTGRES_PASSWORD=cal postgres:16-alpine >/dev/null
for _ in $(seq 1 30); do docker exec calarq-db pg_isready -U sentinel_audit >/dev/null 2>&1 && break; sleep 2; done
docker run -d --name calarq-api --network "$NET" -p 127.0.0.1:18082:8080 \
  -e DB_URL=jdbc:postgresql://calarq-db:5432/sentinel_audit -e DB_USER=sentinel_audit -e DB_PASSWORD=cal \
  -e API_KEY="$KEY" -e SCHEDULER_ENABLED=false -e OSV_ENABLED=false -e TRIVY_ENABLED=false \
  sentinel-audit-api:ci >/dev/null
for _ in $(seq 1 60); do curl -fsS "${H[@]}" "$API/api/v1/health" >/dev/null 2>&1 && break; sleep 3; done
for par in "$@"; do
  repo="${par%%:*}"; perfil="${par##*:}"
  branch="$(gh api "repos/$repo" --jq .default_branch 2>/dev/null || echo main)"
  sid="$(curl -sS "${H[@]}" -X POST "$API/api/v1/systems" -d "{\"name\":\"${repo//\//-}\",\"repoUrl\":\"https://github.com/$repo\",\"stack\":\"MIXED\",\"gitHistoryDepth\":1}" | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])')"
  curl -sS "${H[@]}" -X PUT "$API/api/v1/systems/$sid/profile" -d "{\"profile\":\"$perfil\"}" >/dev/null
  aid="$(curl -sS "${H[@]}" -X POST "$API/api/v1/audits" -d "{\"systemId\":$sid,\"branch\":\"$branch\"}" | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])')"
  status=""
  for _ in $(seq 1 240); do
    status="$(curl -sS "${H[@]}" "$API/api/v1/audits/$aid" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("status",""))')"
    case "$status" in COMPLETED|FAILED) break;; esac; sleep 5
  done
  echo "=== $repo ($perfil): $status"
  curl -sS "${H[@]}" "$API/api/v1/audits/$aid/findings" > "$SAIDA/${repo//\//__}.json"
  REPO="$repo" ARQ="$SAIDA/${repo//\//__}.json" python3 - <<'PY'
import json, os, collections
f = [x for x in json.load(open(os.environ["ARQ"])) if "Arquitetura" in (x.get("toolSource") or "")]
print("achados de arquitetura:", len(f), dict(collections.Counter(x["ruleId"] for x in f)))
for x in f[:60]:
    print("  %s | %s:%s | %s" % (x["ruleId"], x.get("filePath"), x.get("lineNumber"), (x.get("codeSnippet") or "")[:90].replace("\n", " ")))
PY
done
docker rm -f calarq-api calarq-db >/dev/null 2>&1
