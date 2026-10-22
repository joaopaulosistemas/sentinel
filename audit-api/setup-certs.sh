#!/usr/bin/env bash
# Prepara audit-api/certs/ com o CA corporativo para o build do Docker.
# Rode na RAIZ do projeto:
#   ./setup-certs.sh /caminho/do/certificado.(crt|cer|pem)
# Sem argumento, só garante que a pasta existe (com um README) e mostra quem assina a conexão com o PyPI.
set -euo pipefail

CERT_DIR="audit-api/certs"
SRC="${1:-}"

mkdir -p "$CERT_DIR"

if [ -z "$SRC" ]; then
  [ -f "$CERT_DIR/README.md" ] || echo "Coloque aqui o CA raiz da empresa (PEM, .crt)" > "$CERT_DIR/README.md"
  echo "Pasta $CERT_DIR pronta (sem certificado)."
  echo
  echo "Emissores da conexão com pypi.org (se o 'i:' for da empresa, esse é o CA que falta):"
  openssl s_client -connect pypi.org:443 -showcerts </dev/null 2>/dev/null | grep -E "^ *[0-9]+ s:|^ *i:" || true
  echo
  echo "Depois rode: $0 /caminho/do/certificado"
  exit 0
fi

[ -f "$SRC" ] || { echo "Arquivo não encontrado: $SRC" >&2; exit 1; }

NAME="$(basename "${SRC%.*}")"
DEST="$CERT_DIR/${NAME}.crt"

if openssl x509 -in "$SRC" -noout >/dev/null 2>&1; then
  # Já é PEM: só copia
  cp "$SRC" "$DEST"
elif openssl x509 -inform DER -in "$SRC" -noout >/dev/null 2>&1; then
  # DER (binário): converte para PEM
  openssl x509 -inform DER -in "$SRC" -out "$DEST"
  echo "Convertido de DER para PEM."
else
  echo "Formato não reconhecido (esperado PEM ou DER): $SRC" >&2
  exit 1
fi

echo "Certificado instalado em $DEST"
openssl x509 -in "$DEST" -noout -subject -issuer -enddate

echo
echo "Próximo passo: docker compose build --no-cache audit-api"
echo "No log do runtime, 'update-ca-certificates' deve mostrar '1 added'."
