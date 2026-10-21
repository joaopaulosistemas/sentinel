#!/usr/bin/env bash
# Coleta as evidências de banco para o Sentinel: o esquema (sem dados) e as estatísticas de uso.
# Uso:  ./coletar-evidencias.sh "postgresql://usuario@servidor:5432/banco" [pasta-de-saida]
# Precisa dos clientes do Postgres (pg_dump e psql). Nada é alterado no banco.
set -euo pipefail
URL="${1:?informe a URL de conexão do Postgres}"
OUT="${2:-./saida-evidencias}"
mkdir -p "$OUT"
HERE="$(cd "$(dirname "$0")" && pwd)"

# Esquema: só as definições (tabelas, chaves, índices). --schema-only garante que nenhum dado sai do banco.
pg_dump --schema-only --no-owner --no-privileges --no-comments "$URL" > "$OUT/schema.sql"

# Estatísticas de uso (consultas caras, tabelas, índices).
( cd "$OUT" && psql "$URL" -v ON_ERROR_STOP=0 -f "$HERE/coletar-evidencias.sql" )

echo "Pronto. Copie o conteúdo de '$OUT' para ./evidencias/<nome-do-sistema>/"
