#!/usr/bin/env bash
# Prova de ponta a ponta da camada de observabilidade: com a pilha no ar (compose + overlay), roda uma auditoria
# pequena e confere que métricas, logs, traces e o painel do Grafana existem de verdade.
# Uso: scripts/verificar-observabilidade.sh   (API em :8080, Prometheus em :9090, Grafana em :3001)
set -u
API="http://127.0.0.1:8080"; PROM="http://127.0.0.1:9090"; GRAF="http://127.0.0.1:3001"; GA="admin:${GRAFANA_PASSWORD:-sentinel}"
falhas=0
ok()    { echo "  ok   $1"; }
falha() { echo "::error title=Observabilidade::$1"; echo "  FALHA $1"; falhas=$((falhas+1)); }
espera() { local n=$1; shift; for _ in $(seq 1 "$n"); do "$@" >/dev/null 2>&1 && return 0; sleep 3; done; return 1; }
prom() { curl -fsS --get "$PROM/api/v1/query" --data-urlencode "query=$1" | python3 -c 'import json,sys; r=json.load(sys.stdin)["data"]["result"]; print(max([float(x["value"][1]) for x in r] or [0]))'; }

echo "== API no ar"
espera 60 curl -fsS "$API/api/v1/health" && ok "API respondeu ao health" || { falha "a API não subiu"; docker compose logs --tail 60 audit-api; exit 1; }

echo "== uma auditoria pequena (gera métricas, logs e traces)"
sid="$(curl -fsS -H 'Content-Type: application/json' -X POST "$API/api/v1/systems" -d '{"name":"obs-hello","repoUrl":"https://github.com/octocat/Hello-World","stack":"MIXED","gitHistoryDepth":1}' | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])')"
aid="$(curl -fsS -H 'Content-Type: application/json' -X POST "$API/api/v1/audits" -d "{\"systemId\":$sid,\"branch\":\"master\"}" | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])')"
status=""
for _ in $(seq 1 120); do
  status="$(curl -fsS "$API/api/v1/audits/$aid" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("status",""))')"
  case "$status" in COMPLETED|FAILED) break;; esac; sleep 3
done
[ "$status" = "COMPLETED" ] && ok "auditoria concluída" || falha "a auditoria terminou como '$status'"

echo "== Prometheus"
espera 40 bash -c "[ \"\$(curl -fsS $PROM/api/v1/targets | python3 -c 'import json,sys; print(sum(1 for t in json.load(sys.stdin)[\"data\"][\"activeTargets\"] if t[\"labels\"][\"job\"]==\"sentinel-api\" and t[\"health\"]==\"up\"))')\" = 1 ]" \
  && ok "o alvo sentinel-api está up" || falha "o Prometheus não consegue raspar audit-api:9000"
sleep 20   # uma raspagem depois da auditoria
for q in 'sum(sentinel_analyzer_duration_seconds_count)' 'sum(sentinel_audit_duration_seconds_count{outcome="COMPLETED"})' 'sum(http_server_requests_seconds_count)' 'sum(jvm_memory_used_bytes)' 'sum(agroal_active_count)+1'; do
  v="$(prom "$q" 2>/dev/null || echo 0)"
  python3 -c "import sys; sys.exit(0 if float('$v')>0 else 1)" && ok "$q = $v" || falha "métrica ausente ou zerada: $q"
done
curl -fsS "$PROM/api/v1/rules" | python3 -c 'import json,sys; g=json.load(sys.stdin)["data"]["groups"]; sys.exit(0 if sum(len(x["rules"]) for x in g)>=5 else 1)' \
  && ok "regras de alerta carregadas" || falha "as regras de alerta não foram carregadas"

echo "== Grafana"
curl -fsS -u "$GA" "$GRAF/api/search?query=Sentinel" | grep -q sentinel-visao-geral && ok "painel 'Sentinel — visão geral' provisionado" || falha "painel não encontrado no Grafana"
for ds in prometheus loki; do
  espera 30 bash -c "curl -fsS -u '$GA' '$GRAF/api/datasources/uid/$ds/health' | grep -q '\"status\":\"OK\"'" && ok "fonte de dados $ds saudável" || falha "fonte de dados $ds não está saudável"
done
# O teste de saúde do Grafana para o Tempo faz uma busca de exemplo; a prova real é a busca de traces mais abaixo.
echo "  info saúde do Tempo no Grafana: $(curl -sS -u "$GA" "$GRAF/api/datasources/uid/tempo/health" | head -c 300)"
echo "== painel: todas as consultas do painel respondem"
python3 - "$GRAF" "$GA" <<'PY' || falhas=$((falhas+1))
import json, sys, urllib.parse, urllib.request, base64
graf, ga = sys.argv[1], sys.argv[2]
h = {"Authorization": "Basic " + base64.b64encode(ga.encode()).decode()}
def get(u):
    return json.load(urllib.request.urlopen(urllib.request.Request(graf + u, headers=h), timeout=30))
dash = get("/api/dashboards/uid/sentinel-visao-geral")["dashboard"]
ruins = 0
for p in dash["panels"]:
    for t in p.get("targets", []):
        if p["datasource"]["type"] != "prometheus":
            continue
        q = urllib.parse.quote(t["expr"])
        r = get("/api/datasources/proxy/uid/prometheus/api/v1/query?query=" + q)
        if r.get("status") != "success":
            print("  FALHA consulta inválida no painel '%s': %s" % (p["title"], t["expr"])); ruins += 1
print("  ok   %d painéis; consultas Prometheus válidas" % len(dash["panels"]) if not ruins else "")
sys.exit(1 if ruins else 0)
PY
echo "== logs (Loki) e traces (Tempo), via Grafana"
espera 30 bash -c "curl -fsS -u '$GA' --get '$GRAF/api/datasources/proxy/uid/loki/loki/api/v1/query_range' --data-urlencode 'query={service_name=\"sentinel-audit-api\"}' | python3 -c 'import json,sys; sys.exit(0 if json.load(sys.stdin)[\"data\"][\"result\"] else 1)'" \
  && ok "logs da API chegaram ao Loki" || falha "nenhum log da API no Loki"
espera 30 bash -c "curl -fsS -u '$GA' '$GRAF/api/datasources/proxy/uid/tempo/api/search?limit=5' | python3 -c 'import json,sys; sys.exit(0 if json.load(sys.stdin).get(\"traces\") else 1)'" \
  && ok "traces da API chegaram ao Tempo" || falha "nenhum trace da API no Tempo"

[ "$falhas" -eq 0 ] && echo "OBSERVABILIDADE OK" || { echo "$falhas verificação(ões) falharam"; docker compose logs --tail 40 otel-collector prometheus loki tempo | tail -120; exit 1; }
