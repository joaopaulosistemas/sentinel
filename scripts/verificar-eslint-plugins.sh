#!/usr/bin/env bash
# Garante que o ESLint do Sentinel (audit-rules/lint/eslint) instala com o package-lock.json e que os plugins
# rodam de verdade. Regressão que isto guarda: o `overrides` global do minimatch (CVE) forçou a versão 11 no
# eslint-plugin-jsx-a11y, que só funciona com a 3.x; a regra label-has-associated-control passou a lançar
# `TypeError: (0 , _minimatch.default) is not a function` e o ESLint saía com código 2 (resultado vazio).
# Uso: bash scripts/verificar-eslint-plugins.sh
set -euo pipefail

raiz="$(cd "$(dirname "$0")/.." && pwd)"
origem="$raiz/audit-rules/lint/eslint"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

cp "$origem"/package.json "$origem"/package-lock.json "$origem"/*.mjs "$tmp"/
(cd "$tmp" && npm ci --ignore-scripts --no-audit --no-fund >/dev/null)

mkdir "$tmp/projeto"
cat > "$tmp/projeto/Rotulo.tsx" <<'TSX'
export function Rotulo() {
  return (
    <div>
      <label htmlFor="nome">Nome</label>
      <input id="nome" />
      <label><span>sem controle associado</span></label>
      {[1, 2].map((n) => <li>{n}</li>)}
    </div>
  );
}
TSX

saida="$tmp/saida.txt"
codigo=0
(cd "$tmp/projeto" && "$tmp/node_modules/.bin/eslint" -c "$tmp/eslint.config.mjs" . >"$saida" 2>&1) || codigo=$?

# 0 = sem achados, 1 = achados; 2 = falha interna do ESLint ou de um plugin.
if [ "$codigo" -ge 2 ]; then
  echo "ERRO: o ESLint terminou com código $codigo (falha interna, não achado)." >&2
  cat "$saida" >&2
  exit 1
fi
if ! grep -q "jsx-a11y/label-has-associated-control" "$saida"; then
  echo "ERRO: a regra jsx-a11y/label-has-associated-control não produziu o achado esperado." >&2
  cat "$saida" >&2
  exit 1
fi
# eslint-plugin-react não declara suporte ao ESLint 10 e chama APIs removidas; o fixupPluginRules da config as devolve.
# Sem ele, o ESLint sai com código 2 (acima); com ele, a regra tem que produzir o achado.
if ! grep -q "react/jsx-key" "$saida"; then
  echo "ERRO: a regra react/jsx-key não produziu o achado esperado." >&2
  cat "$saida" >&2
  exit 1
fi
echo "ESLint e plugins rodam com o lock atual (minimatch do jsx-a11y: $(cd "$tmp" && npm ls minimatch 2>/dev/null | grep -A1 'jsx-a11y' | tail -1 | sed 's/.*minimatch@//; s/ .*//'))."
