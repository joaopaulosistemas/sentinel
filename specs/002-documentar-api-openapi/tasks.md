# Tarefas: documentar a API REST com OpenAPI/Swagger

Formato: `- [ ] ID descrição com caminho`. Caminhos relativos a `audit-api/src/main/java/com/sentinel/audit` (API) e `audit-api/src/test/java/com/sentinel/audit` (TESTE).

## PR A — segurança, erros padrão, MCP e a trava

- [x] T001 `API/resource/SentinelOpenApiFilter.java`: esquemas `ApiKeyHeader`, `BearerKey`, `McpSystemKey`; schemas `ApiError` e `ApiKeyError`; exigência global; rotas abertas sem exigência; 401 nas protegidas
- [x] T002 `audit-api/src/main/resources/application.properties`: `mp.openapi.filter`
- [x] T003 `API/mcp/McpResource.java`: `@Tag`, `@Operation` e `@APIResponse` nas 3 rotas
- [x] T004 `TESTE/resource/SentinelOpenApiFilterTest.java` (unitário do filtro)
- [x] T005 `TESTE/integration/OpenApiContractIntegrationTest.java`: resumo, tag, resposta, 401, rotas abertas, MCP presente, mínimo de 70 rotas
- [x] T006 `docs/referencia/auditoria-e-api.md`: como autenticar no Swagger e o que o documento garante
- [x] T007 entrada no `docs/evolucao/LOG.md` e versão (`scripts/sincronizar-versao.py`)
- [ ] T008 PR A, CI verde, merge com autorização

## PR B — rotas e parâmetros

- [ ] T009 [P] `Audits`: `AuditResource`, `AuditAnalysisResource`, `AuditExportResource`, `AuditFindingsResource` (respostas, 400/404, parâmetros, tipos de mídia de PDF/SARIF/JUnit/CSV)
- [ ] T010 [P] `Systems`: `SystemResource`, `SystemHistoryResource`, `SystemSourceResource`, `SystemEvidenceResource`
- [ ] T011 [P] `Findings`: `FindingResource`, `FindingSuppressionResource`
- [ ] T012 [P] `Motor, Packs, Rules, Guide`: `EngineConfigResource`, `PackResource`, `RuleResource`, `GuideResource`
- [ ] T013 [P] `Relatórios, Revisão do Claude, Pedidos, MCP admin, Webhooks, Saúde, Configurações`
- [ ] T014 `OpenApiContractIntegrationTest`: exigir descrição em toda rota, 400/404 nas rotas com parâmetro de caminho ou corpo, e descrição em todo parâmetro
- [ ] T015 LOG, versão, PR B, CI verde, merge com autorização

## PR C — esquemas dos DTOs

- [ ] T016 `API/dto/*` e modelos usados direto nas rotas: `@Schema` com descrição por campo e exemplo (sem valor sensível real)
- [ ] T017 `OpenApiContractIntegrationTest`: todo `schema` de corpo tem descrição por propriedade
- [ ] T018 LOG, versão, PR C, CI verde, merge com autorização

## PR D — CI e docs (pede autorização: toca `.github/`)

- [ ] T019 `.github/workflows/ci.yml`: publicar o OpenAPI do build como artefato e validar a estrutura
- [ ] T020 `docs/referencia/auditoria-e-api.md` e `docs/guia/instalacao.md`: onde está o arquivo gerado
- [ ] T021 LOG, versão, PR D, CI verde, merge com autorização
