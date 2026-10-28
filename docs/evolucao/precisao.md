# Precisão medida por revisão manual

Revisão por amostragem feita pelo agente Sentinel Descoberta (ler o código ao redor de cada achado). Não é auditoria exaustiva: as amostras são pequenas e as taxas são indicativas.
VP = verdadeiro positivo (inclui os de pouco valor, como estilo), FP = falso positivo. Atualizar a tabela a cada rodada, somando aos números anteriores.
Para o eixo 10 do placar chegar a 3 são necessários ≥ 100 achados revisados em ≥ 10 repositórios; hoje há 331 achados em 19 repositórios (10 em 4 na rodada de 2026-10-21, 81 em 5 na primeira de 2026-10-22, NestJS, 140 em 5 na segunda de 2026-10-22, Java/Spring, e 100 em 5 em 2026-10-28, React; as linhas de `njsscan-ejs_ect_template`, `eslint-react/jsx-no-undef` e `stylelint-lightness-notation` dessa última entram pelo PR #71). Nenhum dos 19 é um segundo caso de Java/Spring ou Angular em ambiente corporativo; o critério de 1/3 de Java/Spring ou Angular do eixo 10 segue sem cobertura nesta rodada. Ainda não há veredito humano preenchido em `revisao-humana.md` (0 de 40 linhas): a concordância não foi calculada.

| Regra | Revisados | VP | FP | Duvidosos | Última rodada |
|---|---:|---:|---:|---:|---|
| `eslint-@angular-eslint/prefer-inject` | 1 | 0 | 1 | 0 | 2026-10-21 (corrigida: só vale em projeto com `@angular/*`) |
| `sentinel-ts-any-explicito` | 18 | 14 | 0 | 4 | 2026-10-28 (D: `any[]` em restrição genérica de decorator; `JSXElementConstructor<any>` e `React.FC<any>`, tipos de componente de biblioteca) |
| `sqlfluff-RF06` | 1 | 0 | 1 | 0 | 2026-10-21 (migração gerada pelo Prisma) |
| `squawk-require-concurrent-index-creation` | 1 | 0 | 1 | 0 | 2026-10-21 (tabela nova na mesma migração) |
| `eslint-prefer-const` | 1 | 1 | 0 | 0 | 2026-10-21 |
| `njsscan-ejs_ect_template` | 7 | 0 | 7 | 0 | 2026-10-28 (modelos EJS que geram código TypeScript, sem HTML; em sombra nos perfis, PR desta rodada) |
| `eslint-react/jsx-no-undef` | 6 | 0 | 6 | 0 | 2026-10-28 (corrigida: desligada; componentes vêm de `unplugin-auto-import`, e o tsc pega o erro real) |
| `stylelint-lightness-notation` | 6 | 6 | 0 | 0 | 2026-10-28 (corrigida: desligada; VP só de estilo, `oklch(0.5 …)` gerado pelo shadcn, 32% dos achados de um repositório) |
| `sentinel-ts-supressao-de-tipos` | 1 | 0 | 1 | 0 | 2026-10-21 (corrigida: ignora `**/generated/**`) |
| `sentinel-int-http-sem-tls` | 2 | 2 | 0 | 0 | 2026-10-21 |
| `gitleaks-generic-api-key` | 3 | 0 | 2 | 1 | 2026-10-22 (FP: selo de README com `token=` de placeholder) |
| `eslint-regexp/no-super-linear-move` | 6 | 0 | 6 | 0 | 2026-10-22 (scripts de instalação sem entrada externa; pendente) |
| `eslint-@typescript-eslint/no-explicit-any` | 6 | 4 | 0 | 2 | 2026-10-22 (D: `T = any` padrão de genérico) |
| `eslint-@typescript-eslint/no-unused-vars` | 8 | 5 | 2 | 1 | 2026-10-22 (FP: omissão por rest, parâmetro de decorator) |
| `sentinel-cc-funcao-longa` | 20 | 13 | 5 | 2 | 2026-10-22 Java (FP: 3 em jQuery embutido, 1 em `ejs.js:137` com 886 linhas que a leitura não confirma; D: classe anônima contada como função) |
| `sentinel-cc-complexidade` | 24 | 17 | 5 | 2 | 2026-10-28 React (VP: `getFileFormat` 12 e `generateRoutes` 13, recontados à mão; D: componentes React como `MenuAuthorize` (45) e `ApiTreeSelect` (11), em que a soma inclui todos os closures de hooks, e cada closure sozinho é bem menor; 2026-10-22 Java: FP em jQuery embutido, `ejs.js:137` com 175) |
| `sentinel-ts-console-log` | 6 | 4 | 2 | 0 | 2026-10-22 (FP: saída de CLI) |
| `sentinel-cc-aninhamento` | 5 | 3 | 1 | 1 | 2026-10-22 (FP: objeto literal conta como nível; pendente) |
| `eslint-sonarjs/cognitive-complexity` | 6 | 6 | 0 | 0 | 2026-10-22 (só a métrica foi conferida) |
| `eslint-security/detect-non-literal-fs-filename` | 6 | 0 | 6 | 0 | 2026-10-22 (CLI/scripts, sem entrada externa; pendente) |
| `gitleaks-jwt` | 5 | 0 | 0 | 5 | 2026-10-22 (dump SQL de demonstração, tokens vencidos) |
| `sentinel-proj-committed-env` | 10 | 4 | 6 | 0 | 2026-10-28 (corrigida no PR #72: só `.env` com chave de nome sensível ou URL com senha; 6 de 7 eram só configuração pública de Vite, 1 era chave de criptografia fixa) |
| `sentinel-proj-no-tests` | 2 | 2 | 0 | 0 | 2026-10-22 |
| `owasp-dependency-CVE-2022-0235-node-fetch` | 1 | 0 | 1 | 0 | 2026-10-22 (corrigida: maior correção do mesmo major) |
| `hadolint-DL3044` | 1 | 1 | 0 | 0 | 2026-10-22 |
| `sentinel-sql-injection` | 6 | 0 | 6 | 0 | 2026-10-22 Java (`update`/`execute`/`query` em qualquer objeto; corrigida no PR #14) |
| `sentinel-senha-fixa-no-codigo` | 11 | 3 | 8 | 0 | 2026-10-22 Java (nome de campo, cabeçalho e prefixo como valor; corrigida no PR #15) |
| `sentinel-xxe-parser-xml` | 3 | 1 | 1 | 1 | 2026-10-22 Java (FP: `TransformerFactory` só serializa DOM; D: só cria documento vazio) |
| `checkstyle-magic.number` | 13 | 5 | 4 | 4 | 2026-10-22 Java (FP: layout Swing gerado, máscaras hexadecimais; D: código HTTP em `switch`, capacidade inicial) |
| `jscpd-codigo-duplicado` | 12 | 1 | 8 | 3 | 2026-10-22 Java (FP: cabeçalho de licença, `pom.xml`, `README.html` gerado, `nbactions.xml`) |
| `sentinel-ts-var` | 6 | 0 | 6 | 0 | 2026-10-22 Java (6 de 6 em biblioteca embutida: `ejs.js`, `bootstrap.js`, jQuery; corrigida no PR #16) |
| `sentinel-ts-igualdade-frouxa` | 14 | 8 | 6 | 0 | 2026-10-28 React (8 de 8 `==` reais em código próprio, de estilo; 2026-10-22 Java: 6 FP em `base.js`, `ejs.js`, `bootstrap.js`; corrigida no PR #16, que tirou bibliotecas embutidas da auditoria) |
| `stylelint-a11y/content-property-no-static-value` | 3 | 0 | 3 | 0 | 2026-10-22 Java (CSS de fonte de ícones embutido; todos os 936 achados estão em 1 repositório; menos de 5 lidos, de um só arquivo) |
| `checkstyle-redundantModifier` | 9 | 8 | 0 | 1 | 2026-10-22 Java (VP de estilo) |
| `checkstyle-hide.utility.class` | 14 | 7 | 3 | 4 | 2026-10-22 Java (FP: `@SpringBootApplication` e classe com construtor privado do Lombok; D: classes com `main`; nos VP só conferi o cabeçalho da classe) |
| `checkstyle-variable.notPrivate` | 6 | 6 | 0 | 0 | 2026-10-22 Java (VP de estilo) |
| `pmd-ConstructorCallsOverridableMethod` | 6 | 6 | 0 | 0 | 2026-10-22 Java (VP, mas severidade ALTA exagerada; pendente) |
| `pmd-ClassWithOnlyPrivateConstructorsShouldBeFinal` | 12 | 12 | 0 | 0 | 2026-10-22 Java (VP de estilo com severidade ALTA; pendente) |
| `pmd-AvoidThrowingRawExceptionTypes` | 9 | 9 | 0 | 0 | 2026-10-22 Java (VP de estilo com severidade ALTA; pendente) |
| `sentinel-dup-bloco` | 8 | 3 | 3 | 2 | 2026-10-28 React (VP: cópia de `alert-dialog.tsx` do shadcn, `userTree`/`menuTree`, cabeçalho de tabela repetido; FP: bloco que é só o cabeçalho de `import` (`CustomerSatisfaction.tsx:1`, `keep-alive-slot.tsx:1`) e boilerplate de ícone SVG; D: formulário de login, lista de `import` de `slice.ts:5`; pendente) |
| `sentinel-morto-export-interno` | 6 | 6 | 0 | 0 | 2026-10-28 React (conferi por busca do nome: só o próprio arquivo usa; INFO) |
| `sentinel-morto-export` | 9 | 9 | 0 | 0 | 2026-10-28 React (5 LOW sem nenhum uso e 4 INFO usados só no próprio arquivo; busca textual pelo nome, não resolve uso dinâmico nem exportação de biblioteca para terceiros) |
| `stylelint-declaration-no-important` | 7 | 7 | 0 | 0 | 2026-10-28 React (`!important` literal; em `plugins/`, `docs/` e CSS de Storybook entra a mesma regra; VP de estilo) |
| `sentinel-mem-addeventlistener-sem-remove` | 6 | 0 | 6 | 0 | 2026-10-28 React (corrigida no PR #70: só vale em classe; os 5 hooks tinham `removeEventListener` no cleanup e o sexto era `FileReader` local) |
| `sentinel-mem-setinterval-sem-clear` | 1 | 0 | 1 | 0 | 2026-10-28 React (corrigida no PR #70: `clearInterval` no cleanup do `useEffect`) |
| `sentinel-proj-no-lockfile` | 12 | 5 | 7 | 0 | 2026-10-28 React (corrigida no PR #72: FP = 7 pacotes sob `frontend/`, que tem `pnpm-lock.yaml`; VP = 5 em `south-admin-react`, sem lockfile em nenhum lugar) |
| `sentinel-react-dangerously` | 4 | 0 | 2 | 2 | 2026-10-28 React (FP: `<style>` com constante estática do shadcn e do `tree-eazy`; D: HTML de `shiki` e de `formatMarkdown` sem conferir se escapam o texto de origem; pendente) |
| `sentinel-xss-inner-html` | 1 | 0 | 0 | 1 | 2026-10-28 React (D: `useWatermark.ts:46`, não segui a origem do texto) |

Taxa de VP nas regras de severidade média ou alta com ao menos 5 revisados (critério do eixo 2): `no-explicit-any` 4/6, `no-unused-vars` 5/8, `funcao-longa` 6/8 (MEDIUM/LOW), `complexidade` 7/8, `any-explicito` 8/10, `gitleaks-jwt` 0/5 (duvidosos), `no-super-linear-move` e `detect-non-literal-fs-filename` 0/6 (LOW). Várias regras ficam abaixo de 80%; o eixo 2 continua em 2.

Segunda rodada de 2026-10-22 (Java/Spring, 140 achados lidos): `sql-injection` 0/6, `senha-fixa-no-codigo` 3/11, `ts-var` 0/6 e `igualdade-frouxa` 0/6 (o motivo é código de terceiros embutido, não a regra), `xxe-parser-xml` 1/3, `magic.number` 5/13, `jscpd` 1/12 (as demais regras de estilo acima ficam em 100% de VP, de pouco valor). Regras `pmd-ReturnEmptyCollectionRatherThanNull` (6) e `pmd-SingletonClassReturningNewInstance` (3) foram vistas, mas não classificadas (não conferi o tipo de retorno nem o padrão de uso).

Rodada de 2026-10-28 (5 aplicações React, 100 achados lidos; sem Java/Spring nem Angular): taxa de VP nas regras de severidade média ou alta com ao menos 5 lidos nesta rodada: `any-explicito` 6/8 (2 duvidosos), `igualdade-frouxa` 8/8 (estilo), `mem-addeventlistener-sem-remove` 0/6, `jsx-no-undef` 0/6, `njsscan-ejs_ect_template` 0/7, `proj-committed-env` 1/7, `proj-no-lockfile` 5/12, `declaration-no-important` 7/7 (estilo), `dup-bloco` 3/6 (LOW, sem contar 2 duvidosos). `sentinel-cc-funcao-longa` foi vista (11 amostras) mas só conferi a métrica, não a contagem de linhas nem se o JSX infla o número; fica sem linha de contagem. As correções saíram nos PRs #70, #71 e #72.
