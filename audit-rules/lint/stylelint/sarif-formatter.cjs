// Formatador SARIF para o Stylelint (usado pelo pacote market-tools). Recebe os resultados e devolve o SARIF 2.1.0.
"use strict";
const path = require("path");

module.exports = function sarifFormatter(results) {
  const rules = new Map();
  const out = [];
  for (const file of results) {
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
