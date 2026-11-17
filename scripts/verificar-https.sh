#!/usr/bin/env bash
# Prova que o overlay de HTTPS termina o TLS e redireciona o HTTP, sem subir o Sentinel inteiro: só o Caddy, com o
# painel ausente (a resposta esperada do proxy é 502, o que já prova que o TLS funcionou e a conexão chegou ao proxy).
# Uso: scripts/verificar-https.sh   (portas 80 e 443 livres no host)
set -u
falhas=0
ok()    { echo "  ok   $1"; }
falha() { echo "::error title=HTTPS::$1"; echo "  FALHA $1"; falhas=$((falhas+1)); }
espera() { local n=$1; shift; for _ in $(seq 1 "$n"); do "$@" >/dev/null 2>&1 && return 0; sleep 2; done; return 1; }
export POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-ci-https}"
compose() { docker compose -f docker-compose.yml -f deploy/https/docker-compose.yml "$@"; }

echo "== Caddyfile válido"
docker run --rm -e SENTINEL_HOST=localhost -v "$PWD/deploy/https/Caddyfile:/etc/caddy/Caddyfile:ro" docker.io/caddy:2-alpine \
  caddy validate --config /etc/caddy/Caddyfile >/dev/null 2>&1 && ok "caddy validate" || falha "o Caddyfile é inválido"

echo "== Subir só o Caddy"
SENTINEL_HOST=localhost compose up -d --no-deps https >/dev/null 2>&1 || falha "o Caddy não subiu"
espera 30 curl -ksS -o /dev/null https://localhost/ && ok "HTTPS respondeu" || falha "nada respondeu em https://localhost"

echo "== TLS e redirecionamento"
cod="$(curl -ksS -o /dev/null -w '%{http_code}' https://localhost/ || true)"
[ "$cod" = "502" ] && ok "TLS terminado; sem painel o proxy devolve 502" || falha "esperava 502 do proxy sem painel, veio '$cod'"
loc="$(curl -sS -o /dev/null -w '%{http_code} %{redirect_url}' http://localhost/ || true)"
case "$loc" in 30[1278]\ https://*) ok "HTTP redireciona para HTTPS ($loc)";; *) falha "HTTP não redirecionou para HTTPS ($loc)";; esac
curl -sS https://localhost/ >/dev/null 2>&1 && falha "o certificado local foi aceito sem instalar a autoridade (esperava aviso)" || ok "o certificado é da autoridade local (sem ela o curl recusa, como o navegador avisa)"

echo "== Derrubar"
compose down -v >/dev/null 2>&1
[ "$falhas" -eq 0 ] && echo "HTTPS ok" || { echo "$falhas falha(s)"; exit 1; }
