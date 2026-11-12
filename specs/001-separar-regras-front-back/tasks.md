# Tarefas: Escopo front-end e back-end, o que falta fechar

**Entrada**: documentos de `/specs/001-separar-regras-front-back/` (`plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md`)
**Pré-requisitos**: `plan.md` e `spec.md` (existem)

**Testes**: obrigatórios. A constituição (princípio II) manda escrever teste junto com todo recurso novo e sobrepõe o "opcional" do modelo de tarefas do Spec Kit. Em cada história, os testes vêm **antes** da implementação e devem falhar primeiro.

**Verificação**: a sessão do Claude na nuvem não roda Maven nem Docker. Testes Java e do dashboard são provados pelo CI do PR; cada PR só é mesclado com CI verde e com a autorização do João. Onde uma tarefa diz "CI", é o CI que fecha a tarefa.

**Formato**: `- [ ] [ID] [P?] [História] Descrição com caminho de arquivo`. `[P]` = pode rodar em paralelo (arquivos diferentes, sem dependência de tarefa incompleta).

**Convenção de caminhos** (a partir da raiz do repositório): `API` = `audit-api/src/main/java/com/sentinel/audit`, `TESTE` = `audit-api/src/test/java/com/sentinel/audit`, `PACOTES` = `audit-rules/packs`, `DASH` = `audit-dashboard/src`.

**Ordem e PRs**: as histórias 2 e 3 (ambas do PR A) vêm antes da história 1 (PR B) porque o PR A é menor e sem migração de banco. Prioridades: US1 = P1, US2 = P1, US3 = P2, US4 = P3.

---

## Fase 1: Preparação

- [x] T001 Criar a branch `feat/escopo-lado-ferramentas` a partir da `main` atualizada, para o PR A
- [x] T002 [P] Ler `TESTE/pack/ToolSpecParserTest.java`, `TESTE/analyzer/SarifAnalyzerTest.java` e `TESTE/pack/RuleSideTest.java` para seguir o estilo dos testes existentes (leitura, sem alterar arquivos)

---

## Fase 2: Fundação (bloqueia as histórias)

Sem pré-requisitos bloqueantes: cada história altera arquivos próprios, e o `RuleSide` já existe e é reaproveitado. Seguir direto para a Fase 3.

---

## Fase 3: História 2 — Ferramentas externas acompanham o lado desligado (P1) — PR A

**Objetivo**: ferramenta externa de lado desligado não roda nem gera achados, e o resumo da auditoria diz o motivo (FR-008, SC-001).

**Teste independente**: com `disableSides = {front}`, uma ferramenta `side: front` não roda e aparece como "lado front-end desligado"; uma `side: common` roda (cenário 2 do `quickstart.md`).

### Testes da história 2 (escrever primeiro; devem falhar)

- [x] T003 [P] [US2] Em `TESTE/pack/ToolSpecParserTest.java`, testar a chave `side`: aceita `back`, `front` e `common`; valor fora disso gera erro de carregamento com a mesma redação das outras chaves inválidas; sem `side`, deduz de `applies-to` com `RuleSide.infer(List.of("regex"), appliesTo)`
- [x] T004 [P] [US2] Em `TESTE/analyzer/SarifAnalyzerTest.java`, testar com política que desliga `front`: ferramenta `side: front` não é executada e o resumo registra "lado front-end desligado"; ferramenta `side: common` executa; achados de uma ferramenta de nome `jscpd` em arquivo `.scss` são descartados e os de `.java` ficam
- [x] T005 [P] [US2] Criar `TESTE/pack/MarketToolsSideTest.java` (usando `TestPacks.registry()` como em `RuleSideTest`) afirmando o lado final das ferramentas embutidas do `research.md` R3: `njsscan` back; `squawk`, `sqlfluff` e `jscpd` common; `stylelint` e `dependency-cruiser` front; `checkstyle` e `joern-taint` back; `checkov`, `hadolint`, `kube-linter`, `kube-score` e `spectral` common

### Implementação da história 2

- [x] T006 [US2] Em `API/pack/ToolSpec.java`, acrescentar o componente `RuleSide side` ao record, depois de `appliesTo`
- [x] T007 [US2] Em `API/pack/ToolSpecParser.java`, acrescentar `"side"` à lista de chaves permitidas (linha 38), ler com `RuleSide.parse`, registrar erro para valor inválido e usar `RuleSide.infer(List.of("regex"), applies)` quando ausente
- [x] T008 [US2] Ajustar todas as chamadas de `new ToolSpec(` no código e nos testes (buscar com `grep -rn "new ToolSpec(" audit-api/src`) para passar o lado novo
- [x] T009 [US2] Em `API/pack/AuditPolicy.java`, criar `toolOffNote(ToolSpec)` no mesmo molde de `analyzerOffNote` (texto: `lado front-end desligado no escopo deste sistema` ou `lado back-end ...`) e incluir o nome `"jscpd"` em `SIDE_BY_FILE_TOOLS`, para o filtro por arquivo de `inDisabledSide` valer para ele (o `toolSource` das ferramentas SARIF é o campo `name` do YAML, ver `research.md` R4)
- [x] T010 [US2] Em `API/analyzer/SarifAnalyzer.java` (`analisarPacote`, linha ~101) e `API/analyzer/SarifResumo.java`, pular a ferramenta quando `policy.toolOffNote(spec)` não for nulo e registrar no resumo a linha `<nome>: <motivo>`; vale também para ferramentas em modo `input`
- [x] T011 [P] [US2] Acrescentar `side:` nos YAMLs: `side: back` ao `njsscan` em `PACOTES/market-tools/tools/javascript.yml`; `side: common` ao `squawk` e ao `sqlfluff` em `PACOTES/market-tools/tools/sql.yml`; `side: common` ao `jscpd` em `PACOTES/market-tools/tools/duplicacao.yml`
- [x] T012 [P] [US2] Documentar a chave `side` das ferramentas (valores `back`, `front`, `common`; padrão deduzido de `applies-to`) em `docs/referencia/pacotes-de-regras.md`, seguindo `contracts/pacote-ferramentas.md`

**Ponto de verificação**: T003 a T005 passam no job `API, CLI e pacotes de regras` do CI.

---

## Fase 4: História 3 — Regra de código esquecida como "comum" é barrada por teste (P2) — PR A

**Objetivo**: regra de código sem `metadata.side` que a dedução classifique como comum sem ser infraestrutura faz o teste falhar com o id da regra (FR-010, SC-004).

**Teste independente**: uma regra de exemplo com `languages: [java, typescript]` e sem `side` faz o teste de guarda falhar; com `metadata.side: back` passa (cenário 3 do `quickstart.md`).

### Testes da história 3 (escrever primeiro)

- [x] T013 [P] [US3] Em `TESTE/pack/RuleSideTest.java`, escrever o teste de guarda: ler cada regra dos pacotes embutidos com `RuleYaml.rules(...)`; se não tiver `metadata.side` explícito e `RuleSide.infer(languages, includes)` der `COMMON` sem ser infraestrutura, falhar listando os ids; incluir um caso com regra sintética mista (falha) e uma de Docker/Kubernetes (passa sem declarar)

### Implementação da história 3

- [x] T014 [US3] Em `API/pack/RuleSide.java`, expor um método público que diga se linguagens e caminhos indicam só infraestrutura (Dockerfile, Compose, Kubernetes, Helm, IaC), reaproveitando o `INFRA` e a regra de `dockerfile` já existentes, para o teste T013 e o `RuleSide.infer` não duplicarem a lógica
- [x] T015 [US3] Rodar o teste de guarda no CI e, para cada regra de código que ele apontar (o LOG 12.48.0 cita 3 regras de qualidade comuns a Java e TypeScript), acrescentar `metadata.side: common` (ou o lado correto) no YAML do pacote dela em `PACOTES/*/rules/*.yml`; as regras de infraestrutura não precisam de mudança
- [x] T016 [P] [US3] Avaliar se o mesmo guarda cabe em `scripts/verificar-regras.py` (roda sem Maven); se couber, acrescentar lá com um teste em `scripts/test_verificar_regras.py`; se não couber, registrar no LOG que o guarda só roda no CI

---

## Fase 5: Fechamento do PR A

- [x] T017 Acrescentar a entrada do PR A em `docs/evolucao/LOG.md` (o que mudou, **Verificado** e **NÃO verificado**) e subir a versão com `python3 scripts/sincronizar-versao.py`, seguindo as entradas 12.48.0 e 12.49.0
- [x] T018 Rodar `python3 scripts/conferir-numeros.py`, `python3 scripts/sincronizar-versao.py --check` e `python3 scripts/verificar-separacao.py`; abrir o PR A com o modelo `docs/pull_request_template.md`; esperar o CI verde; mesclar **somente** com a autorização do João

---

## Fase 6: História 1 — Selo no ranking e nos relatórios (P1) — PR B

**Objetivo**: o Ranking de Sistemas e os relatórios mostram, ao lado da nota, o lado desligado na auditoria que gerou a nota, sem mudar o cálculo (FR-009, SC-002, SC-003).

**Teste independente**: desligar o front, auditar e abrir o ranking e o relatório: aparece "front-end desligado"; com os dois lados ligados, nenhum selo; ajuste mudado depois da auditoria não muda o selo (cenário 1 do `quickstart.md`).

**Dependência**: começa depois do PR A mesclado, em nova branch `feat/selo-lado-desligado` a partir da `main`.

### Testes da história 1 (escrever primeiro; devem falhar)

- [x] T019 [P] [US1] Em `TESTE/service/AuditRunStoreTest.java`, testar que a auditoria grava `disabledSides` no início: `"front"` quando só o front estava desligado, `""` quando os dois estavam ligados; e que o valor não muda depois
- [x] T020 [P] [US1] Criar `TESTE/resource/SystemViewsTest.java` testando `summary()`: `disabledSides` vem da última auditoria **concluída**; vazio quando o valor da auditoria é `NULL` (anterior à funcionalidade); não muda quando o ajuste do sistema é alterado depois da auditoria; `lastAuditScore` idêntico ao de antes
- [x] T021 [P] [US1] Em `TESTE/service/ReportServiceTest.java`, `TESTE/service/PdfReportServiceTest.java` e `TESTE/service/PortfolioReportServiceTest.java`, testar que o selo "front-end desligado" (ou "back-end desligado") aparece ao lado da nota quando há lado desligado e não aparece quando não há
- [x] T022 [P] [US1] Criar `DASH/pages/Ranking/RankingPage.test.tsx` (vitest + Testing Library, como os demais testes do dashboard) testando que a linha do sistema mostra o selo quando `disabledSides` tem `front` ou `back` e não mostra quando está vazio

### Implementação da história 1

- [x] T023 [US1] Criar a migração `audit-api/src/main/resources/db/migration/V6__lados_desligados_na_auditoria.sql` que acrescenta a `public.audit_run` a coluna `disabled_sides` do tipo `VARCHAR(20)`, **anulável e sem valor padrão** (`NULL` = auditoria anterior à funcionalidade, escopo desconhecido); valores gravados: `"back"`, `"front"` ou `""` (quando os dois lados estavam ligados)
- [x] T024 [US1] Em `API/model/AuditRun.java`, mapear o campo `disabledSides` à coluna `disabled_sides` (`@Column(name = "disabled_sides", length = 20)`) com getter e setter
- [x] T025 [US1] Em `API/service/AuditOrchestrationService.java`, quando a auditoria começa, gravar em `AuditRun.disabledSides` os lados desligados do ajuste usado (o mesmo `SystemTuning` que `API/service/AuditPolicyResolver.java` aplica), ordenados e separados por vírgula; gravar uma vez, nunca atualizar depois
- [x] T026 [US1] Em `API/dto/SystemSummaryResponse.java`, acrescentar `List<String> disabledSides` ao final do record e preencher em `API/resource/SystemViews.java` (`summary()`, a partir de `lastRun`): lista vazia e nunca nula quando `lastRun` não existe, quando o valor é `NULL` ou quando é vazio; segundo `contracts/api-sistemas.md`
- [x] T027 [US1] Em `API/resource/ReportResource.java` (rota `/portfolio`), ler como cada `SystemSnapshot` é montado e acrescentar `disabledSides` a `SystemSnapshot` em `API/service/PortfolioReportService.java` (ainda **não verificado** se reaproveita `SystemViews.summary`, ver `research.md` R5)
- [x] T028 [US1] Em `API/service/ReportService.java` e `API/service/PdfReportService.java`, mostrar o selo ao lado da nota; em `PortfolioReportService`, mostrar o selo por sistema; texto do selo: "front-end desligado" ou "back-end desligado" (tabela de `data-model.md`)
- [x] T029 [P] [US1] Em `DASH/types/index.ts`, acrescentar `disabledSides: string[]` ao tipo do resumo do sistema e, em `DASH/pages/Ranking/RankingPage.tsx`, mostrar o selo ao lado da nota usando o visual atual (tag do Carbon já usada na página)
- [x] T030 [P] [US1] Documentar o campo `disabledSides` em `docs/referencia/auditoria-e-api.md` e conferir se o OpenAPI gerado o inclui

### Fechamento do PR B

- [x] T031 [US1] Entrada do PR B em `docs/evolucao/LOG.md` (**Verificado** / **NÃO verificado**), subir a versão com `scripts/sincronizar-versao.py`, apontar no corpo do PR a migração V6 e a mudança aditiva da API; abrir o PR com `docs/pull_request_template.md`, esperar o CI verde (`API, CLI e pacotes de regras`, `Dashboard`) e mesclar **somente** com a autorização do João

---

## Fase 7: História 4 — Opengrep sem as regras do lado desligado (P3, opcional) — PR C

**Objetivo**: não executar as regras do lado desligado no Opengrep, com os mesmos achados de hoje (FR-011).

**Dependência**: só se a verificação abaixo mostrar um caminho seguro; caso contrário, cancelar a história.

- [-] T032 (cancelada: ver research.md R6) [US4] Verificar na documentação oficial do Opengrep 1.30.0 (https://github.com/opengrep/opengrep) se existe opção para excluir regras por id (no Semgrep é `--exclude-rule`; **não confirmado** para o Opengrep) e registrar o resultado em `specs/001-separar-regras-front-back/research.md` R6
- [-] T033 (cancelada: ver research.md R6) [US4] Apresentar ao João o resultado de T032 e decidir ir ou não ir; se for, seguir T034 a T036 em nova branch `feat/opengrep-sem-lado-desligado`
- [-] T034 (cancelada: ver research.md R6) [P] [US4] Em `TESTE/analyzer/` (teste de `SemgrepAnalyzer`), testar que `buildCommand`/`resolveConfigs` com lado desligado não passam as regras do lado, e que o conjunto de achados final é igual ao do filtro posterior de `AuditPolicy.apply`
- [-] T035 (cancelada: ver research.md R6) [US4] Em `API/analyzer/SemgrepAnalyzer.java` (`resolveConfigs` e `buildCommand`), excluir as regras do lado desligado usando o catálogo `RuleCatalog.of(policy.packs())` e o `SystemTuning` da política
- [-] T036 (cancelada: ver research.md R6) [US4] Medir antes e depois o tempo e os achados de uma auditoria com um lado desligado (job `Calibração em vários repositórios` e `OWASP Benchmark (recall e precisão)` sem queda), entrada no `docs/evolucao/LOG.md`, PR C e mesclagem **somente** com autorização do João

---

## Fase 8: Acabamento

- [x] T037 [P] Atualizar `specs/001-separar-regras-front-back/spec.md` (status e requisitos FR-008 a FR-011 marcados como atendidos) e `docs/sdd.md`, se o fluxo ensinado mudou
- [ ] T038 Rodar `/speckit-analyze` sobre `spec.md`, `plan.md` e `tasks.md` antes de começar a implementar, para pegar inconsistências
- [ ] T039 Executar os cenários do `quickstart.md` numa instalação real (Docker na máquina do João) e registrar o resultado, já que a tela só se verifica com navegador real

---

## Dependências e ordem de execução

- **Fase 1** → **Fase 3 e 4** (mesma branch, PR A) → **Fase 5** (fecha o PR A) → **Fase 6** (PR B, depois do A mesclado) → **Fase 7** (PR C, opcional) → **Fase 8**.
- **Dentro de cada história**: testes primeiro (devem falhar), depois modelos, serviços, telas, documentação.
- **Entre histórias**: US2 e US3 compartilham o PR A e arquivos diferentes (exceto `RuleSide.java`, tocado só por T014). US1 é independente de US2/US3 no código, mas usa a mesma versão e o mesmo `LOG.md`, por isso espera o PR A mesclado. US4 depende apenas de T032/T033.
- **Dependências de tarefas**: T006 → T007 → T008 → T009 → T010; T014 → T013 (o teste usa o método); T023 → T024 → T025 → T026 → T027/T028/T029.

### Oportunidades de paralelismo

- **US2, testes**: T003, T004 e T005 em paralelo (arquivos diferentes).
- **US2, implementação**: T011 e T012 em paralelo entre si e com T006 a T010.
- **US1, testes**: T019, T020, T021 e T022 em paralelo.
- **US1, implementação**: T029 e T030 em paralelo com a parte Java (T024 a T028).

---

## Estratégia de implementação

1. **MVP**: PR A (US2 + US3) entrega o interruptor completo nas ferramentas externas e o guarda; é pequeno, sem migração de banco, e fecha a lacuna que deixa achados de front aparecerem num projeto só Java.
2. **Incremento**: PR B (US1) entrega o selo; é o maior, por causa da migração V6 e da mudança aditiva da API.
3. **Opcional**: PR C (US4) só depois da decisão do João sobre T032.
4. **Verificação**: nenhum PR é mesclado sem CI verde e sem a autorização do João; o que só se verifica com Docker e navegador (T039) fica explicitamente como **NÃO verificado** na entrada do `LOG.md` até ser feito.
5. **Registro**: toda entrada do LOG traz **Verificado** e **NÃO verificado**, e a spec é atualizada no mesmo PR se o código divergir dela.
