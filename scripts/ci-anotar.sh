#!/usr/bin/env bash
# Executa um comando e, se falhar, publica o final da saída como anotação do GitHub Actions.
# Serve para ler a causa da falha pela API de anotações (útil quando o log completo não está acessível).
# Uso: scripts/ci-anotar.sh <titulo> <comando> [args...]
titulo="$1"; shift
saida="$(mktemp)"
set +e
"$@" 2>&1 | tee "$saida"
codigo=${PIPESTATUS[0]}
if [ "$codigo" -ne 0 ]; then
  # procura as linhas mais informativas; se não houver, usa o final da saída
  resumo="$(grep -E "ERROR|FAIL|✖|Missed|rror:|Tests run:.*(Failures|Errors): [1-9]" "$saida" | head -25)"
  [ -z "$resumo" ] && resumo="$(tail -25 "$saida")"
  resumo="${resumo//'%'/'%25'}"; resumo="${resumo//$'\r'/}"; resumo="${resumo//$'\n'/'%0A'}"
  echo "::error title=${titulo} falhou::${resumo:0:3500}"
fi
exit "$codigo"
