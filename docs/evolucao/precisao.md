# Precisão medida por revisão manual

Revisão por amostragem feita pelo agente Sentinel Descoberta (ler o código ao redor de cada achado). Não é auditoria exaustiva: as amostras são pequenas e as taxas são indicativas.
VP = verdadeiro positivo (inclui os de pouco valor, como estilo), FP = falso positivo. Atualizar a tabela a cada rodada, somando aos números anteriores.
Para o eixo 10 do placar chegar a 3 são necessários ≥ 100 achados revisados em ≥ 10 repositórios; hoje há 231 achados em 14 repositórios (10 em 4 na rodada de 2026-10-21, 81 em 5 na primeira de 2026-10-22, NestJS, e 140 em 5 na segunda de 2026-10-22, Java/Spring). Ainda não há veredito humano preenchido em `revisao-humana.md`: a concordância não foi calculada.

| Regra | Revisados | VP | FP | Duvidosos | Última rodada |
|---|---:|---:|---:|---:|---|
| `eslint-@angular-eslint/prefer-inject` | 1 | 0 | 1 | 0 | 2026-10-21 (corrigida: só vale em projeto com `@angular/*`) |
| `sentinel-ts-any-explicito` | 10 | 8 | 0 | 2 | 2026-10-22 (D: `any[]` em restrição genérica de decorator) |
| `sqlfluff-RF06` | 1 | 0 | 1 | 0 | 2026-10-21 (migração gerada pelo Prisma) |
| `squawk-require-concurrent-index-creation` | 1 | 0 | 1 | 0 | 2026-10-21 (tabela nova na mesma migração) |
| `eslint-prefer-const` | 1 | 1 | 0 | 0 | 2026-10-21 |
| `sentinel-ts-supressao-de-tipos` | 1 | 0 | 1 | 0 | 2026-10-21 (corrigida: ignora `**/generated/**`) |
| `sentinel-int-http-sem-tls` | 2 | 2 | 0 | 0 | 2026-10-21 |
| `gitleaks-generic-api-key` | 3 | 0 | 2 | 1 | 2026-10-22 (FP: selo de README com `token=` de placeholder) |
| `eslint-regexp/no-super-linear-move` | 6 | 0 | 6 | 0 | 2026-10-22 (scripts de instalação sem entrada externa; pendente) |
| `eslint-@typescript-eslint/no-explicit-any` | 6 | 4 | 0 | 2 | 2026-10-22 (D: `T = any` padrão de genérico) |
| `eslint-@typescript-eslint/no-unused-vars` | 8 | 5 | 2 | 1 | 2026-10-22 (FP: omissão por rest, parâmetro de decorator) |
| `sentinel-cc-funcao-longa` | 20 | 13 | 5 | 2 | 2026-10-22 Java (FP: 3 em jQuery embutido, 1 em `ejs.js:137` com 886 linhas que a leitura não confirma; D: classe anônima contada como função) |
| `sentinel-cc-complexidade` | 20 | 15 | 5 | 0 | 2026-10-22 Java (FP: 3 em jQuery embutido, `ejs.js:137` com 175; não recontei os pontos de decisão dos VP) |
| `sentinel-ts-console-log` | 6 | 4 | 2 | 0 | 2026-10-22 (FP: saída de CLI) |
| `sentinel-cc-aninhamento` | 5 | 3 | 1 | 1 | 2026-10-22 (FP: objeto literal conta como nível; pendente) |
| `eslint-sonarjs/cognitive-complexity` | 6 | 6 | 0 | 0 | 2026-10-22 (só a métrica foi conferida) |
| `eslint-security/detect-non-literal-fs-filename` | 6 | 0 | 6 | 0 | 2026-10-22 (CLI/scripts, sem entrada externa; pendente) |
| `gitleaks-jwt` | 5 | 0 | 0 | 5 | 2026-10-22 (dump SQL de demonstração, tokens vencidos) |
| `sentinel-proj-committed-env` | 3 | 3 | 0 | 0 | 2026-10-22 |
| `sentinel-proj-no-tests` | 2 | 2 | 0 | 0 | 2026-10-22 |
| `owasp-dependency-CVE-2022-0235-node-fetch` | 1 | 0 | 1 | 0 | 2026-10-22 (corrigida: maior correção do mesmo major) |
| `hadolint-DL3044` | 1 | 1 | 0 | 0 | 2026-10-22 |
| `sentinel-sql-injection` | 6 | 0 | 6 | 0 | 2026-10-22 Java (`update`/`execute`/`query` em qualquer objeto; corrigida no PR #14) |
| `sentinel-senha-fixa-no-codigo` | 11 | 3 | 8 | 0 | 2026-10-22 Java (nome de campo, cabeçalho e prefixo como valor; corrigida no PR #15) |
| `sentinel-xxe-parser-xml` | 3 | 1 | 1 | 1 | 2026-10-22 Java (FP: `TransformerFactory` só serializa DOM; D: só cria documento vazio) |
| `checkstyle-magic.number` | 13 | 5 | 4 | 4 | 2026-10-22 Java (FP: layout Swing gerado, máscaras hexadecimais; D: código HTTP em `switch`, capacidade inicial) |
| `jscpd-codigo-duplicado` | 12 | 1 | 8 | 3 | 2026-10-22 Java (FP: cabeçalho de licença, `pom.xml`, `README.html` gerado, `nbactions.xml`) |
| `sentinel-ts-var` | 6 | 0 | 6 | 0 | 2026-10-22 Java (6 de 6 em biblioteca embutida: `ejs.js`, `bootstrap.js`, jQuery; corrigida no PR #16) |
| `sentinel-ts-igualdade-frouxa` | 6 | 0 | 6 | 0 | 2026-10-22 Java (idem: `base.js`, `ejs.js`, `bootstrap.js`) |
| `stylelint-a11y/content-property-no-static-value` | 3 | 0 | 3 | 0 | 2026-10-22 Java (CSS de fonte de ícones embutido; todos os 936 achados estão em 1 repositório; menos de 5 lidos, de um só arquivo) |
| `checkstyle-redundantModifier` | 9 | 8 | 0 | 1 | 2026-10-22 Java (VP de estilo) |
| `checkstyle-hide.utility.class` | 14 | 7 | 3 | 4 | 2026-10-22 Java (FP: `@SpringBootApplication` e classe com construtor privado do Lombok; D: classes com `main`; nos VP só conferi o cabeçalho da classe) |
| `checkstyle-variable.notPrivate` | 6 | 6 | 0 | 0 | 2026-10-22 Java (VP de estilo) |
| `pmd-ConstructorCallsOverridableMethod` | 6 | 6 | 0 | 0 | 2026-10-22 Java (VP, mas severidade ALTA exagerada; pendente) |
| `pmd-ClassWithOnlyPrivateConstructorsShouldBeFinal` | 12 | 12 | 0 | 0 | 2026-10-22 Java (VP de estilo com severidade ALTA; pendente) |
| `pmd-AvoidThrowingRawExceptionTypes` | 9 | 9 | 0 | 0 | 2026-10-22 Java (VP de estilo com severidade ALTA; pendente) |

Taxa de VP nas regras de severidade média ou alta com ao menos 5 revisados (critério do eixo 2): `no-explicit-any` 4/6, `no-unused-vars` 5/8, `funcao-longa` 6/8 (MEDIUM/LOW), `complexidade` 7/8, `any-explicito` 8/10, `gitleaks-jwt` 0/5 (duvidosos), `no-super-linear-move` e `detect-non-literal-fs-filename` 0/6 (LOW). Várias regras ficam abaixo de 80%; o eixo 2 continua em 2.

Segunda rodada de 2026-10-22 (Java/Spring, 140 achados lidos): `sql-injection` 0/6, `senha-fixa-no-codigo` 3/11, `ts-var` 0/6 e `igualdade-frouxa` 0/6 (o motivo é código de terceiros embutido, não a regra), `xxe-parser-xml` 1/3, `magic.number` 5/13, `jscpd` 1/12 (as demais regras de estilo acima ficam em 100% de VP, de pouco valor). Regras `pmd-ReturnEmptyCollectionRatherThanNull` (6) e `pmd-SingletonClassReturningNewInstance` (3) foram vistas, mas não classificadas (não conferi o tipo de retorno nem o padrão de uso).
