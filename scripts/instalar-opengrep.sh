#!/usr/bin/env bash
# Instala o Opengrep fixado, com conferência de SHA-256. Uso: scripts/instalar-opengrep.sh [pasta-destino]
# A versão e os hashes são os mesmos do audit-api/Dockerfile (ARG OPENGREP_*): atualize os dois juntos.
# Destino padrão: ~/.local/bin; no GitHub Actions a pasta entra no PATH dos próximos passos.
set -euo pipefail

VERSAO="v1.30.0"
SHA_X86="35779bdd72e92129c8df2a77f0c55e8c08356801ea92591ef32108d6b28d564c"
SHA_ARM="a5d5a4a58ba5d46ff51e921663da1c2bba38f4b03987f4aeec87f16c6ad3ecae"

destino="${1:-$HOME/.local/bin}"
case "$(uname -m)" in
  x86_64|amd64)  arq="x86";     sha="$SHA_X86" ;;
  aarch64|arm64) arq="aarch64"; sha="$SHA_ARM" ;;
  *) echo "Arquitetura sem binário do Opengrep: $(uname -m)" >&2; exit 1 ;;
esac

tmp="$(mktemp)"
trap 'rm -f "$tmp"' EXIT
curl -fsSL -o "$tmp" "https://github.com/opengrep/opengrep/releases/download/${VERSAO}/opengrep_manylinux_${arq}"
echo "${sha}  ${tmp}" | sha256sum -c - >/dev/null || { echo "SHA-256 do Opengrep ${VERSAO} não confere" >&2; exit 1; }

mkdir -p "$destino"
install -m 0755 "$tmp" "$destino/opengrep"
[ -n "${GITHUB_PATH:-}" ] && echo "$destino" >> "$GITHUB_PATH"
echo "Opengrep ${VERSAO} instalado em $destino/opengrep"
