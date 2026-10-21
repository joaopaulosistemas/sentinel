// Regras do Dependency-Cruiser usadas pelo Sentinel: só o que não depende de node_modules instalado.
module.exports = {
  forbidden: [
    {
      name: "ciclo-de-dependencia",
      severity: "warn",
      comment: "Módulos que dependem uns dos outros em círculo dificultam testes e mudanças.",
      from: {},
      to: { circular: true },
    },
    {
      name: "modulo-orfao",
      severity: "info",
      comment: "Arquivo que ninguém importa (e que não importa ninguém).",
      from: {
        orphan: true,
        pathNot: [
          "(^|/)[.][^/]+[.](?:js|cjs|mjs|ts|json)$",
          "[.]d[.]ts$",
          "(^|/)tsconfig[^/]*[.]json$",
          "(^|/)(main|index|polyfills|setup-jest|test|environment[^/]*|app[.]config|app[.]routes)[.]ts$",
          "[.](spec|test|stories|config|mock)[.]",
          "(^|/)(e2e|cypress|mocks?|__mocks__|assets)/",
        ],
      },
      to: {},
    },
    {
      name: "producao-importa-teste",
      severity: "warn",
      comment: "Código de produção não deve importar arquivos de teste.",
      from: { pathNot: "[.](spec|test)[.]|(^|/)(e2e|__tests__|test|tests)/" },
      to: { path: "[.](spec|test)[.](?:js|ts|tsx|jsx)$" },
    },
  ],
  options: {
    doNotFollow: { path: "node_modules" },
    exclude: { path: "(^|/)(node_modules|dist|build|coverage|target|\\.angular)/" },
    tsPreCompilationDeps: true,
  },
};
