// Google TypeScript Style Guide + Angular Style Guide + regras de robustez e segurança do Sonar (perfil
// padrao-google-typescript-angular).
// Para usar: ESLINT_CONFIG=/opt/eslint/eslint.google-angular.config.mjs
//
// Parte da configuração padrão do Sentinel (eslint.config.mjs: segurança, promises, regex, Angular e templates) e
// acrescenta por cima:
//   1. Google TypeScript Style Guide (https://google.github.io/styleguide/tsguide.html): nomes, visibilidade, tipos,
//      importações e exportações, controle de fluxo.
//   2. Angular Style Guide (https://angular.dev/style-guide): nome de arquivo, seletores, inject(), outputs readonly.
//   3. Sonar (eslint-plugin-sonarjs): segurança e robustez além das que a configuração padrão já liga.
// Só entram regras que não precisam de informação de tipos (o EslintAnalyzer roda sem tsconfig).
// Complexidade, tamanho de função e aninhamento ficam com o CleanCodeAnalyzer, com os limites do perfil.
//
// Conflitos entre os guias e a decisão (detalhes em docs/padrao-google/README.md):
//   - Nome de arquivo: o Google TS manda snake_case, o Angular manda hífen. Vale o Angular (projeto Angular).
//   - Constantes de módulo: aceita CONSTANT_CASE e lowerCamelCase (o Google permite os dois conforme o caso).
import path from "node:path";
import angular from "angular-eslint";
import sonarjs from "eslint-plugin-sonarjs";
import stylistic from "@stylistic/eslint-plugin";
import base from "./eslint.config.mjs";

// Sem regex aninhada (a forma /^[a-z0-9]+(-[a-z0-9]+)*$/ é apontada como backtracking): confere parte a parte.
const MINUSCULAS_E_DIGITOS = /^[a-z0-9]+$/;
const ehKebab = (nome) => nome.split("-").every((parte) => MINUSCULAS_E_DIGITOS.test(parte));
const NOMES_GENERICOS = new Set(["helpers", "utils", "util", "common", "misc"]);

// Regra local (sem dependência nova): Angular Style Guide, "File naming" e "Project structure".
const sentinelAngular = {
  rules: {
    "nome-de-arquivo": {
      meta: {
        type: "suggestion",
        schema: [],
        messages: {
          hifen: "Nome de arquivo '{{nome}}' deve usar palavras minúsculas separadas por hífen (ex.: user-profile.component.ts), conforme o Angular Style Guide.",
          generico: "Nome de arquivo '{{nome}}' é genérico demais (helpers/utils/common). Nomeie o arquivo pelo identificador principal que ele contém (Angular Style Guide).",
        },
      },
      create(context) {
        return {
          Program(node) {
            const nome = path.basename(context.filename);
            if (nome.endsWith(".d.ts")) {
              return;
            }
            const partes = nome.replace(/\.(ts|tsx)$/, "").split(".");
            if (!partes.every((parte) => ehKebab(parte))) {
              context.report({ node, loc: { line: 1, column: 0 }, messageId: "hifen", data: { nome } });
            } else if (NOMES_GENERICOS.has(partes[0])) {
              context.report({ node, loc: { line: 1, column: 0 }, messageId: "generico", data: { nome } });
            }
          },
        };
      },
    },
  },
};

const comAngular = process.env.SENTINEL_ESLINT_ANGULAR !== "0";
const codigoTs = ["**/*.{ts,tsx}"];
const camelOuConstante = ["camelCase", "UPPER_CASE"];

const googleTypeScript = {
  files: codigoTs,
  ignores: ["**/*.d.ts"],
  plugins: { "@stylistic": stylistic, "sentinel-angular": sentinelAngular },
  rules: {
    // ---- Nomes (Google TS: "Naming") ----
    // lowerCamelCase para variável, parâmetro, função, método e propriedade; UpperCamelCase para tipos;
    // CONSTANT_CASE para constante global e valor de enum. Sem '_' no começo ou no fim (nem em membro privado),
    // sem prefixo 'I' em interface.
    "@typescript-eslint/naming-convention": [
      "error",
      { selector: "default", format: ["camelCase"], leadingUnderscore: "forbid", trailingUnderscore: "forbid" },
      { selector: "variable", format: camelOuConstante, leadingUnderscore: "forbid", trailingUnderscore: "forbid" },
      { selector: "parameter", format: ["camelCase"], leadingUnderscore: "forbid", trailingUnderscore: "forbid" },
      { selector: "classProperty", modifiers: ["static", "readonly"], format: camelOuConstante, leadingUnderscore: "forbid" },
      { selector: "typeLike", format: ["PascalCase"] },
      { selector: "interface", format: ["PascalCase"], custom: { regex: "^I[A-Z]", match: false } },
      { selector: "enumMember", format: ["UPPER_CASE"] },
      // chaves de objeto e de tipo vêm de contratos externos (cabeçalhos HTTP, JSON): sem formato imposto
      { selector: ["objectLiteralProperty", "objectLiteralMethod", "typeProperty", "typeMethod"], format: null },
      // importações: a biblioteca decide o nome
      { selector: "import", format: null },
    ],

    // ---- Visibilidade (Google TS: "Visibility", "Parameter properties") ----
    // Sem a palavra 'public' (exceto em propriedade de parâmetro); 'readonly' no que não é reatribuído.
    "@typescript-eslint/explicit-member-accessibility": ["error", { accessibility: "no-public", overrides: { parameterProperties: "off" } }],
    "@typescript-eslint/parameter-properties": ["error", { prefer: "parameter-property" }],
    "no-restricted-syntax": [
      "error",
      { selector: "ExportDefaultDeclaration", message: "Use export nomeado: export default dificulta a renomeação e a busca (Google TS: Exports)." },
      { selector: "PropertyDefinition[key.type='PrivateIdentifier']", message: "Não use campos #privados; use a palavra private (Google TS: Private fields)." },
      { selector: "MethodDefinition[key.type='PrivateIdentifier']", message: "Não use métodos #privados; use a palavra private (Google TS: Private fields)." },
      { selector: "TSEnumDeclaration[const=true]", message: "Não use const enum; use enum comum (Google TS: Enums)." },
    ],

    // ---- Tipos (Google TS: "Type system") ----
    "@typescript-eslint/no-explicit-any": "error",
    "@typescript-eslint/no-non-null-assertion": "error",
    "@typescript-eslint/consistent-type-assertions": ["error", { assertionStyle: "as", objectLiteralTypeAssertions: "never" }],
    "@typescript-eslint/consistent-type-definitions": ["error", "interface"],
    "@typescript-eslint/array-type": ["error", { default: "array-simple" }],
    "@typescript-eslint/no-wrapper-object-types": "error",
    "@typescript-eslint/no-empty-object-type": "error",
    "@typescript-eslint/no-inferrable-types": "error",
    "@typescript-eslint/no-namespace": "error",
    "@typescript-eslint/no-require-imports": "error",
    "@typescript-eslint/prefer-as-const": "error",
    "@typescript-eslint/no-useless-constructor": "error",
    "@typescript-eslint/no-array-constructor": "error",
    "@typescript-eslint/ban-ts-comment": ["error", { "ts-ignore": true, "ts-expect-error": true, "ts-nocheck": true, "ts-check": false }],

    // ---- Variáveis, igualdade e controle de fluxo (Google TS: "Language features") ----
    "no-var": "error",
    "prefer-const": "error",
    "one-var": ["error", "never"],
    eqeqeq: ["error", "always", { null: "ignore" }],
    curly: ["error", "multi-line"],
    "default-case": "error",
    "default-case-last": "error",
    "no-fallthrough": "error",
    "guard-for-in": "error",
    "no-cond-assign": ["error", "except-parens"],
    "no-new-wrappers": "error",
    "no-object-constructor": "error",
    "no-throw-literal": "error",
    "no-with": "error",
    "no-debugger": "error",
    "no-caller": "error",
    "prefer-rest-params": "error",
    "prefer-template": "warn",
    "no-useless-concat": "warn",
    "max-classes-per-file": ["warn", 1],
    "@stylistic/semi": ["error", "always"],
    "@stylistic/quotes": ["error", "single", { avoidEscape: true, allowTemplateLiterals: "always" }],

    // ---- Angular Style Guide: nome de arquivo ----
    "sentinel-angular/nome-de-arquivo": "warn",
  },
};

const angularStyleGuide = comAngular
  ? [
      {
        files: ["**/*.ts"],
        ignores: ["**/*.spec.ts", "**/*.d.ts"],
        plugins: { "@angular-eslint": angular.tsPlugin },
        rules: {
          "@angular-eslint/prefer-inject": "error",
          "@angular-eslint/prefer-output-readonly": "error",
          "@angular-eslint/component-selector": ["error", { type: "element", style: "kebab-case" }],
          "@angular-eslint/directive-selector": ["error", { type: "attribute", style: "camelCase" }],
          "@angular-eslint/component-class-suffix": "error",
          "@angular-eslint/directive-class-suffix": "error",
          "@angular-eslint/use-lifecycle-interface": "error",
          "@angular-eslint/no-empty-lifecycle-method": "error",
          "@angular-eslint/prefer-signals": "warn",
        },
      },
    ]
  : [];

// Sonar: segurança e robustez além do que a configuração padrão já liga (sonarjs/cognitive-complexity e afins).
// Entram em modo sombra no perfil até a precisão ser medida (docs/evolucao/precisao.md).
const sonarRobustezESeguranca = {
  files: ["**/*.{ts,tsx,js,jsx,mjs}"],
  plugins: { sonarjs },
  rules: {
    "sonarjs/no-hardcoded-passwords": "error",
    "sonarjs/no-hardcoded-secrets": "warn",
    "sonarjs/no-hardcoded-ip": "warn",
    "sonarjs/no-clear-text-protocols": "error",
    "sonarjs/no-os-command-from-path": "error",
    "sonarjs/os-command": "error",
    "sonarjs/pseudo-random": "warn",
    "sonarjs/weak-ssl": "error",
    "sonarjs/hashing": "warn",
    "sonarjs/encryption-secure-mode": "error",
    "sonarjs/insecure-cookie": "error",
    "sonarjs/insecure-jwt-token": "error",
    "sonarjs/no-unsafe-unzip": "error",
    "sonarjs/unverified-certificate": "error",
    "sonarjs/unverified-hostname": "error",
    "sonarjs/disabled-auto-escaping": "error",
    "sonarjs/post-message": "warn",
    "sonarjs/no-ignored-return": "error",
    "sonarjs/no-ignored-exceptions": "warn",
    "sonarjs/no-try-promise": "error",
    "sonarjs/no-misleading-array-reverse": "warn",
    "sonarjs/no-array-delete": "error",
    "sonarjs/no-unthrown-error": "error",
    "sonarjs/no-in-misuse": "error",
    "sonarjs/no-invariant-returns": "warn",
    "sonarjs/no-useless-catch": "warn",
    "sonarjs/no-nested-switch": "warn",
  },
};

export default [
  ...base,
  googleTypeScript,
  ...angularStyleGuide,
  sonarRobustezESeguranca,
  // Arquivos de configuração do projeto (vite, eslint, karma...) exigem export default; nome fixo por ferramenta.
  {
    files: ["**/*.config.{ts,js,mjs,cjs}", "**/*.conf.{ts,js}"],
    rules: { "no-restricted-syntax": "off", "sentinel-angular/nome-de-arquivo": "off" },
  },
  // Ordem final: a lista de ignorados da configuração padrão continua valendo (spec, test, stories).
  { ignores: ["**/*.spec.ts", "**/*.test.{ts,tsx,js,jsx}", "**/*.stories.*"] },
];
