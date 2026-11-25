#!/usr/bin/env bash
# Prova de ponta a ponta do login: com a pilha de deploy/keycloak no ar, pega tokens reais do Keycloak e confere que a API
# valida o token e aplica os papéis (admin e executor). Uso: bash scripts/verificar-login-keycloak.sh
set -u
MAILPIT="${MAILPIT:-http://localhost:8025}"; KC_ADMIN_SENHA="${KEYCLOAK_ADMIN_PASSWORD:-kcadmin}"
KC="${KC:-http://localhost:8180}"; API="${API:-http://localhost:8080}"; PAINEL="${PAINEL:-http://localhost:3000}"
REALM="$KC/realms/sentinel"; FALHAS=0
ok()    { echo "  ok    $*"; }
falha() { echo "  FALHA $*"; FALHAS=$((FALHAS+1)); }
espera() { local n=$1; shift; for _ in $(seq 1 "$n"); do "$@" >/dev/null 2>&1 && return 0; sleep 3; done; return 1; }

# token <usuário> <senha> <cliente>  ->  imprime o access_token ("" se o Keycloak recusar)
token() {
  curl -sS -d grant_type=password -d "client_id=$3" -d "username=$1" -d "password=$2" "$REALM/protocol/openid-connect/token" \
    | python3 -c 'import json,sys; print(json.load(sys.stdin).get("access_token",""))' 2>/dev/null
}
# codigo <método> <caminho> <token> [corpo]  ->  imprime só o status HTTP
codigo() {
  local args=(-sS -o /dev/null -w '%{http_code}' -X "$1" "$API$2")
  [ -n "$3" ] && args+=(-H "Authorization: Bearer $3")
  [ -n "${4:-}" ] && args+=(-H 'Content-Type: application/json' -d "$4")
  curl "${args[@]}"
}
espera_codigo() { # confere um status esperado e registra
  local esperado=$1 descricao=$2 obtido=$3
  [ "$obtido" = "$esperado" ] && ok "$descricao ($obtido)" || falha "$descricao: esperava $esperado e veio $obtido"
}

echo "== Subida"
espera 60 curl -fsS "$REALM/.well-known/openid-configuration" && ok "Keycloak com o realm sentinel" || { falha "Keycloak não respondeu"; exit 1; }
espera 80 curl -fsS "$API/api/v1/health" && ok "API no ar" || { falha "API não respondeu"; exit 1; }

echo "== Tokens"
ADMIN="$(token admin admin sentinel-cli)"; EXEC="$(token executor executor sentinel-cli)"; SEMAUD="$(token admin admin sentinel-teste-sem-audiencia)"
[ -n "$ADMIN" ] && ok "token do admin" || falha "Keycloak não emitiu token do admin"
[ -n "$EXEC" ]  && ok "token do executor" || falha "Keycloak não emitiu token do executor"
[ -n "$SEMAUD" ] && ok "token sem audiência (para o teste de recusa)" || falha "sem token do cliente sem audiência"
[ -z "$(token admin errada sentinel-cli)" ] && ok "senha errada não gera token" || falha "senha errada gerou token"

echo "== A API valida o token"
espera_codigo 200 "health é público" "$(codigo GET /api/v1/health '')"
espera_codigo 401 "sem token" "$(codigo GET /api/v1/systems '')"
espera_codigo 401 "token inventado" "$(codigo GET /api/v1/systems 'isto.nao.e.um.jwt')"
espera_codigo 401 "token de outro destino (sem audiência sentinel-api)" "$(codigo GET /api/v1/systems "$SEMAUD")"

echo "== Perfil (/api/v1/me)"
ME_ADMIN="$(curl -sS -H "Authorization: Bearer $ADMIN" "$API/api/v1/me")"; ME_EXEC="$(curl -sS -H "Authorization: Bearer $EXEC" "$API/api/v1/me")"
echo "$ME_ADMIN" | python3 -c 'import json,sys; d=json.load(sys.stdin); sys.exit(0 if d["username"]=="admin" and d["admin"] and "sentinel-admin" in d["roles"] else 1)' && ok "admin: nome e papel lidos do token" || falha "perfil do admin: $ME_ADMIN"
echo "$ME_EXEC" | python3 -c 'import json,sys; d=json.load(sys.stdin); sys.exit(0 if d["username"]=="executor" and not d["admin"] and d["canExecute"] else 1)' && ok "executor: nome e papel lidos do token" || falha "perfil do executor: $ME_EXEC"

echo "== Papéis"
# Corpo vazio: se a rota deixou passar, a validação responde 400; se o papel barrou, responde 403.
espera_codigo 200 "executor lê sistemas" "$(codigo GET /api/v1/systems "$EXEC")"
espera_codigo 200 "admin lê sistemas" "$(codigo GET /api/v1/systems "$ADMIN")"
espera_codigo 403 "executor NÃO cadastra sistema" "$(codigo POST /api/v1/systems "$EXEC" '{}')"
espera_codigo 400 "admin cadastra sistema (passou pelo papel; corpo vazio é recusado pela validação)" "$(codigo POST /api/v1/systems "$ADMIN" '{}')"
espera_codigo 400 "executor dispara auditoria (passou pelo papel; corpo vazio é recusado pela validação)" "$(codigo POST /api/v1/audits "$EXEC" '{}')"
espera_codigo 403 "executor NÃO lê configurações" "$(codigo GET /api/v1/settings "$EXEC")"
espera_codigo 200 "admin lê configurações" "$(codigo GET /api/v1/settings "$ADMIN")"

echo "== Tela de login (tema do Sentinel)"
TELA="$(curl -sS "$REALM/protocol/openid-connect/auth?client_id=sentinel-dashboard&redirect_uri=http://localhost:3000/&response_type=code&scope=openid&code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM&code_challenge_method=S256")"
echo "$TELA" | grep -q 'sentinel.css' && ok "a tela de login usa o CSS do tema sentinel" || falha "a tela de login não referencia sentinel.css (tema não aplicado)"
echo "$TELA" | grep -qi 'lang="pt-BR"' && ok "a tela de login está em português" || falha "a tela de login não está em pt-BR"
CSS_URL="$(echo "$TELA" | grep -o '[^"]*sentinel\.css' | head -1)"
case "$CSS_URL" in http*) ;; /*) CSS_URL="$KC$CSS_URL" ;; esac
curl -fsS "$CSS_URL" 2>/dev/null | grep -q 'IBM Plex Sans' && ok "o CSS do tema é servido (IBM Plex Sans)" || falha "CSS do tema não servido: $CSS_URL"
curl -fsS -o /dev/null "${CSS_URL%css/sentinel.css}fonts/ibm-plex-sans-latin-400-normal.woff2" && ok "a fonte do tema é servida" || falha "fonte do tema não servida"

echo "== Redefinição de senha por e-mail (Mailpit)"
echo "$TELA" | grep -q 'reset-credentials' && ok "a tela de login oferece \"Esqueceu a senha?\"" || falha "a tela de login não tem o link de redefinição de senha"
espera 20 curl -fsS "$MAILPIT/api/v1/messages" && ok "Mailpit no ar" || falha "Mailpit não respondeu em $MAILPIT"
curl -sS -X DELETE "$MAILPIT/api/v1/messages" >/dev/null 2>&1
KC_TOKEN="$(curl -sS -d grant_type=password -d client_id=admin-cli -d username=kcadmin --data-urlencode "password=$KC_ADMIN_SENHA" "$KC/realms/master/protocol/openid-connect/token" \
  | python3 -c 'import json,sys; print(json.load(sys.stdin).get("access_token",""))' 2>/dev/null)"
USER_ID="$(curl -sS -H "Authorization: Bearer $KC_TOKEN" "$KC/admin/realms/sentinel/users?username=admin&exact=true" \
  | python3 -c 'import json,sys; d=json.load(sys.stdin); print(d[0]["id"] if d else "")' 2>/dev/null)"
if [ -n "$KC_TOKEN" ] && [ -n "$USER_ID" ]; then
  ENVIO="$(curl -sS -o /dev/null -w '%{http_code}' -X PUT -H "Authorization: Bearer $KC_TOKEN" -H 'Content-Type: application/json' -d '["UPDATE_PASSWORD"]' \
    "$KC/admin/realms/sentinel/users/$USER_ID/execute-actions-email?client_id=sentinel-dashboard&redirect_uri=http://localhost:3000/")"
  [ "$ENVIO" = "204" ] && ok "o Keycloak aceitou enviar o e-mail de redefinição (204)" || falha "envio do e-mail de redefinição: esperava 204 e veio $ENVIO"
  CHEGOU=""
  for _ in $(seq 1 15); do
    CHEGOU="$(curl -sS "$MAILPIT/api/v1/messages" | python3 -c 'import json,sys; m=[x for x in json.load(sys.stdin).get("messages",[]) if any(t["Address"]=="admin@sentinel.local" for t in x["To"])]; print(m[0]["ID"] if m else "")' 2>/dev/null)"
    [ -n "$CHEGOU" ] && break; sleep 2
  done
  if [ -n "$CHEGOU" ]; then
    ok "o e-mail chegou ao Mailpit para admin@sentinel.local"
    curl -sS "$MAILPIT/api/v1/message/$CHEGOU" | grep -q 'action-token' && ok "o e-mail traz o link de redefinição" || falha "o e-mail não traz o link (action-token)"
  else
    falha "nenhum e-mail para admin@sentinel.local chegou ao Mailpit"
  fi
else
  falha "sem token de administrador do Keycloak ou usuário admin não encontrado (não foi possível pedir o e-mail)"
fi

echo "== Swagger UI"
SW="$(curl -sS -o /dev/null -w '%{http_code}' -L "$API/swagger-ui/")"
[ "$SW" = "200" ] && ok "Swagger UI servido pela imagem (200)" || falha "Swagger UI: esperava 200 e veio $SW"

echo "== Painel"
curl -fsS "$PAINEL/config.json" | python3 -c 'import json,sys; d=json.load(sys.stdin); sys.exit(0 if d["oidcAuthority"].endswith("/realms/sentinel") and d["oidcClientId"]=="sentinel-dashboard" else 1)' \
  && ok "o painel publica a configuração do login" || falha "config.json do painel"
PAINEL_ME="$(curl -sS -H "Authorization: Bearer $EXEC" "$PAINEL/api/v1/me")"
echo "$PAINEL_ME" | grep -q '"executor"' && ok "o proxy do painel repassa o token do usuário à API" || falha "proxy do painel: $PAINEL_ME"

if [ "$FALHAS" -eq 0 ]; then echo "LOGIN OK"; else echo "$FALHAS verificação(ões) falharam"; exit 1; fi
