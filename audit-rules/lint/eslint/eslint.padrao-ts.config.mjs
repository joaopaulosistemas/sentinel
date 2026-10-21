// Padrão de código TypeScript/Angular do Sentinel (perfil padrao-typescript-angular).
// Para usar: ESLINT_CONFIG=/opt/eslint/eslint.padrao-ts.config.mjs
// Complexidade, tamanho de método/arquivo e aninhamento NÃO estão aqui: o CleanCodeAnalyzer do Sentinel já mede isso
// com os limites do perfil (50 linhas, complexidade 10, aninhamento 3). Aqui ficam as regras de estrutura, nome e tipos.
import tseslint from "typescript-eslint";
import stylistic from "@stylistic/eslint-plugin";

const padrao = {
  files: ["**/*.{ts,tsx}"],
  ignores: ["**/*.spec.ts", "**/*.test.ts", "**/*.test.tsx", "**/*.mock.ts"],
  plugins: { "@stylistic": stylistic },
  rules: {
    eqeqeq: ["error", "always"],
    "no-debugger": "error",
    "no-var": "error",
    "prefer-const": "error",
    "no-eval": "error",
    "no-implied-eval": "error",
    "no-console": ["error", { allow: ["warn", "error"] }],
    "@typescript-eslint/no-shadow": "error",
    "@typescript-eslint/no-unused-vars": ["error", { argsIgnorePattern: "^_", varsIgnorePattern: "^_" }],

    // Todo método declara o que devolve (void ou tipo), nunca "adivinha".
    "@typescript-eslint/explicit-function-return-type": ["error", { allowExpressions: true }],
    // public/private explícito; o construtor fica sem 'public'.
    "@typescript-eslint/explicit-member-accessibility": [
      "error",
      { accessibility: "explicit", overrides: { constructors: "no-public" } },
    ],
    // Campos, depois construtor, depois métodos.
    "@typescript-eslint/member-ordering": [
      "error",
      { default: ["static-field", "decorated-field", "instance-field", "constructor", "instance-method"] },
    ],
    "@typescript-eslint/naming-convention": [
      "error",
      // privado: _camelCase (inclui parâmetros do construtor: private _router)
      { selector: ["memberLike", "parameterProperty"], modifiers: ["private"], format: ["camelCase", "UPPER_CASE"], leadingUnderscore: "require" },
      { selector: "parameterProperty", modifiers: ["private"], format: ["camelCase"], leadingUnderscore: "require" },
      // não privado: sem underscore
      { selector: ["memberLike", "parameterProperty"], modifiers: ["public"], format: ["camelCase", "UPPER_CASE"], leadingUnderscore: "forbid" },
      // interface: IConta ou ContaInterface
      { selector: "interface", format: ["PascalCase"], custom: { regex: "^I[A-Z]|Interface$", match: true } },
      // enum: TipoPessoaEnum, membros em MAIÚSCULAS_COM_UNDERLINE
      { selector: "enum", format: ["PascalCase"], custom: { regex: "Enum$", match: true } },
      { selector: "enumMember", format: ["UPPER_CASE"] },
    ],

    // Formatação vertical: nenhuma linha em branco dentro de método; uma entre métodos; imports separados da classe.
    "@stylistic/no-multiple-empty-lines": ["error", { max: 1, maxEOF: 0, maxBOF: 0 }],
    "@stylistic/padding-line-between-statements": [
      "error",
      { blankLine: "never", prev: "*", next: "*" },
      { blankLine: "any", prev: ["export", "function", "class", "interface", "type", "enum"], next: "*" },
      { blankLine: "any", prev: "*", next: ["export", "function", "class", "interface", "type", "enum"] },
      { blankLine: "always", prev: "import", next: "*" },
      { blankLine: "any", prev: "import", next: "import" },
    ],
    "@stylistic/lines-between-class-members": ["error", "always", { exceptAfterSingleLine: true }],
    "@stylistic/object-curly-newline": ["error", { consistent: true, multiline: true }],
    "@stylistic/object-property-newline": ["error", { allowAllPropertiesOnSameLine: true }],
  },
};

export default tseslint.config(
  { ignores: ["dist/**", "node_modules/**", "coverage/**"] },
  {
    files: ["**/*.{ts,tsx}"],
    extends: [...tseslint.configs.recommended],
  },
  padrao,
  // Modelos e interfaces são só dados: sem ordem de membros nem modificador de acesso.
  {
    files: ["**/*.model.ts", "**/*.interface.ts"],
    rules: {
      "@typescript-eslint/member-ordering": "off",
      "@typescript-eslint/explicit-member-accessibility": "off",
    },
  },
);
