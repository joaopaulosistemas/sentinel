#!/usr/bin/env bash
# O Sentinel audita o próprio código com as próprias regras (pacotes de audit-rules/packs) e falha se achar algo de
# severidade ERROR (alta). Warnings e infos ficam no relatório, sem barrar. Usa o Opengrep do CI (ou o Semgrep, se for o que há).
# Uso: auditar-sentinel.sh [pasta-ou-arquivo ...]   (sem argumentos: o código de produção do repositório)
set -euo pipefail
cd "$(dirname "$0")/.."

motor="$(command -v opengrep || command -v semgrep || true)"
[ -n "$motor" ] || { echo "::error title=Auto-auditoria::nem opengrep nem semgrep no PATH"; exit 1; }

configs=()
for d in audit-rules/packs/*/rules; do configs+=(--config "$d"); done

alvos=("$@")
[ "${#alvos[@]}" -gt 0 ] || alvos=(audit-api/src/main audit-cli/src/main audit-dashboard/src deploy docker-compose.yml)

saida="$(mktemp)"
"$motor" scan "${configs[@]}" --json --quiet --metrics=off --timeout 20 -o "$saida" \
  "${alvos[@]}" || true

python3 -I - "$saida" <<'PY'
import json, sys
achados = json.load(open(sys.argv[1], encoding="utf-8"))["results"]
por_sev = {}
for a in achados:
    por_sev.setdefault(a["extra"]["severity"], []).append(a)
print("Auto-auditoria:", {k: len(v) for k, v in sorted(por_sev.items())} or "sem achados")
altos = por_sev.get("ERROR", [])
for a in altos:
    print(f"::error file={a['path']},line={a['start']['line']},title={a['check_id'].split('.')[-1]}::{a['extra']['message'][:200]}")
sys.exit(1 if altos else 0)
PY
