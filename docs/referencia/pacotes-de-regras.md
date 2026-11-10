# Qualidade de código, pacotes de regras e perfis


Além de segurança e dependências, a auditoria avalia **clean code, boas práticas, resiliência de integrações e lint**,
sem exigir nada instalado (as três primeiras camadas) e usando PMD/ESLint quando existirem.

| Camada | O que verifica | Precisa instalar? |
|---|---|---|
| **CleanCode** (interno) | função longa, muitos parâmetros, complexidade ciclomática, aninhamento profundo | não |
| **Regras Semgrep** `audit-rules/packs/core-quality/rules/` | 41 regras no total: regras Java (catch vazio, `return null` em coleção, injeção por campo, estado estático mutável, `==` em String, `@Transactional` privado, log concatenado, thread manual, service locator, singleton manual...), regras TypeScript/React (`any`, `@ts-ignore`, `var`, `==`, `useEffect` async, `key` por índice, token em `localStorage`...) e 9 de **integração** (HTTP/JDBC/axios/fetch sem timeout, pool ilimitado, HTTP sem TLS) | Semgrep |
| **PMD** (lint Java) | boas práticas, propensão a erro, design e segurança do PMD 7 (ruleset embutido; troque com `PMD_RULESET`) | `pmd` no PATH |
| **ESLint** (lint TypeScript) | `typescript-eslint` recommended + `eqeqeq`, `no-var`, `no-console`, `prefer-const`, `no-eval` | `eslint` no PATH **e** `/opt/eslint/eslint.config.mjs` |

Como funciona:

- **Limites do CleanCode** (`CLEANCODE_*`): 50 linhas por função (100 para componente JSX), 5 parâmetros, complexidade 10, aninhamento 4. As métricas são **textuais** (um scanner de chaves que ignora comentários e strings), não uma árvore sintática: é rápido e não depende de nada, mas pode errar em código muito fora do comum. Testes, `target`, `node_modules`, `dist` e arquivos gerados são ignorados.
- **Sem contagem em dobro**: quando Semgrep e PMD/ESLint apontam o mesmo ponto (catch vazio, `var`, `==`, `any`, `console.log`), fica um finding só, com a maior severidade e a nota "Também detectado por...". Só se juntam equivalências conhecidas (`FindingDeduplicator`); regra desconhecida nunca é escondida.
- **O ESLint nunca carrega a configuração do repositório auditado** (`--no-config-lookup`): um `eslint.config.js` é código e seria executado no servidor. Vale a configuração de `audit-rules/lint/eslint/`.
- **Imagem Docker**: PMD vem ligado (`WITH_PMD=true`); ESLint (com Node 20 e Stylelint) vem ligado (`WITH_ESLINT=true`; `--build-arg WITH_ESLINT=false` tira).
- Cada regra nova tem orientação de correção (antes/depois) no catálogo, exibida no dashboard; um teste impede regra sem orientação.
- A taxa de falso positivo por regra (`GET /api/v1/rules/quality`) mostra quais regras estão incomodando e devem ser ajustadas.

```bash
# testar as regras Semgrep de qualidade
semgrep --test --config audit-rules/packs/core-quality/rules audit-rules/packs/core-quality/tests
```

## Pacotes de regras, perfis e arquitetura (plug and play)

As regras não ficam mais no código: são **pacotes** (pastas com um `pack.yml`) que você adiciona, atualiza ou remove sem recompilar.
Vêm prontos em `audit-rules/packs/`: `core-security` (101 regras Semgrep, 31 delas para backend Node/Express), `core-spring` (18, Spring Boot e JPA), `core-persistencia` (13: N+1, escrita em loop, `findAll` sem paginação, `SELECT *`, `LIKE '%x'`, `ORDER BY random()`, `DELETE`/`UPDATE` sem `WHERE`, `@ManyToOne` EAGER, log de SQL em DEBUG), `core-iac` (Dockerfile, Compose e Kubernetes),
`core-quality` (41, clean code, integrações e **vazamento de memória em Java**), `core-frontend` (29: Angular, React, HTML, CSS/SCSS, acessibilidade, performance e **vazamento de memória no navegador**), `market-tools` (ferramentas externas, veja abaixo), `ts-clean-code` (padrão TypeScript, opt-in) e `core-architecture` (exemplo de política de camadas e componentes, **desligado por padrão**).

### Estrutura de um pacote

```
meu-pacote/
├── pack.yml              # id, name, version, description, author, languages, enabled-by-default, min-sentinel
├── rules/*.yml           # regras Semgrep (metadata.status: draft|experimental|stable|deprecated)
├── tests/                # casos "// ruleid:" / "// ok:" — mesmo nome do arquivo de regra
├── remediation/*.yml     # orientação de correção por id de regra (key, summary, before, after, refs)
├── dedup.yml             # regras que descrevem o mesmo problema (agrupa em um achado; `prefer: <Ferramenta>` escolhe qual fica, padrão Semgrep)
├── tools/*.yml           # fontes SARIF de outras ferramentas (veja abaixo)
└── architecture.yml      # camadas, convenções, arquivos obrigatórios, política de bibliotecas
```

Tudo é validado ao carregar (chaves desconhecidas, severidade inválida, regex quebrada, camada inexistente, ids repetidos...).
Pacote inválido é **rejeitado com o motivo** e os demais continuam; veja em `GET /api/v1/packs`.

### De onde vêm os pacotes

- `audit-rules/packs` (embutido), mais `PACKS_DIRS` (pastas extras, separadas por vírgula);
- um **repositório Git central**: `PACKS_GIT_URL`, `PACKS_GIT_REF` (padrão `main`), `PACKS_GIT_PATH`. É atualizado a cada `refresh-interval` e em `POST /api/v1/packs/reload`; se a rede falhar, segue com o cache.
- Mesmo `id` em dois lugares: vale a **maior versão**. `PACKS_DISABLED=id1,id2` desliga pacotes.

### Ferramenta para autores (`sentinel-pack`)

```bash
sentinel-pack new meu-time --dir ./meus-pacotes      # cria pacote de exemplo (já com teste)
sentinel-pack validate ./meus-pacotes                # mesmas checagens do servidor (--strict: regra sem teste é erro)
sentinel-pack test ./meus-pacotes/meu-time           # semgrep --test
sentinel-pack keygen minha-chave                     # Ed25519: minha-chave.key (segredo) e .pub
sentinel-pack pack ./meus-pacotes/meu-time --key minha-chave.key
sentinel-pack verify ./meus-pacotes/meu-time --public-key minha-chave.pub
```

Na imagem Docker o comando já existe. Fora dela: `SENTINEL_HOME=<pasta quarkus-app> audit-rules/bin/sentinel-pack ...`.

**Laboratório de regras:** `POST /api/v1/rules/playground` com `{"rule": "<yaml>", "code": "<trecho>", "extension": "java"}` valida a regra e roda o Semgrep
de verdade sobre o trecho (máx. 20 mil/100 mil caracteres, 30 s, 2 execuções simultâneas). Desligue com `PLAYGROUND_ENABLED=false`.

### Perfis (por sistema)

Arquivos em `profiles/*.yml` na raiz de pacotes (ex.: `audit-rules/packs/profiles/`). Cada sistema escolhe um perfil na tela do sistema (ou `PUT /api/v1/systems/{id}/profile`).

```yaml
id: legado
extends: base-comum            # opcional; sem 'packs', herda do pai (ou, sem pai, todos os habilitados por padrão)
packs: [core-security, core-quality]
disable-packs: [core-spring]
rules:
  "sentinel-java-*": {shadow: true}      # aparece no relatório, mas não pesa no score nem derruba o pipeline
  sentinel-god-object: {severity: low}
  sentinel-sql-injection: {severity: critical}
  sentinel-crypto-hash-fraco: {disabled: true}
thresholds:                  # clean code: max-function-lines, max-component-lines, max-parameters, max-complexity, max-nesting, max-findings
  max-function-lines: 120
```

**Lado da regra (`metadata.side`):** `back`, `front` ou `common` (Docker, Kubernetes e o que vale para qualquer linguagem). Sem o campo, o Sentinel deduz: só `java`/`kotlin` é back; só `javascript`/`typescript` é front; regra de texto (`regex`) decide pelas extensões de `paths.include`; infra e dúvida são `common`. Escreva o campo quando a dedução erra (ex.: regra de Node/Express, que é TypeScript no back-end). A tela do motor de regras e o escopo por lado usam esse valor.

**Modo sombra:** regra com `metadata.status: draft|experimental` já nasce em sombra; `shadow: false` no perfil a promove. Serve para
introduzir uma regra nova sem derrubar a nota de ninguém: observe o volume, ajuste, promova. Exemplos prontos: `profiles/legado.yml`, `profiles/arquitetura-em-camadas.yml`.

**Perfis para Quarkus:** `quarkus-react` e `quarkus-vue` reúnem os pacotes padrão (sem Spring nem Angular) com `core-quarkus`
(injeção por reflexão, `getenv`, `await()` e `Thread.sleep` em código reativo, HQL do Panache concatenado, segredo em `application.properties`),
`core-boas-praticas` (recurso sem `try-with-resources`, `@Transactional` no controller, `strict: false`, cast sem validação, `fetch` em `useEffect`) e,
no Vue, `core-vue` (`v-html`, `v-for` sem `:key`, `v-if` com `v-for`, `reactive` desestruturado). Os três pacotes têm `enabled-by-default: false`:
só pesam na nota de quem escolhe um desses perfis.

### Guia de código

A tela **Guia de código** (menu, depois do Ranking de Sistemas) mostra, por tecnologia (Java, Spring, Quarkus, TypeScript, Angular, React e Vue),
as boas práticas ("faça" e "evite"), as regras do Sentinel que valem para ela (com severidade, CWE e antes/depois), o que o Sentinel ainda não verifica
e um `CLAUDE.md` pronto para a IA seguir. As regras vêm dos pacotes carregados; o texto das boas práticas, os comandos e o estado da revisão ficam em
`audit-api/src/main/resources/guia/stacks.yml`. A **nota do guia** (0 a 10) só conta o que foi feito: `review.docs` (práticas conferidas na documentação
oficial), `review.team` (revisão da equipe) e `review.ai` (teste com a IA numa tarefa real) começam em `false` e são marcados à mão.

A seção **Commits e revisão** (bloco `process:` do mesmo arquivo, igual para todas as tecnologias, e também no `CLAUDE.md`) resume mensagem de commit, PR pequeno e
revisão de código, com base no Google Engineering Practices e no AWS Well-Architected DL.CR.1. O Sentinel confere só a **mensagem dos commits**
(analisador `Mensagens de commit`, regra `sentinel-commit-fora-do-padrao`, severidade baixa): lê os últimos 200 commits do clone e aponta um achado único quando
mais de 30% (`commit-max-bad-percent`) fogem de `tipo(escopo): descrição` (até 100 caracteres, sem descrição vazia); precisa de 20 commits avaliáveis
(`commit-min-commits`). Merge, revert e fixup/squash não contam. Tamanho do PR e qualidade da revisão continuam por conta da equipe.

### Arquitetura e componentes declarativos (`architecture.yml`)

```yaml
layers:   [{name: resource, paths: ["**/resource/**"]}, {name: repository, paths: ["**/repository/**"], packages: ["*.repository.*"]}]
forbid:   [{from: resource, to: repository, severity: high, message: "Passe pelo serviço"}]   # allow + strict: true = só o que está em allow
conventions:
  - {id: endpoint-sem-auth, files: ["**/resource/**/*.java"], exclude: ["**/*Test.java"], require: "@RolesAllowed", severity: medium}
required-files: [{path: README.md, severity: low}]
components:
  forbidden:   [{dep: "log4j:log4j", reason: "descontinuada"}]
  min-version: [{dep: "org.apache.logging.log4j:log4j-core", version: ">=2.17.1", severity: critical}]
  required:    [{dep: "org.slf4j:*"}]
  approved-only: [{dep: "com.acme.*"}]       # dependências diretas fora da lista viram achado
```

Imports Java e TypeScript (relativos e por pacote) são resolvidos para camadas. Ids gerados: `sentinel-arch-layer-<de>-<para>`, `sentinel-arch-conv-<id>`,
`sentinel-arch-required-file`, `sentinel-comp-forbidden|min-version|required|not-approved`. Versão não resolvida (`${prop}`, intervalo) **não** reprova.

### Outras ferramentas via SARIF (`tools/*.yml`)

```yaml
- id: ci-reports                      # lê SARIF que o CI do time já gera (sempre permitido)
  input: "reports/*.sarif"
- id: spotbugs                        # executa uma ferramenta que grava o SARIF em {output}
  command: [spotbugs-sarif, --project, "{project}", --output, "{output}"]
  severity-map: {error: high, warning: medium, note: low}
- id: hadolint                        # ferramenta que imprime o SARIF na saída padrão
  stdout: true
  applies-to: ["**/Dockerfile", "**/*.dockerfile"]   # só roda se o projeto tiver ao menos um arquivo assim
  command: [hadolint, -f, sarif, "{files}"]          # {files} = os arquivos que casaram (relativos, até 500)
```

Comandos nunca passam por shell, rodam com o projeto como diretório de trabalho e só executam se o pacote for liberado
(`PACKS_ALLOW_TOOLS=true`, para todos) ou **confiável** (`PACKS_TRUSTED_TOOL_PACKS`, abaixo). Ferramenta ausente é ignorada sem erro.

### Escopo de tecnologias e analisadores

O Sentinel analisa **Angular, TypeScript, JavaScript, React, HTML, CSS, SCSS, Java, Spring Boot, Docker e Kubernetes**. Analisadores de Python, Go e Terraform foram removidos.

| Tecnologia | Analisadores |
|---|---|
| Java | PMD, **Checkstyle** (bugs prováveis, tamanho e complexidade); regras Semgrep de segurança (injeção, criptografia, OWASP API, XSS, **XXE, SpEL, redirect aberto, desserialização do Jackson, Zip Slip, dado sensível no log, senha fixa, cookie sem Secure**), clean code e integrações |
| SQL / Banco | **Squawk** (migrations do Postgres: índice sem `CONCURRENTLY`, mudança de tipo, `NOT NULL`, `int` em vez de `bigint`...) e **SQLFluff** (estilo, dialeto Postgres) em `db/migration`, `migrations`, `db/changelog`; regras Semgrep `sentinel-sql-*` e `sentinel-jpa-*` (pacote `core-persistencia`) nas consultas dentro do código Java |
| Memória | regras Semgrep `sentinel-mem-*`: `ThreadLocal` sem `remove()`, coleção estática, cache sem limite, executor sem `shutdown`, `new Thread`, leitura total de arquivo; no front, `setInterval`, `addEventListener`, `subscribe()` e `useEffect` sem limpeza |
| Spring Boot / JPA | pacote `core-spring` (18 regras): Actuator exposto, console H2, senha em properties, `ddl-auto`, `open-in-view`, `@RequestMapping` sem método, `@RequestBody` sem `@Valid`, `permitAll()` geral, `frameOptions` desligado, `FetchType.EAGER`, `@Data` em entidade; `@CrossOrigin` aberto |
| TypeScript / JavaScript | ESLint com `typescript-eslint`, `security`, `no-unsanitized`, `sonarjs`, **`promise` e `regexp` (ReDoS)**; regras Semgrep `sentinel-ts-*`; njsscan |
| Angular | `angular-eslint` no código e nos **templates** (acessibilidade incluída); regras `sentinel-ng-*` (bypassSecurityTrust, nativeElement, `::ng-deep`, `*ngFor` sem `trackBy`, método no template) |
| React | `react`, `react-hooks` e `jsx-a11y`; regras `sentinel-react-*` |
| HTML | **`@html-eslint`** (lang, title, alt, label, ids duplicados, viewport, ARIA, tabindex...) e regras `sentinel-html-*` / `sentinel-fe-*` |
| CSS / SCSS | Stylelint (`standard-scss`, **acessibilidade com `stylelint-a11y`**, `!important`, `transition: all`, aninhamento) e regras `sentinel-css-*` / `sentinel-scss-*` |
| Docker / Compose | `core-iac` (Dockerfile: `latest`, root, `ADD` remoto, `chmod 777`, segredo em `ARG`/`ENV`, SSH exposto, apt; **Compose: `privileged`, socket do Docker, `network_mode: host`, segredo no ambiente**), Hadolint, Checkov, Trivy (misconfig) |
| Kubernetes | regras `sentinel-k8s-*` (`hostNetwork`, `hostPath`, root, escalada de privilégio, capabilities, `:latest`), kube-linter, **kube-score**, Checkov, Trivy |
| Arquitetura (TS/JS) | **Dependency-Cruiser**: ciclos entre módulos, módulos órfãos e código de produção importando teste (roda sem `node_modules`) |
| Qualquer linguagem | Gitleaks e histórico (segredos), OSV/Trivy (dependências vulneráveis), **jscpd (código duplicado)** |

### Ferramentas externas (`market-tools`)

Pacote embutido que liga por SARIF: **njsscan** (segurança de JS/Node), **Stylelint** (CSS/SCSS), **Checkstyle** (Java), **jscpd** (código duplicado em Java, TS/JS, CSS/SCSS e HTML; limites em `JSCPD_MIN_LINES`/`JSCPD_MIN_TOKENS`), **Squawk** e **SQLFluff** (SQL de migrations), **Dependency-Cruiser** (ciclos entre módulos TS/JS), **Hadolint** (Dockerfile), **kube-linter** e **kube-score** (manifestos Kubernetes em `k8s/`, `kubernetes/`, `manifests/`) e **Checkov** (Dockerfile, Kubernetes e GitHub Actions). O ESLint tem analisador próprio. Cada ferramenta só roda se estiver instalada **e** se o projeto tiver arquivos do tipo que ela entende.

- Na imagem Docker: `WITH_ESLINT=true` (padrão) instala Node 24, ESLint e Stylelint; `WITH_MARKET_TOOLS=true` (padrão) instala njsscan, Checkov, SQLFluff, kube-linter, kube-score e o Checkstyle; o Hadolint (GPL-3.0) só entra com `WITH_HADOLINT=true` (precisa da JVM que a imagem já tem); o jscpd, o Squawk e o Dependency-Cruiser (`/opt/extras`) vêm junto com o ESLint. Fora do Docker, basta ter os executáveis no `PATH` e, para o Stylelint, a pasta `audit-rules/lint/stylelint` instalada em `/opt/stylelint`.
- Os findings entram com o nome da ferramenta e o id `<ferramenta>-<regra>` (ex.: `stylelint-declaration-no-important`, `eslint-react-hooks/rules-of-hooks`). Achados repetidos entre ferramentas (ESLint e Semgrep, Hadolint/Checkov e as regras de Dockerfile) são juntados (`dedup.yml`).
- O Stylelint inclui regras de estilo: para não pesar no score, use um perfil `rules: { "stylelint-*": { shadow: true } }`.
- O SQLFluff e o Squawk olham só arquivos `.sql`; o Squawk, só os de pastas de migration. Dialeto do SQLFluff: `postgres` (troque com `SQLFLUFF_DIALECT`). O Squawk ignora por padrão `prefer-robust-stmts`, `require-lock-timeout` e `require-statement-timeout` (política de ambiente); troque com `SQUAWK_EXCLUDE`. Para silenciar o estilo: `rules: { "sqlfluff-*": { shadow: true } }`.
- O Dependency-Cruiser roda **sem** `node_modules` (só ciclos e órfãos entre arquivos do próprio projeto). Knip e depcheck ficaram de fora de propósito: sem as dependências instaladas eles acusam como "sem uso" pacotes que o código usa.
- **Confiança:** `PACKS_TRUSTED_TOOL_PACKS=market-tools,java-google-style` (padrão) libera as ferramentas desses pacotes sem ligar `PACKS_ALLOW_TOOLS`.
  Vale **só** para o pacote que vem na pasta do produto; um pacote externo com o mesmo id, mesmo mais novo, não herda a confiança.
  Para desligar tudo: `PACKS_TRUSTED_TOOL_PACKS=` (vazio) ou `PACKS_DISABLED=market-tools`.
- Não incluídos de propósito: Grype e OSV-Scanner (repetem o OSV interno e o Trivy), Lighthouse (exige navegador e site publicado) e SpotBugs/FindSecBugs (precisam do código Java já compilado).

### Padrão de código TypeScript / Angular (`ts-clean-code`)

Pacote opt-in (desligado por padrão) que transforma o padrão de clean code da casa em verificações automáticas. Veja o padrão completo, o que cada regra checa e os conflitos encontrados no documento original em [`docs/padrao-typescript/README.md`](../padrao-typescript/README.md).

- **7 regras Semgrep** (`sentinel-tsp-*`): membro privado com `_`, público sem `_`, modificador de acesso explícito, enum terminado em `Enum`, interface com `I`/`Interface`, hooks de ciclo de vida só com chamadas a métodos privados e sem abreviações (`num`, `cod`, `desc`...).
- **Perfil `padrao-typescript-angular`**: liga o pacote e fixa os limites do padrão (método 50 linhas, complexidade 10, aninhamento 3, arquivo 500 linhas).
- **Lint de estrutura e nomes (ESLint)**: use `ESLINT_CONFIG=/opt/eslint/eslint.padrao-ts.config.mjs`. Cobre tipo de retorno, ordem de membros, nomes, linhas em branco, `any`, `console` e `==`.
- **Nos seus projetos Angular**: copie `docs/padrao-typescript/eslintrc.json` para `.eslintrc.json` (mesmas regras, no formato do ESLint 8 + Angular + Prettier).

### Clean code em TypeScript (`ts-clean-code-labs42`)

Pacote opt-in com 21 regras `sentinel-cct-*` inspiradas no guia [clean-code-typescript](https://labs42io.github.io/clean-code-typescript/): parâmetros, erros e promessas, comentários, nomes e importações. Perfil `clean-code-typescript` (severidade baixa). A tabela guia → regra e o que fica para revisão humana estão em [`docs/padrao-typescript/clean-code-labs42.md`](../padrao-typescript/clean-code-labs42.md).

### Padrão Google (Java e TypeScript/Angular) + SEI CERT e Sonar

Perfis opt-in baseados em guias públicos: `padrao-google-java` (Google Java Style via Checkstyle, mais 11 regras de robustez e segurança do SEI CERT e do Sonar) e `padrao-google-typescript-angular` (Google TypeScript Style Guide, Angular Style Guide e regras do Sonar via ESLint, com `ESLINT_CONFIG=/opt/eslint/eslint.google-angular.config.mjs`). O perfil TypeScript contradiz o padrão da casa (`padrao-typescript-angular`): use um ou outro. Veja o que é verificado, o que fica para a revisão e as decisões nos conflitos entre os guias em [`docs/padrao-google/README.md`](../padrao-google/README.md).

### Auditoria de PR (só o que mudou)

`POST /api/v1/audits` com `"baseRef": "main"` (ou `--base-ref main` no CLI) mantém só achados em arquivos alterados em relação a esse branch.
O "novo vs. anterior" compara com a última auditoria **completa** do branch base; auditorias de PR nunca viram base nem entram na tendência.
Se não for possível calcular o diff, nada é escondido (todos os achados são mantidos). O CLI grava um resumo para comentar no PR: `--pr-comment pr.md` (ele **não** posta; o pipeline posta).

```bash
java -jar sentinel-audit-cli.jar --system-id 1 --branch feature/x --base-ref main --fail-on HIGH --pr-comment pr.md
```

### Segurança dos pacotes

- Um pacote é **código de confiança**: só instale de fontes suas. O YAML é lido sem instanciar classes, com limites de tamanho/aliases; links simbólicos são recusados.
- `command` em `tools/` é **desligado** por padrão (`PACKS_ALLOW_TOOLS=false`); quando ligado, roda sem shell, com tempo limite.
- **Integridade:** `pack.lock` (SHA-256 de cada arquivo) detecta alteração; qualquer divergência rejeita o pacote. Com `PACKS_PUBLIC_KEY` + `pack.sig` (Ed25519) a origem é conferida;
  `PACKS_REQUIRE_SIGNATURE=true` só carrega pacotes assinados com essa chave — **inclusive os `core-*`**, que você deve assinar com a sua chave (`sentinel-pack pack --key`).
- URLs de Git: só https/ssh; nomes que começam com `-` são recusados.

---
