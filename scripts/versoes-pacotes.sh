#!/usr/bin/env bash
# Lista "pacote versão" de cada pack.yml (para as notas de release).
for f in audit-rules/packs/*/pack.yml; do
  id="$(grep -m1 '^id:' "$f" | sed 's/^id:[[:space:]]*//')"
  v="$(grep -m1 '^version:' "$f" | sed 's/^version:[[:space:]]*//')"
  printf -- '- `%s` %s\n' "$id" "$v"
done
