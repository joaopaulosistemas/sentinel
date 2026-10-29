// Conversor para SARIF 2.1.0 (na saída padrão) das ferramentas de SQL e de arquitetura do pacote market-tools.
// Uso (a pasta atual é o projeto auditado):
//   node sarif-extras.mjs squawk   <arquivos.sql...>   migrations do Postgres (risco de lock, tipos, índices)
//   node sarif-extras.mjs sqlfluff <arquivos.sql...>   estilo e boas práticas de SQL
//   node sarif-extras.mjs depcruise                    ciclos e módulos órfãos em TS/JS
import { execFileSync } from "node:child_process";
import { existsSync, statSync } from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const aqui = dirname(fileURLToPath(import.meta.url));
const [, , modo, ...arquivos] = process.argv;
const BUF = 128 * 1024 * 1024;
const posix = (p) => p.split("\\").join("/").replace(/^\.\//, "");

function rodar(cmd, args) {
  try {
    return execFileSync(cmd, args, { encoding: "utf8", maxBuffer: BUF, stdio: ["ignore", "pipe", "ignore"] });
  } catch (e) {
    // as ferramentas saem com código != 0 quando acham problemas; o que importa é a saída
    return e.stdout ? e.stdout.toString() : "";
  }
}

const regras = new Map();
const resultados = [];
function achado({ ruleId, nivel, texto, ajuda, arquivo, linha, coluna, descricao }) {
  if (!regras.has(ruleId)) {
    regras.set(ruleId, { id: ruleId, shortDescription: { text: descricao || ruleId }, ...(ajuda ? { help: { text: ajuda } } : {}) });
  }
  resultados.push({
    ruleId,
    level: nivel,
    message: { text: ajuda && !texto.includes(ajuda) ? `${texto} ${ajuda}` : texto },
    locations: [{
      physicalLocation: {
        artifactLocation: { uri: posix(arquivo) },
        region: { startLine: Math.max(1, linha || 1), startColumn: Math.max(1, coluna || 1) },
      },
    }],
  });
}

let ferramenta = modo;
let url = "";

if (modo === "squawk") {
  ferramenta = "Squawk";
  url = "https://squawkhq.com";
  // regras exigidas só por política de ambiente (timeouts, idempotência) geram ruído em migrations de Flyway/Liquibase
  const excluir = process.env.SQUAWK_EXCLUDE || "prefer-robust-stmts,require-lock-timeout,require-statement-timeout";
  const bin = join(aqui, "node_modules", ".bin", "squawk");
  const lista = arquivos.filter((a) => a.toLowerCase().endsWith(".sql"));
  for (let i = 0; i < lista.length; i += 100) {
    const saida = rodar(bin, ["--reporter", "json", "--exclude", excluir, ...lista.slice(i, i + 100)]);
    let itens = [];
    try { itens = JSON.parse(saida); } catch { itens = []; }
    for (const v of itens) {
      achado({ ruleId: v.rule_name, nivel: /error/i.test(v.level) ? "error" : "warning", texto: v.message, ajuda: v.help || "", arquivo: v.file, linha: v.line, coluna: v.column, descricao: `Squawk: ${v.rule_name}` });
    }
  }
} else if (modo === "sqlfluff") {
  ferramenta = "SQLFluff";
  url = "https://sqlfluff.com";
  const dialeto = process.env.SQLFLUFF_DIALECT || "postgres";
  const lista = arquivos.filter((a) => a.toLowerCase().endsWith(".sql"));
  for (let i = 0; i < lista.length; i += 100) {
    const saida = rodar("sqlfluff", ["lint", "--format", "json", "--dialect", dialeto, "--config", join(aqui, "sqlfluff.cfg"), "--nocolor", ...lista.slice(i, i + 100)]);
    let itens = [];
    try { itens = JSON.parse(saida); } catch { itens = []; }
    for (const f of itens) {
      const vistos = new Set();
      for (const v of f.violations || []) {
        const chave = `${v.code}|${v.start_line_no}`;
        if (vistos.has(chave)) continue;   // a mesma regra costuma repetir várias vezes na mesma linha
        vistos.add(chave);
        achado({ ruleId: v.code, nivel: "note", texto: v.description, ajuda: "", arquivo: f.filepath, linha: v.start_line_no, coluna: v.start_line_pos, descricao: `SQLFluff ${v.code}: ${v.name || ""}`.trim() });
      }
    }
  }
} else if (modo === "depcruise") {
  ferramenta = "Dependency-Cruiser";
  url = "https://github.com/sverweij/dependency-cruiser";
  const bin = join(aqui, "node_modules", ".bin", "depcruise");
  const pastas = ["src", "app", "projects", "libs", "packages"].filter((p) => existsSync(p) && statSync(p).isDirectory());
  const args = ["--config", join(aqui, "dependency-cruiser.cjs"), "--output-type", "json"];
  if (existsSync("tsconfig.json")) args.push("--ts-config", "tsconfig.json");
  args.push(...(pastas.length ? pastas : ["."]));
  let rel = {};
  try { rel = JSON.parse(rodar(bin, args)); } catch { rel = {}; }
  const vistos = new Set();
  for (const v of rel.summary?.violations || []) {
    const nome = v.rule?.name || "regra";
    const nivel = v.rule?.severity === "error" ? "error" : v.rule?.severity === "warn" ? "warning" : "note";
    let texto;
    if (v.type === "cycle" || v.cycle) {
      const caminho = [v.from, ...(v.cycle || []).map((c) => c.name)].join(" → ");
      texto = `Dependência circular: ${caminho}. Extraia o que é compartilhado para um terceiro módulo ou inverta a dependência.`;
    } else if (nome === "modulo-orfao") {
      texto = "Arquivo órfão: nenhum outro módulo o importa e ele não importa nada. Remova se estiver sem uso.";
    } else {
      texto = `${v.from} importa ${v.to}, o que a regra "${nome}" não permite.`;
    }
    const chave = `${nome}|${v.from}|${v.to}`;
    if (vistos.has(chave)) continue;
    vistos.add(chave);
    achado({ ruleId: nome, nivel, texto, ajuda: "", arquivo: v.from, linha: 1, coluna: 1, descricao: `Dependency-Cruiser: ${nome}` });
  }
} else {
  process.stderr.write("modo desconhecido: " + modo + "\n");
  process.exit(2);
}

process.stdout.write(JSON.stringify({
  version: "2.1.0",
  $schema: "https://json.schemastore.org/sarif-2.1.0.json",
  runs: [{
    tool: { driver: { name: ferramenta, informationUri: url, rules: [...regras.values()] } },
    results: resultados.slice(0, 1000),
  }],
}));
