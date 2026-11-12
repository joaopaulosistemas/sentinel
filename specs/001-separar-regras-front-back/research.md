# Pesquisa (Fase 0): escopo front-end e back-end

Tudo abaixo vem de leitura do código da `main` (commit `9865b57`) e do `docs/evolucao/LOG.md`. Nada foi executado: Maven, Docker e o Opengrep não rodam na sessão do Claude na nuvem, então o que depende deles aparece como **NÃO verificado**.

## R1. A funcionalidade já existe em grande parte

- **Decisão**: o plano cobre só as lacunas FR-008 a FR-011.
- **Evidência**: LOG 12.48.0 e 12.49.0; `RuleSide.java`, `RuleCatalog.entryOf`, `SystemTuning.disableSides`, `AuditPolicy.applyTuning`/`analyzerOffNote`/`inDisabledSide`, `EngineConfigViews`, e os testes `RuleSideTest`, `SystemTuningSidesTest`, `AnalyzerCatalogSideTest`.
- **Alternativas**: refazer do zero (descartada, duplicaria código e testes que já passam).

## R2. Regra sem grupo: manter a dedução e acrescentar um teste de guarda

- **Decisão**: `RuleSide.infer` continua deduzindo o lado; um teste novo exige `metadata.side` explícito quando a dedução cai em "comum" e a regra não é de infraestrutura.
- **Motivo**: `RuleCatalog.entryOf` já cai em dedução para as 255 regras; falhar ao carregar quebraria pacotes de terceiros e o princípio V (plug-and-play). A dedução cai em "comum" na dúvida, e é isso que o teste de guarda impede de passar despercebido.
- **Alternativas**: falhar ao carregar (era a resposta inicial do João, dada sem saber da dedução; substituída por delegação); grupo padrão `back` (descartada, esconderia regra de front).

## R3. Lado das ferramentas externas (`market-tools`)

A dedução por `applies-to` (`RuleSide.infer(["regex"], applies-to)`) acerta quase todas, mas erra em duas famílias. Tabela do que a dedução daria e do que o plano declara:

| Ferramenta | `applies-to` | Dedução por padrões | Lado declarado |
|---|---|---|---|
| stylelint | `*.css`, `*.scss` | front | (deduzido) front |
| dependency-cruiser | `*.ts`, `*.tsx`, `*.js`, `*.jsx` | front | (deduzido) front |
| checkstyle | `*.java` | back | (deduzido) back |
| joern-taint | `*.java` | back | (deduzido) back |
| **njsscan** | `*.js`, `*.mjs`, `*.cjs`, `*.ts` | **front (errado)**: é análise de Node.js/Express | **`side: back`** (mesmo critério da 12.48.0, que marcou 15 regras de Node/Express como back) |
| **squawk, sqlfluff** | `*.sql` | **back (errado para o que o João decidiu)**: `.sql` está em `BACK_EXTENSIONS` | **`side: common`** (SQL fica sempre ligado) |
| checkov, hadolint, kube-linter, kube-score | Dockerfile, k8s | comum | (deduzido) comum |
| spectral | `openapi*`, `swagger*` | comum (extensões `.yaml`/`.json` não decidem) | (deduzido) comum |
| jscpd | Java + TS + CSS + HTML | comum (mistura) | `side: common`; ver R4 |

- **Decisão**: acrescentar a chave opcional `side` ao YAML das ferramentas e à validação (`ToolSpecParser.checkKeys`); declarar explicitamente `njsscan` (back), `squawk` e `sqlfluff` (common) e `jscpd` (common). As demais usam a dedução.
- **Alternativas**: só deduzir (descartada, erraria njsscan e SQL); lado por pacote (descartada, `market-tools` é um pacote só com todos os lados).

## R4. jscpd (duplicação) olha Java e TypeScript juntos

- **Fato**: o analisador interno `Duplicação` já está em `SIDE_BY_FILE_TOOLS` (`AuditPolicy`): com um lado desligado ele perde só os achados dos arquivos do lado desligado.
- **Decisão**: tratar o jscpd externo do mesmo jeito. Ele é declarado `common` (continua rodando) e seus achados em arquivos do lado desligado são descartados, reaproveitando `inDisabledSide` com o nome da fonte. Isso mantém o comportamento igual ao da `Duplicação`.
- **Verificado na leitura**: `SarifAnalyzer` grava `f.setToolSource(spec.name())`, ou seja, o campo `name` do YAML (para o jscpd, `"jscpd"`). O filtro por arquivo usa esse nome, não o `id`.

## R5. Onde o selo é calculado e exibido

- **Fato**: `SystemViews.summary()` monta `SystemSummaryResponse`, que alimenta o Ranking (`audit-dashboard/src/pages/Ranking/RankingPage.tsx`, 291 linhas). Não há `disableSides` em nenhum arquivo do ranking nem dos relatórios.
- **Decisão (tomada por delegação do João em 2026-11-12)**: o selo mostra o escopo **usado na última auditoria concluída**, e não o ajuste atual do sistema, porque a nota exibida é dessa auditoria. Para isso, cada `AuditRun` guarda os lados desligados no momento em que a auditoria roda: coluna nova e anulável `disabled_sides` (migração Flyway `V6__...`, a pasta tem V1 a V5). Auditoria antiga fica com `NULL`, que significa "escopo desconhecido" e não mostra selo (nunca afirmamos o que não sabemos).
- O `SystemSummaryResponse` ganha `disabledSides` (lista de strings) preenchido por `SystemViews.summary()` a partir da última auditoria concluída (`lastRun`, já calculada ali). O Ranking, o relatório do sistema (`ReportService`, `PdfReportService`) e o do portfólio leem esse campo. `ScoringService` não é tocado.
- **Portfólio**: `PortfolioReportService` recebe uma lista de `SystemSnapshot` montada em `ReportResource` (`/portfolio`); o `SystemSnapshot` ganha `disabledSides`. **NÃO verificado**: de onde `ReportResource` obtém cada snapshot (se reusa `SystemViews.summary`); a primeira tarefa do PR B lê isso.
- **Alternativas**: ler o ajuste atual (descartada: pode contradizer a nota exibida); endpoint separado (descartada, N chamadas no ranking); guardar o selo em `RiskScore` (descartada, a nota não muda); reaproveitar `context_json` da auditoria (descartada: é lido por `InsightsService` com um tipo fixo, `AuditContextData`, e misturaria assuntos).

## R6. Executar menos regras no Opengrep (FR-011, P3)

- **Fato**: `SemgrepAnalyzer.resolveConfigs(policy)` passa ao motor as pastas `rules/` dos pacotes ativos; o lado desligado só é filtrado depois em `AuditPolicy.apply`.
- **Opções**: (a) flag de exclusão de regra do próprio motor; (b) gerar uma pasta temporária de regras sem as do lado desligado.
- **Decisão**: adiar. É ganho de desempenho, está no LOG como "não feito" e não muda resultado. Antes de planejar a implementação, verificar no CI se o Opengrep 1.30.0 aceita a opção (a); o Semgrep tem `--exclude-rule`, mas **NÃO verifiquei que o Opengrep a aceita**. Se não aceitar, a opção (b) tem custo e risco maiores e a história pode ser cancelada.

## R7. Como o CI prova cada critério

| Critério | Prova | Job do CI (nomes reais do `protecao-main.json`) |
|---|---|---|
| FR-008, FR-010 (JUnit) | testes novos em `audit-api/src/test/java/.../pack` | `API, CLI e pacotes de regras` |
| FR-008 (YAML das ferramentas) | carregamento dos pacotes embutidos com `side` | `API, CLI e pacotes de regras` |
| FR-009 (tela) | testes `vitest` do ranking e dos relatórios | `Dashboard` |
| FR-009 (API) | teste do `SystemViews.summary` | `API, CLI e pacotes de regras` |
| SC-002, SC-005 (sem regressão) | calibração e benchmark existentes | `Calibração em vários repositórios`, `OWASP Benchmark (recall e precisão)` |

## R8. Versão e registro

- **Fato**: PRs de funcionalidade anteriores subiram a versão (`scripts/sincronizar-versao.py`, versão atual 12.84.0) e acrescentaram uma entrada ao `docs/evolucao/LOG.md` com *Verificado* e *NÃO verificado* (entradas 12.48.0 e 12.49.0).
- **Decisão**: cada PR do plano segue esse padrão. **NÃO verificado**: a regra exata de quando subir versão menor ou de correção; o João confirma no PR.
