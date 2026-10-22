// Configuração usada pelo EslintAnalyzer (rodando com --no-config-lookup: a do repositório auditado nunca é carregada).
// Stack coberta: TypeScript, JavaScript, Angular (código e templates), React, e as armadilhas de segurança de JS.
import tseslint from "typescript-eslint";
import angular from "angular-eslint";
import react from "eslint-plugin-react";
import reactHooks from "eslint-plugin-react-hooks";
import jsxA11y from "eslint-plugin-jsx-a11y";
import security from "eslint-plugin-security";
import sonarjs from "eslint-plugin-sonarjs";
import noUnsanitized from "eslint-plugin-no-unsanitized";
import promise from "eslint-plugin-promise";
import regexp from "eslint-plugin-regexp";
import html from "@html-eslint/eslint-plugin";

const codigo = ["**/*.{ts,tsx,js,jsx,mjs}"];

export default tseslint.config(
  {
    files: codigo,
    extends: [...tseslint.configs.recommended],
    plugins: { security, sonarjs, "no-unsanitized": noUnsanitized, promise, regexp },
    rules: {
      eqeqeq: "error",
      "no-var": "error",
      "no-console": "warn",
      "prefer-const": "error",
      "no-eval": "error",
      "no-implied-eval": "error",
      "no-new-func": "error",
      "no-script-url": "error",
      // segurança em JavaScript
      "security/detect-eval-with-expression": "error",
      "security/detect-unsafe-regex": "warn",
      "security/detect-non-literal-regexp": "warn",
      "security/detect-child-process": "warn",
      // desligadas pela medição (precisao.md, 2026-10-22): 0 verdadeiros positivos em 6 achados lidos de cada uma;
      // a regra de fs aponta todo path.join de CLI e script, e a "super-linear-move" 112 vezes em scripts de instalação
      "security/detect-non-literal-fs-filename": "off",
      "security/detect-pseudoRandomBytes": "error",
      "security/detect-buffer-noassert": "error",
      "security/detect-new-buffer": "error",
      "security/detect-possible-timing-attacks": "warn",
      "no-unsanitized/method": "error",
      "no-unsanitized/property": "error",
      // bugs e complexidade cognitiva
      "sonarjs/cognitive-complexity": ["warn", 15],
      "sonarjs/no-identical-functions": "warn",
      "sonarjs/no-duplicated-branches": "warn",
      "sonarjs/no-collapsible-if": "warn",
      "sonarjs/no-identical-expressions": "error",
      "sonarjs/no-all-duplicated-branches": "error",
      "sonarjs/no-element-overwrite": "error",
      "sonarjs/no-use-of-empty-return-value": "error",
      "sonarjs/no-gratuitous-expressions": "warn",
      "sonarjs/no-redundant-boolean": "warn",
      "sonarjs/no-inverted-boolean-check": "warn",
      // promises
      "promise/catch-or-return": ["warn", { allowFinally: true }],
      "promise/no-return-wrap": "warn",
      "promise/no-nesting": "warn",
      "promise/no-promise-in-callback": "warn",
      "promise/no-callback-in-promise": "warn",
      "promise/param-names": "warn",
      "promise/valid-params": "error",
      "promise/no-new-statics": "error",
      "promise/no-return-in-finally": "warn",
      // expressões regulares: ReDoS e erros
      "regexp/no-super-linear-backtracking": "error",
      "regexp/no-super-linear-move": "off",
      "regexp/no-trivially-nested-quantifier": "warn",
      "regexp/no-misleading-capturing-group": "warn",
      "regexp/no-empty-alternative": "warn",
      "regexp/no-dupe-characters-character-class": "warn",
      "regexp/no-contradiction-with-assertion": "warn",
      "regexp/no-useless-assertions": "warn",
      "regexp/no-empty-group": "warn",
      "regexp/no-misleading-unicode-character": "warn",
    },
  },
  // React
  {
    files: ["**/*.{tsx,jsx}"],
    plugins: { react, "react-hooks": reactHooks, "jsx-a11y": jsxA11y },
    settings: { react: { version: "detect" } },
    rules: {
      ...react.configs.recommended.rules,
      "react/react-in-jsx-scope": "off",
      "react/prop-types": "off",
      "react/jsx-key": "error",
      "react/no-danger": "warn",
      "react/jsx-no-target-blank": "error",
      "react/jsx-no-script-url": "error",
      "react/no-array-index-key": "warn",
      "react/no-unstable-nested-components": "warn",
      "react-hooks/rules-of-hooks": "error",
      "react-hooks/exhaustive-deps": "warn",
      ...jsxA11y.flatConfigs.recommended.rules,
    },
    languageOptions: jsxA11y.flatConfigs.recommended.languageOptions,
  },
  // Angular: código (componentes, diretivas, serviços). O EslintAnalyzer define SENTINEL_ESLINT_ANGULAR=0 quando o
  // projeto não depende de @angular/*; sem isso, NestJS/Express receberiam regras como prefer-inject (injeção por
  // construtor é o padrão do Nest) e a nota seria distorcida (calibração: 112 achados em 3 repositórios sem Angular).
  ...(process.env.SENTINEL_ESLINT_ANGULAR === "0"
    ? []
    : [
        {
          files: ["**/*.ts"],
          ignores: ["**/*.spec.ts"],
          extends: [...angular.configs.tsRecommended],
          processor: angular.processInlineTemplates,
        },
      ]),
  // HTML puro (páginas, index.html): acessibilidade e boas práticas. Templates Angular usam a regra acima.
  {
    files: ["**/*.html"],
    ignores: ["**/*.component.html"],
    plugins: { "@html-eslint": html },
    language: "@html-eslint/html",
    rules: {
      "@html-eslint/require-lang": "warn",
      "@html-eslint/require-title": "warn",
      "@html-eslint/require-img-alt": "warn",
      "@html-eslint/require-button-type": "warn",
      "@html-eslint/require-input-label": "warn",
      "@html-eslint/require-frame-title": "warn",
      "@html-eslint/require-meta-viewport": "warn",
      "@html-eslint/require-meta-charset": "warn",
      "@html-eslint/require-closing-tags": "warn",
      "@html-eslint/no-duplicate-id": "error",
      "@html-eslint/no-duplicate-attrs": "error",
      "@html-eslint/no-positive-tabindex": "warn",
      "@html-eslint/no-target-blank": "error",
      "@html-eslint/no-non-scalable-viewport": "warn",
      "@html-eslint/no-aria-hidden-body": "error",
      "@html-eslint/no-aria-hidden-on-focusable": "error",
      "@html-eslint/no-invalid-role": "error",
      "@html-eslint/no-empty-headings": "warn",
      "@html-eslint/no-multiple-h1": "warn",
      "@html-eslint/no-skip-heading-levels": "warn",
      "@html-eslint/no-nested-interactive": "warn",
      "@html-eslint/no-obsolete-tags": "warn",
      "@html-eslint/no-script-style-type": "warn",
      "@html-eslint/no-inline-styles": "warn",
      "@html-eslint/no-accesskey-attrs": "warn",
      "@html-eslint/prefer-https": "warn",
    },
  },
  // Templates de motor do servidor (Thymeleaf, Freemarker, JSP em pastas templates/fragments/views) são trechos
  // de página, montados por um layout: não têm <html lang>, <title>, <meta> nem fecham todas as tags sozinhos.
  // Calibração no spring-petclinic: 33 + 12 + 8 achados desse tipo.
  {
    files: ["**/templates/**/*.html", "**/fragments/**/*.html", "**/views/**/*.html"],
    rules: {
      "@html-eslint/require-lang": "off",
      "@html-eslint/require-title": "off",
      "@html-eslint/require-meta-viewport": "off",
      "@html-eslint/require-meta-charset": "off",
      "@html-eslint/require-closing-tags": "off",
      "@html-eslint/no-multiple-h1": "off",
      "@html-eslint/no-skip-heading-levels": "off",
    },
  },
  // Angular: templates, com acessibilidade
  {
    files: ["**/*.component.html"],
    extends: [...angular.configs.templateRecommended, ...angular.configs.templateAccessibility],
  },
  { ignores: ["**/*.spec.ts", "**/*.test.{ts,tsx,js,jsx}", "**/*.stories.*"] },
);
