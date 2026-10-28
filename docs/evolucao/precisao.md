# Precisão medida por revisão manual

Revisão por amostragem feita pelo agente Sentinel Descoberta (ler o código ao redor de cada achado). Não é auditoria exaustiva: as amostras são pequenas e as taxas são indicativas.
VP = verdadeiro positivo (inclui os de pouco valor, como estilo), FP = falso positivo. Atualizar a tabela a cada rodada, somando aos números anteriores.
Para o eixo 10 do placar chegar a 3 são necessários ≥ 100 achados revisados em ≥ 10 repositórios; hoje há 450 achados em 22 repositórios listados (10 em 4 na rodada de 2026-10-21, 81 em 5 na primeira de 2026-10-22, NestJS, 140 em 5 na segunda de 2026-10-22, Java/Spring, 100 em 5 em 2026-10-28, React, e 119 em 3 na segunda de 2026-10-28, Java/Spring; as linhas de `njsscan-ejs_ect_template`, `eslint-react/jsx-no-undef` e `stylelint-lightness-notation` da rodada React entram pelo PR #71). Dos 3 da última rodada, 2 são variantes (`RuoYi-Vue-Oracle` e `RuoYi-Vue-fast`) de um repositório já revisado em 2026-10-22 (`RuoYi-Vue`) e compartilham quase todo o código: contam como 20 aplicações distintas, não 22. O único novo (`Nasruddin/spring-boot-based-microservices`, Spring Cloud + Kubernetes) é um projeto de demonstração, não um sistema corporativo; o critério de 1/3 de Java/Spring ou Angular em ambiente corporativo do eixo 10 segue sem cobertura. Os 119 não incluem a classificação por heurística de script dos 98 achados de `checkstyle-hide.utility.class` (resumida na linha da regra). Ainda não há veredito humano preenchido em `revisao-humana.md` (0 de 60 linhas): a concordância não foi calculada.

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
| `sentinel-int-http-sem-tls` | 3 | 2 | 1 | 0 | 2026-10-28 Java (FP: `http://` num link de Javadoc em `UUID.java:211`); 2026-10-21 |
| `gitleaks-generic-api-key` | 8 | 3 | 4 | 1 | 2026-10-28 Java (VP: `client_secret` de realm do Keycloak versionado ×3; FP: texto de `description` de coleção Postman ×2); 2026-10-22 (FP: selo de README com `token=` de placeholder) |
| `eslint-regexp/no-super-linear-move` | 6 | 0 | 6 | 0 | 2026-10-22 (scripts de instalação sem entrada externa; pendente) |
| `eslint-@typescript-eslint/no-explicit-any` | 6 | 4 | 0 | 2 | 2026-10-22 (D: `T = any` padrão de genérico) |
| `eslint-@typescript-eslint/no-unused-vars` | 8 | 5 | 2 | 1 | 2026-10-22 (FP: omissão por rest, parâmetro de decorator) |
| `sentinel-cc-funcao-longa` | 20 | 13 | 5 | 2 | 2026-10-22 Java (FP: 3 em jQuery embutido, 1 em `ejs.js:137` com 886 linhas que a leitura não confirma; D: classe anônima contada como função) |
| `sentinel-cc-complexidade` | 28 | 21 | 5 | 2 | 2026-10-28 Java (4 HIGH recontados à mão: `initColumnField` 26, `dataScopeFilter` 21, `textToNumericFormatV4` 21, `importExcel` 43 contra 42 do analisador; antes: 2026-10-28 React e 2026-10-22 Java); 2026-10-28 React (VP: `getFileFormat` 12 e `generateRoutes` 13, recontados à mão; D: componentes React como `MenuAuthorize` (45) e `ApiTreeSelect` (11), em que a soma inclui todos os closures de hooks, e cada closure sozinho é bem menor; 2026-10-22 Java: FP em jQuery embutido, `ejs.js:137` com 175) |
| `sentinel-ts-console-log` | 6 | 4 | 2 | 0 | 2026-10-22 (FP: saída de CLI) |
| `sentinel-cc-aninhamento` | 5 | 3 | 1 | 1 | 2026-10-22 (FP: objeto literal conta como nível; pendente) |
| `eslint-sonarjs/cognitive-complexity` | 6 | 6 | 0 | 0 | 2026-10-22 (só a métrica foi conferida) |
| `eslint-security/detect-non-literal-fs-filename` | 6 | 0 | 6 | 0 | 2026-10-22 (CLI/scripts, sem entrada externa; pendente) |
| `gitleaks-jwt` | 8 | 0 | 0 | 8 | 2026-10-28 Java (D: tokens de exemplo em `README.backup.md` e em coleções Bruno, com `exp` já vencido; 0 de 8 confirmados como credencial viva); 2026-10-22 (dump SQL de demonstração, tokens vencidos) |
| `sentinel-proj-committed-env` | 10 | 4 | 6 | 0 | 2026-10-28 (corrigida no PR #72: só `.env` com chave de nome sensível ou URL com senha; 6 de 7 eram só configuração pública de Vite, 1 era chave de criptografia fixa) |
| `sentinel-proj-no-tests` | 2 | 2 | 0 | 0 | 2026-10-22 |
| `owasp-dependency-CVE-2022-0235-node-fetch` | 1 | 0 | 1 | 0 | 2026-10-22 (corrigida: maior correção do mesmo major) |
| `hadolint-DL3044` | 1 | 1 | 0 | 0 | 2026-10-22 |
| `sentinel-sql-injection` | 6 | 0 | 6 | 0 | 2026-10-22 Java (`update`/`execute`/`query` em qualquer objeto; corrigida no PR #14) |
| `sentinel-senha-fixa-no-codigo` | 11 | 3 | 8 | 0 | 2026-10-22 Java (nome de campo, cabeçalho e prefixo como valor; corrigida no PR #15) |
| `sentinel-xxe-parser-xml` | 3 | 1 | 1 | 1 | 2026-10-22 Java (FP: `TransformerFactory` só serializa DOM; D: só cria documento vazio) |
| `checkstyle-magic.number` | 19 | 7 | 6 | 6 | 2026-10-28 Java (FP: máscara `0xFF` e deslocamento de bits em `Base64`; D: `1024 * 1024`, índice 62 do alfabeto Base64; VP: tamanho de placa 7, cache de 5 horas); 2026-10-22 Java (FP: layout Swing gerado, máscaras hexadecimais; D: código HTTP em `switch`, capacidade inicial) |
| `jscpd-codigo-duplicado` | 18 | 1 | 13 | 4 | 2026-10-28 Java (FP: cabeçalho de `pom.xml` ×2, interface repetida na implementação de serviço ×2, bloco de `import`; D: `GlobalException`/`ServiceException`; em sombra nos perfis); 2026-10-22 Java (FP: cabeçalho de licença, `pom.xml`, `README.html` gerado, `nbactions.xml`) |
| `sentinel-ts-var` | 6 | 0 | 6 | 0 | 2026-10-22 Java (6 de 6 em biblioteca embutida: `ejs.js`, `bootstrap.js`, jQuery; corrigida no PR #16) |
| `sentinel-ts-igualdade-frouxa` | 14 | 8 | 6 | 0 | 2026-10-28 React (8 de 8 `==` reais em código próprio, de estilo; 2026-10-22 Java: 6 FP em `base.js`, `ejs.js`, `bootstrap.js`; corrigida no PR #16, que tirou bibliotecas embutidas da auditoria) |
| `stylelint-a11y/content-property-no-static-value` | 3 | 0 | 3 | 0 | 2026-10-22 Java (CSS de fonte de ícones embutido; todos os 936 achados estão em 1 repositório; menos de 5 lidos, de um só arquivo) |
| `checkstyle-redundantModifier` | 14 | 13 | 0 | 1 | 2026-10-28 Java (VP de estilo: 793 de 797 são `public` em método de interface, 25% dos achados de cada RuoYi-Vue; corrigida: módulo removido do `checks.xml` no PR #75); 2026-10-22 Java (VP de estilo) |
| `checkstyle-hide.utility.class` | 20 | 11 | 5 | 4 | 2026-10-28 Java (FP: `@SpringBootApplication` em 2 de 2 lidos; heurística sobre os 98 achados: 88 classes só com estáticos, 6 anotadas com `@SpringBootApplication`/`@Configuration` (FP), 2 `ScheduleConstants` com membro de instância, 2 `EscapeUtil` com `main`); 2026-10-22 Java (FP: `@SpringBootApplication` e classe com construtor privado do Lombok; D: classes com `main`; nos VP só conferi o cabeçalho da classe) |
| `checkstyle-variable.notPrivate` | 11 | 11 | 0 | 0 | 2026-10-28 Java (VP de estilo; `public static String` não final em `DateUtils` é mutável de verdade); 2026-10-22 Java (VP de estilo) |
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
| `checkstyle-hidden.field` | 6 | 6 | 0 | 0 | 2026-10-28 Java (VP de estilo; conferi a existência do campo em 4 das 6 classes) |
| `pmd-UnusedAssignment` | 6 | 6 | 0 | 0 | 2026-10-28 Java (VP de estilo: inicializador sobrescrito; li a linha e a mensagem, não o fluxo completo em 3 das 6) |
| `pmd-AvoidReassigningParameters` | 6 | 6 | 0 | 0 | 2026-10-28 Java (VP de estilo; vários são só valor padrão para parâmetro nulo) |
| `sentinel-java-log-concatenado` | 6 | 6 | 0 | 0 | 2026-10-28 Java (VP de estilo; 47 achados nos 3 repositórios) |
| `sentinel-java-autowired-campo` | 6 | 6 | 0 | 0 | 2026-10-28 Java (VP de estilo; 205 achados, 6% de cada RuoYi-Vue) |
| `pmd-ReturnEmptyCollectionRatherThanNull` | 7 | 0 | 7 | 0 | 2026-10-28 Java (corrigida no PR #74: fora do conjunto PMD; 19 de 20 achados no Oracle são `byte[]` com `null` como entrada inválida, 1 é `List`) |
| `pmd-AssignmentInOperand` | 5 | 3 | 0 | 2 | 2026-10-28 Java (D: idioma `while ((linha = leitor.readLine()) != null)`; VP: atribuições dentro de `if` em `Base64`) |
| `sentinel-spring-requestbody-sem-valid` | 7 | 7 | 0 | 0 | 2026-10-28 Java (VP; MEDIUM talvez alto para endpoints com `@PreAuthorize`; não conferi o que o serviço valida depois) |
| `pmd-UseLocaleWithCaseConversions` | 4 | 4 | 0 | 0 | 2026-10-28 Java (VP de robustez; MEDIUM é alto para o efeito) |
| `pmd-ImmutableField` | 4 | 4 | 0 | 0 | 2026-10-28 Java (VP de estilo) |
| `checkov-CKV_K8S_21` | 3 | 3 | 0 | 0 | 2026-10-28 Java (VP: Service/Deployment no namespace `default`; 32 achados em 1 repositório) |
| `sentinel-docker-sem-user` | 3 | 3 | 0 | 0 | 2026-10-28 Java (VP: Dockerfiles sem `USER`, conferido por busca) |
| `sentinel-k8s-imagem-latest` | 3 | 3 | 0 | 0 | 2026-10-28 Java (VP: `:latest` em compose e manifesto) |
| `sentinel-crypto-hash-fraco` | 2 | 2 | 0 | 0 | 2026-10-28 Java (VP: MD5 em `Md5Utils.java:22`, o mesmo código nos dois RuoYi-Vue; não conferi para que o hash é usado) |
| `sentinel-xss-response-write` | 1 | 0 | 1 | 0 | 2026-10-28 Java (FP: `ServletUtils.java:146` escreve JSON com `application/json`; a origem do texto não aparece no método) |
| `sentinel-api-cors-wildcard` | 1 | 1 | 0 | 0 | 2026-10-28 Java (VP: cabeçalho `Access-Control-Allow-Origin: *` em `GenController.java:257`; o `CorsFilter` com `addAllowedOriginPattern("*")` de `ResourcesConfig.java:58` não é apontado: falso negativo) |
| `sentinel-spring-requestmapping-sem-method` | 1 | 0 | 0 | 1 | 2026-10-28 Java (D: `@RequestMapping("/")` de página inicial, método sem efeito colateral) |
| `sentinel-codigo-comentado` | 3 | 3 | 0 | 0 | 2026-10-28 Java (VP de estilo; inclui blocos de configuração do Quartz comentados) |
| `kube-score-container-resources` | 4 | 1 | 0 | 3 | 2026-10-28 Java (VP: `grafana.yml`, Deployment sem `resources`; D ×3: o achado fica na linha 0, no 1º recurso do arquivo (ConfigMap), e não conferi o Deployment correspondente) |

Taxa de VP nas regras de severidade média ou alta com ao menos 5 revisados (critério do eixo 2): `no-explicit-any` 4/6, `no-unused-vars` 5/8, `funcao-longa` 6/8 (MEDIUM/LOW), `complexidade` 7/8, `any-explicito` 8/10, `gitleaks-jwt` 0/5 (duvidosos), `no-super-linear-move` e `detect-non-literal-fs-filename` 0/6 (LOW). Várias regras ficam abaixo de 80%; o eixo 2 continua em 2.

Segunda rodada de 2026-10-22 (Java/Spring, 140 achados lidos): `sql-injection` 0/6, `senha-fixa-no-codigo` 3/11, `ts-var` 0/6 e `igualdade-frouxa` 0/6 (o motivo é código de terceiros embutido, não a regra), `xxe-parser-xml` 1/3, `magic.number` 5/13, `jscpd` 1/12 (as demais regras de estilo acima ficam em 100% de VP, de pouco valor). Regras `pmd-ReturnEmptyCollectionRatherThanNull` (6) e `pmd-SingletonClassReturningNewInstance` (3) foram vistas, mas não classificadas (não conferi o tipo de retorno nem o padrão de uso).

Rodada de 2026-10-28 (5 aplicações React, 100 achados lidos; sem Java/Spring nem Angular): taxa de VP nas regras de severidade média ou alta com ao menos 5 lidos nesta rodada: `any-explicito` 6/8 (2 duvidosos), `igualdade-frouxa` 8/8 (estilo), `mem-addeventlistener-sem-remove` 0/6, `jsx-no-undef` 0/6, `njsscan-ejs_ect_template` 0/7, `proj-committed-env` 1/7, `proj-no-lockfile` 5/12, `declaration-no-important` 7/7 (estilo), `dup-bloco` 3/6 (LOW, sem contar 2 duvidosos). `sentinel-cc-funcao-longa` foi vista (11 amostras) mas só conferi a métrica, não a contagem de linhas nem se o JSX infla o número; fica sem linha de contagem. As correções saíram nos PRs #70, #71 e #72.
