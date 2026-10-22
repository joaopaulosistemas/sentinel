#!/usr/bin/env bash
# Escolhe repositórios PÚBLICOS novos para auditar, alternando o perfil de tecnologia a cada semana.
# Uso: scripts/descobrir-repos.sh [quantidade] [perfil]     (precisa do gh autenticado; imprime dono/repo, um por linha)
# perfis: java-spring | angular | react | typescript-node | docker-k8s | auto (alterna pela semana do ano)
# Não repete o que já está em audit-rules/calibracao/descobertos.txt nem nos repositórios fixos da calibração.
set -u
N="${1:-6}"; PERFIL="${2:-auto}"
HIST="audit-rules/calibracao/descobertos.txt"; touch "$HIST"
PERFIS=(java-spring angular react typescript-node docker-k8s)
if [ "$PERFIL" = auto ]; then PERFIL="${PERFIS[$(( 10#$(date +%V) % ${#PERFIS[@]} ))]}"; fi
echo "perfil: $PERFIL" >&2

case "$PERFIL" in
  java-spring)     LANG=java;       TERMOS=("spring boot" "spring boot jpa" "quarkus") ;;
  angular)         LANG=typescript; TERMOS=("angular" "angular ngrx") ;;
  react)           LANG=typescript; TERMOS=("react" "react redux" "next.js") ;;
  typescript-node) LANG=typescript; TERMOS=("express api" "nestjs" "node api") ;;
  docker-k8s)      LANG=dockerfile; TERMOS=("docker compose" "kubernetes manifests" "helm chart") ;;
  *) echo "perfil desconhecido: $PERFIL" >&2; exit 2 ;;
esac

DESDE="$(date -d '-18 months' +%Y-%m-%d 2>/dev/null || date -v-18m +%Y-%m-%d)"
TMP="$(mktemp)"
for termo in "${TERMOS[@]}"; do
  gh search repos "$termo" --language="$LANG" --stars="200..8000" --updated=">=$DESDE" --archived=false \
    --limit 40 --json fullName,size,isFork,license -q '.[]|select(.isFork|not)|select(.size<60000)|.fullName' >> "$TMP" 2>/dev/null
  sleep 2
done
# remove repetidos, o histórico, os repositórios fixos da calibração, e embaralha
FIXOS="$(python3 -c 'import json;print("\n".join(json.load(open("audit-rules/calibracao/esperado.json"))["repositorios"]))')"
sort -u "$TMP" | grep -v -i -x -F -f <(cat "$HIST" <(echo "$FIXOS") <(echo "${GITHUB_REPOSITORY:-}")) | shuf | head -n "$N"
rm -f "$TMP"
