#!/usr/bin/env bash
# Audita vários repositórios públicos de perfis diferentes com a API real (imagem sentinel-audit-api:ci)
# e grava um resumo por repositório em calibracao-saida/<dono>__<repo>.json.
# Uso: scripts/calibracao.sh [dono/repo ...]   (padrão: os quatro perfis abaixo)
set -u
REPOS=("$@")
[ ${#REPOS[@]} -eq 0 ] && REPOS=(spring-projects/spring-petclinic gothinkster/angular-realworld-example-app gothinkster/react-redux-realworld-example-app WebGoat/WebGoat)
# o Sentinel audita a si mesmo (repositório privado: precisa de GIT_TOKEN com leitura)
[ -n "${GIT_TOKEN:-}" ] && [ -n "${GITHUB_REPOSITORY:-}" ] && REPOS+=("$GITHUB_REPOSITORY")
KEY="cal-$(date +%s)"; NET="sentinel-cal"; API="http://127.0.0.1:18081"
H=(-H "X-API-Key: $KEY" -H "Content-Type: application/json")
SAIDA="calibracao-saida"; mkdir -p "$SAIDA"

falhar() {
  local r="$1%0A$(docker logs sentinel-cal-api 2>&1 | grep -E 'ERROR|Exception' | tail -8)"
  r="${r//$'\n'/%0A}"; echo "::error title=Calibração falhou::${r:0:3500}"
  docker rm -f sentinel-cal-api sentinel-cal-db >/dev/null 2>&1; exit 1
}

docker network create "$NET" >/dev/null 2>&1
docker run -d --name sentinel-cal-db --network "$NET" -e POSTGRES_DB=sentinel_audit -e POSTGRES_USER=sentinel_audit -e POSTGRES_PASSWORD=cal postgres:16-alpine >/dev/null || falhar "Postgres não subiu"
for _ in $(seq 1 30); do docker exec sentinel-cal-db pg_isready -U sentinel_audit >/dev/null 2>&1 && break; sleep 2; done
docker run -d --name sentinel-cal-api --network "$NET" -p 127.0.0.1:18081:8080 \
  -e DB_URL=jdbc:postgresql://sentinel-cal-db:5432/sentinel_audit -e DB_USER=sentinel_audit -e DB_PASSWORD=cal \
  -e API_KEY="$KEY" -e GIT_ALLOWED_HOSTS=github.com -e SCHEDULER_ENABLED=false -e OSV_ENABLED=false -e TRIVY_ENABLED=false \
  -e GIT_HISTORY_DEPTH=1 -e GIT_TOKEN="${GIT_TOKEN:-}" -e GIT_TOKEN_HOSTS=github.com sentinel-audit-api:ci >/dev/null || falhar "API não subiu"
ok=0; for _ in $(seq 1 60); do curl -fsS "${H[@]}" "$API/api/v1/health" >/dev/null 2>&1 && { ok=1; break; }; sleep 3; done
[ "$ok" = 1 ] || falhar "API não respondeu ao health"

for repo in "${REPOS[@]}"; do
  branch="$(gh api "repos/$repo" --jq .default_branch 2>/dev/null || echo main)"
  inicio=$(date +%s)
  sid="$(curl -sS "${H[@]}" -X POST "$API/api/v1/systems" -d "{\"name\":\"${repo//\//-}\",\"repoUrl\":\"https://github.com/$repo\",\"stack\":\"MIXED\"}" | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])')" || falhar "cadastro de $repo"
  aid="$(curl -sS "${H[@]}" -X POST "$API/api/v1/audits" -d "{\"systemId\":$sid,\"branch\":\"$branch\"}" | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])')" || falhar "pedido de auditoria de $repo"
  status=""
  for _ in $(seq 1 240); do
    resp="$(curl -sS "${H[@]}" "$API/api/v1/audits/$aid")"
    status="$(printf '%s' "$resp" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("status",""))')"
    case "$status" in COMPLETED|FAILED) break;; esac
    sleep 5
  done
  fim=$(date +%s)
  [ "$status" = "COMPLETED" ] || falhar "$repo terminou como '$status': ${resp:0:300}"
  curl -sS "${H[@]}" "$API/api/v1/audits/$aid/findings" | REPO="$repo" SEG=$((fim-inicio)) RESP="$resp" python3 -c '
import json, os, sys, collections
f = json.load(sys.stdin)
resp = json.loads(os.environ["RESP"])
por_regra = collections.Counter("%s/%s" % (x.get("toolSource"), x.get("ruleId")) for x in f)
saida = {"repo": os.environ["REPO"], "total": len(f), "segundos": int(os.environ["SEG"]),
         "nota": (resp.get("riskScore") or {}).get("overall"), "porRegra": dict(por_regra)}
nome = os.environ["REPO"].replace("/", "__")
json.dump(saida, open("calibracao-saida/%s.json" % nome, "w"), ensure_ascii=False, indent=1)
print("%s: %d achados, nota %s, %ds" % (os.environ["REPO"], saida["total"], saida["nota"], saida["segundos"]))
'
done
docker rm -f sentinel-cal-api sentinel-cal-db >/dev/null 2>&1
