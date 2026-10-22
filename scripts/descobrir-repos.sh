#!/usr/bin/env bash
# Escolhe repositórios PÚBLICOS novos para auditar, alternando o perfil de tecnologia a cada semana.
# Uso: scripts/descobrir-repos.sh [quantidade] [perfil]     (precisa do gh autenticado; imprime dono/repo, um por linha)
# perfis: java-spring | angular | react | typescript-node | docker-k8s | auto (alterna pela semana do ano)
# Não repete o que já está em audit-rules/calibracao/descobertos.txt nem nos repositórios fixos da calibração.
set -u
N="${1:-6}"; PERFIL="${2:-auto}"
HIST="audit-rules/calibracao/descobertos.txt"; touch "$HIST"
# Calendário ponderado pelo que importa para o Sentinel (Java/Spring e Angular em ambiente corporativo; Docker/K8s):
# 2 de cada 5 semanas Java/Spring, 1 Angular, 1 Docker/K8s, 1 alterna React ou Node. Os perfis que já têm muita evidência
# (TypeScript no servidor, já com 9 repositórios revisados) só entram se pedidos com o parâmetro `perfil`.
PERFIS=(java-spring angular java-spring docker-k8s react)
if [ "$PERFIL" = auto ]; then PERFIL="${PERFIS[$(( 10#$(date +%V) % ${#PERFIS[@]} ))]}"; fi
echo "perfil: $PERFIL" >&2

# Procuramos aplicações de verdade, não esqueletos: tamanho mínimo, atividade recente e termos de domínio de negócio.
# Termos de negócio puxam sistemas com regra de negócio, camadas e persistência (o que um sistema corporativo tem).
case "$PERFIL" in
  java-spring)     LANG=java;       TERMOS=("spring boot microservices" "spring boot jpa rest api" "spring security jwt" "jakarta ee" "spring boot banking" "spring boot ecommerce" "spring boot erp") ;;
  angular)         LANG=typescript; TERMOS=("angular admin" "angular erp" "angular ngrx enterprise" "angular dashboard") ;;
  react)           LANG=typescript; TERMOS=("react admin" "react redux enterprise" "next.js dashboard") ;;
  typescript-node) LANG=typescript; TERMOS=("nestjs microservices" "express api" "node api") ;;
  docker-k8s)      LANG=dockerfile; TERMOS=("docker compose production" "kubernetes manifests" "helm chart") ;;
  *) echo "perfil desconhecido: $PERFIL" >&2; exit 2 ;;
esac

DESDE="$(date -d '-18 months' +%Y-%m-%d 2>/dev/null || date -v-18m +%Y-%m-%d)"
TMP="$(mktemp)"
for termo in "${TERMOS[@]}"; do
  gh search repos "$termo" --language="$LANG" --stars="300..30000" --updated=">=$DESDE" --archived=false \
    --limit 60 --json fullName,size,isFork,license -q '.[]|select(.isFork|not)|select(.size>=800 and .size<150000)|select(.fullName|test("(starter|boilerplate|template|tutorial|example|demo|course|learn|awesome|sample|bootcamp|cheatsheet|workshop|kata|playground)";"i")|not)|.fullName' >> "$TMP" 2>/dev/null
  sleep 2
done
# remove repetidos, o histórico, os repositórios fixos da calibração, e embaralha
FIXOS="$(python3 -c 'import json;print("\n".join(json.load(open("audit-rules/calibracao/esperado.json"))["repositorios"]))')"
sort -u "$TMP" | grep -v -i -x -F -f <(cat "$HIST" <(echo "$FIXOS") <(echo "${GITHUB_REPOSITORY:-}")) | shuf | head -n "$N"
rm -f "$TMP"
