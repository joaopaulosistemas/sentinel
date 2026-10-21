// Formatador SARIF para o Stylelint (usado pelo pacote market-tools). Recebe os resultados e devolve o SARIF 2.1.0.
"use strict";
const path = require("path");

// CSS gerado: um .css cujo nome também existe como .scss/.sass/.less no projeto é saída de compilação
// (milhares de achados que ninguém vai corrigir à mão). O erro está no .scss de origem.
const FONTES = /\.(scss|sass|less)$/i;

module.exports = function sarifFormatter(results) {
  const rules = new Map();
  const out = [];
  const nomesDeFonte = new Set(
    results.filter((r) => FONTES.test(r.source || "")).map((r) => path.basename(r.source).replace(FONTES, "").toLowerCase()),
  );
  for (const file of results) {
    if (/\.css$/i.test(file.source || "") && nomesDeFonte.has(path.basename(file.source).replace(/\.css$/i, "").toLowerCase())) {
      continue;
    }
    const uri = path.relative(process.cwd(), file.source || "").split(path.sep).join("/");
    for (const w of file.warnings || []) {
      const id = w.rule || "stylelint-erro";
      rules.set(id, { id, shortDescription: { text: id } });
      out.push({
        ruleId: id,
        level: w.severity === "error" ? "error" : "warning",
        message: { text: String(w.text || id).replace(/\s*\([^()]*\)\s*$/, "") },
        locations: [{ physicalLocation: { artifactLocation: { uri }, region: { startLine: w.line || 1, startColumn: w.column || 1 } } }],
      });
    }
  }
  return JSON.stringify({
    version: "2.1.0",
    $schema: "https://json.schemastore.org/sarif-2.1.0.json",
    runs: [{ tool: { driver: { name: "Stylelint", informationUri: "https://stylelint.io", rules: [...rules.values()] } }, results: out }],
  });
};
