#!/usr/bin/env bash
# Roda o taint entre arquivos do Sentinel com o Joern e grava o SARIF.
# Uso: rodar-joern.sh <pasta-do-projeto> <saida.sarif>
# O Joern grava uma pasta "workspace" no diretório atual; por isso roda dentro de uma pasta temporária,
# apagada no fim, e nada é criado dentro do projeto auditado.
# Variáveis: JOERN_HOME (padrão /opt/joern/joern-cli), SENTINEL_JOERN_XMX (padrão 4g).
set -euo pipefail

projeto="${1:?uso: rodar-joern.sh <projeto> <saida.sarif>}"
saida="${2:?uso: rodar-joern.sh <projeto> <saida.sarif>}"
aqui="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
joern="${JOERN_HOME:-/opt/joern/joern-cli}/joern"
[ -x "$joern" ] || { echo "Joern não encontrado em $joern (defina JOERN_HOME)" >&2; exit 127; }

projeto="$(cd "$projeto" && pwd)"
case "$saida" in /*) ;; *) saida="$PWD/$saida" ;; esac

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
cd "$tmp"
"$joern" -J-Xmx"${SENTINEL_JOERN_XMX:-4g}" --nocolors \
  --script "$aqui/taint-entre-arquivos.sc" \
  --param inputPath="$projeto" --param outFile="$saida" >"$tmp/joern.log" 2>&1 \
  || { tail -n 30 "$tmp/joern.log" >&2; exit 1; }
