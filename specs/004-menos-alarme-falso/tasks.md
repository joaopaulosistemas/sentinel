---

description: "Tarefas da spec 004: Sentinel Premium 2 — Menos alarme falso e só o novo no PR"
---

# Tarefas: Sentinel Premium 2 — Menos alarme falso e só o novo no PR

**Entrada**: `specs/004-menos-alarme-falso/` (plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md)
**Testes**: **obrigatórios** (princípio II): cada fatia traz os testes **antes** do código.
**Organização**: por história; as fatias do plano (A a F) são os PRs. **Nenhuma tarefa roda sem o João mandar implementar** (regime atual: só especificação).

## Formato: `[ID] [P?] [História] Descrição com caminho`

- **[P]**: paralelizável (arquivos diferentes, sem dependência pendente)
- **[USn]**: US1 só o novo no PR, US2 ruído conhecido sai com prova, US3 agrupamento, US4 confiança por achado
- ⚠️ **Autorização**: o João precisa liberar antes (API pública ou `.github/`)
- Toda fatia termina com entrada em `docs/evolucao/LOG.md` (Verificado / NÃO verificado) e, se mudar comportamento, versão nova (`audit-dashboard/package.json` + `package-lock.json` linhas 3 e 9 + `python3 -I scripts/sincronizar-versao.py`). Toda chave nova nasce **desligada** (FR-011).

## Notas da análise (2026-11-13)

C1: chaves por sistema, não em `application.properties`. C2: T037 cobre o SARIF. C3: T038 empacota o JSON. C5: sem a régua da 003 o "fica" é manual. C6: caminhos resolvidos.

## Mapa de fatias

| Fatia | Fase | PR |
|---|---|---|
| A | 1 e 2 | Base do PR: severidade, contagem, renomeação, decisão por arquivo |
| B | 3 (US1) | `PrClassifier` e comentário só com novo/piorado |
| C | 4 (US2) | Gerado não segurança fora da nota |
| D | 5 (US4) | `precisao-por-regra.json` e `ruleBand` |
| E | 6 (US3) | Agrupamento e `groupBy` ⚠️ |
| F | 7 (US2) | Verificação de rebaixe por precisão |
| G | 8 | Fechamento |

---

## Fase 1: Preparação (fatia A)

- [ ] T001 Criar fixtures de teste em `audit-api/src/test/resources/pr/` (base e PR com: achado antigo, novo, severidade subida, ocorrência duplicada, trecho movido, reformatação, arquivo renomeado)
- [ ] T002 [P] Registrar as três chaves **por sistema** (princípio V), desligadas por padrão, em `.sentinel.yml` lido por `ProjectConfig`: `pr.classify`, `generated.exclude-non-security`, `rule-band`; teste de que, sem a chave, o valor é `false`, em `audit-api/src/test/java/com/sentinel/audit/service/ProjectConfigTest.java` (C1). Nada vai para `application.properties`

## Fase 2: Fundação (bloqueia as histórias; fatia A)

- [ ] T003 [P] Testes de `ChangedFiles` com `git diff --name-status -M`: renomeação devolve par antigo→novo; arquivo apagado e adicionado não vira par em `audit-api/src/test/java/com/sentinel/audit/service/ChangedFilesTest.java`
- [ ] T004 [P] Testes de `Baseline`: guarda severidade por fingerprint e contagem por (regra, arquivo); base sem `commitSha` é indecidível em `audit-api/src/test/java/com/sentinel/audit/service/FindingLifecycleServiceTest.java`
- [ ] T005 [P] Testes da decisão por arquivo (D2): arquivo alterado no branch base depois da auditoria vira INDECIDIDO; sem auditoria base, todos INDECIDIDO em `audit-api/src/test/java/com/sentinel/audit/service/BaseDecisionTest.java`
- [ ] T006 Estender `ChangedFiles` com renomeação (D3) em `audit-api/src/main/java/com/sentinel/audit/service/ChangedFiles.java`
- [ ] T007 Estender `Baseline` com `severityByFingerprint` e `countByRuleFile` (D4) em `audit-api/src/main/java/com/sentinel/audit/service/FindingLifecycleService.java`
- [ ] T008 Implementar a decisão por arquivo (D2) em `audit-api/src/main/java/com/sentinel/audit/service/BaseDecision.java`

**Checkpoint**: testes das fatias A verdes; com as chaves desligadas nada muda.

---

## Fase 3: História 1 — O PR mostra só o que ele introduziu (P1) 🎯 MVP (fatia B)

**Teste independente**: roteiros 1 a 3 do `quickstart.md`.

- [ ] T009 [P] [US1] Testes de `PrClassifier`: NOVO, PIORADO (severidade e excedente), EXISTENTE, INDECIDIDO; movido/reformatado/renomeado não é novo (SC-001, SC-002) em `audit-api/src/test/java/com/sentinel/audit/service/PrClassifierTest.java`
- [ ] T010 [P] [US1] Teste do comentário (`contracts/comentario-pr.md`): ordem das partes, linha "N achados antigos fora deste comentário", aviso de base, nunca omitir segurança nova (FR-003, FR-012) em `audit-cli/src/test/java/com/sentinel/audit/cli/MarkdownWriterPrCommentTest.java`
- [ ] T011 [P] [US1] Teste de regressão: com a chave desligada, comentário e relatório idênticos aos de hoje, byte a byte (FR-011) no mesmo arquivo
- [ ] T012 [US1] Implementar `PrClassifier` (D1 a D4) em `audit-api/src/main/java/com/sentinel/audit/service/PrClassifier.java`
- [ ] T013 [US1] Ligar o classificador ao `--pr-comment` atrás da chave em `audit-cli/src/main/java/com/sentinel/audit/cli/MarkdownWriter.java`
- [ ] T037 [US1] Aplicar o mesmo corte ao arquivo SARIF (as anotações do PR no GitHub, `--sarif` do pipeline): com `pr.classify` ligado, só novos e piorados entram; teste antes em `audit-cli/src/test/java/com/sentinel/audit/cli/SarifWriterPrTest.java`, implementação em `audit-cli/src/main/java/com/sentinel/audit/cli/SarifWriter.java` (FR-001, FR-004; C2)
- [ ] T014 [US1] Garantir que achados antigos seguem no painel e no relatório completo (FR-004) com teste em `audit-api/src/test/java/com/sentinel/audit/service/FindingRefinementTest.java`

## Fase 4: História 2 — Ruído conhecido sai, com prova (P1) (fatia C)

- [ ] T015 [P] [US2] Testes da exclusão `GENERATED_NON_SECURITY`: estilo, qualidade, manutenção e desempenho saem; segurança fica; contagem e motivo no relatório (FR-005) em `audit-api/src/test/java/com/sentinel/audit/service/GeneratedExclusionTest.java`
- [ ] T016 [US2] Implementar a exclusão reaproveitando `GeneratedFiles`, `PathRole.GENERATED` e a categoria do catálogo, atrás da chave (D5)
- [ ] T017 [US2] Teste de que a nota de segurança não muda com a chave ligada

## Fase 5: História 4 — Cada achado diz quão confiável é (P2) (fatia D)

- [ ] T018 [P] [US4] Testes de `scripts/precisao-regras.py --json`: Wilson 95 %, faixas 80/50, n < 20 e regra corrigida depois da amostra → SEM_MEDIDA em `scripts/test_precisao_regras.py`
- [ ] T019 [US4] Implementar `--json` e gerar `audit-rules/calibracao/precisao-por-regra.json` (D6)
- [ ] T038 [US4] Empacotar `audit-rules/calibracao/precisao-por-regra.json` junto do catálogo de regras e testar que a API o encontra na imagem (C3)
- [ ] T020 [P] [US4] Testes de `RuleBandService`: regra ausente = SEM_MEDIDA; campo separado de `confidence` em `audit-api/src/test/java/com/sentinel/audit/service/RuleBandServiceTest.java`
- [ ] T021 [US4] Implementar `RuleBandService` e expor `ruleBand`/`ruleBandSample` atrás da chave ⚠️ **API pública: autorização do João**
- [ ] T022 [US4] Mostrar a faixa na tabela do comentário do PR (`contracts/comentario-pr.md`)

## Fase 6: História 3 — Achados repetidos viram um só (P2) (fatia E)

- [ ] T023 [P] [US3] Testes de agrupamento: chave regra + trecho normalizado; contagem e locais; nota idêntica com e sem agrupar (SC-007) em `audit-api/src/test/java/com/sentinel/audit/service/FindingGroupingTest.java`
- [ ] T024 [US3] Implementar `GrupoDeAchados` na leitura em `audit-api/src/main/java/com/sentinel/audit/service/FindingGrouping.java`
- [ ] T025 [US3] ⚠️ **Autorização**: parâmetro `groupBy=cause` na listagem e interruptor no painel (`audit-dashboard/`)

## Fase 7: Rebaixe por precisão (fatia F, US2)

- [ ] T026 [P] [US2] Testes de `precisao-regras.py --verifica`: `shadow: true` novo sem comentário `# precisao.md: k VP de n` falha; número divergente de `precisao.md` falha; regra corrigida depois da amostra falha (FR-006, SC-006)
- [ ] T027 [US2] Implementar a verificação em `scripts/precisao-regras.py`
- [ ] T028 ⚠️ **Autorização**: se `scripts/test_precisao_regras.py` for novo, acrescentá-lo à linha de testes de scripts em `.github/workflows/ci.yml`

## Fase 8: Fechamento (fatia G)

- [ ] T029 Rodar gabarito e OWASP Benchmark com chaves ligadas e desligadas; registrar recall (SC-003, trava −0,02) em `docs/evolucao/LOG.md`
- [ ] T030 Registrar o veredito manual por redução (SC-004) e a medida em código real (SC-005); nenhuma chave vira padrão sem "fica"
- [ ] T031 Atualizar `docs/referencia/pacotes-de-regras.md` (seção "Auditoria de PR") e `docs/evolucao/precisao.md`
- [ ] T032 Conferir `conferir-numeros.py`, `sincronizar-versao.py --check` e o roteiro completo do `quickstart.md`

## Dependências

Fase 2 antes de tudo. US1 (fatia B) depende de A (T037 depende de T012). T038 vem antes de T021. US4 (D) é independente de B, mas T022 depende de B. US3 (E) e fatia F são independentes entre si. G por último.
