// Roda o jscpd na pasta atual (o projeto auditado) e escreve o SARIF 2.1.0 na saída padrão.
// Detecta trechos copiados em Java, TypeScript, JavaScript, TSX/JSX, CSS/SCSS e HTML.
import { execFileSync } from "node:child_process";
import { mkdtempSync, readFileSync, rmSync, existsSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const aqui = dirname(fileURLToPath(import.meta.url));
const bin = join(aqui, "node_modules", ".bin", "jscpd");
const minLinhas = process.env.JSCPD_MIN_LINES || "10";
const minTokens = process.env.JSCPD_MIN_TOKENS || "100";
const ignorar = [
  "**/node_modules/**", "**/dist/**", "**/target/**", "**/build/**", "**/coverage/**", "**/.git/**", "**/vendor/**",
  "**/*.min.*", "**/*.d.ts", "**/*.spec.ts", "**/*.test.*", "**/test/**", "**/tests/**", "**/__tests__/**",
  "**/generated/**", "**/*-lock.json", "**/package-lock.json",
].join(",");

const tmp = mkdtempSync(join(tmpdir(), "jscpd-"));
let relatorio = [];
try {
  try {
    execFileSync(bin, [
      ".", "--reporters", "json", "--output", tmp, "--silent",
      "--min-lines", minLinhas, "--min-tokens", minTokens, "--max-size", "300kb",
      "--format", "java,typescript,javascript,tsx,jsx,css,scss,markup",
      "--ignore", ignorar,
    ], { stdio: ["ignore", "ignore", "ignore"], maxBuffer: 64 * 1024 * 1024 });
  } catch {
    // o jscpd sai com código diferente de zero em alguns casos; o relatório é o que importa
  }
  const arquivo = join(tmp, "jscpd-report.json");
  if (existsSync(arquivo)) {
    relatorio = JSON.parse(readFileSync(arquivo, "utf8")).duplicates || [];
  }
} finally {
  rmSync(tmp, { recursive: true, force: true });
}

const loc = (f) => ({
  physicalLocation: {
    artifactLocation: { uri: f.name.split("\\").join("/") },
    region: { startLine: f.startLoc?.line ?? f.start ?? 1, endLine: f.endLoc?.line ?? f.end ?? 1 },
  },
});

const resultados = relatorio.slice(0, 300).map((d) => ({
  ruleId: "codigo-duplicado",
  level: d.lines >= 40 ? "error" : "warning",
  message: {
    text: `Trecho de ${d.lines} linhas duplicado em ${d.secondFile.name}:${d.secondFile.startLoc?.line ?? d.secondFile.start}. ` +
      "Extraia para um método, componente ou mixin compartilhado.",
  },
  locations: [loc(d.firstFile)],
  relatedLocations: [loc(d.secondFile)],
}));

process.stdout.write(JSON.stringify({
  version: "2.1.0",
  $schema: "https://json.schemastore.org/sarif-2.1.0.json",
  runs: [{
    tool: { driver: { name: "jscpd", informationUri: "https://github.com/kucherenko/jscpd", rules: [{ id: "codigo-duplicado", shortDescription: { text: "Código duplicado" } }] } },
    results: resultados,
  }],
}));
