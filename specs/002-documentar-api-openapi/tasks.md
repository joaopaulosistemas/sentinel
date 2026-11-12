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
- [x] T008 PR A (#243), CI verde, mergeado

## PR B — rotas e parâmetros

- [x] T009 [P] [US1] `Audits`: antes de escrever, levantar no código os códigos que cada rota devolve (inclusive 429/503/413 fora do `ApiErrorMapper`); `AuditResource`, `AuditAnalysisResource`, `AuditExportResource`, `AuditFindingsResource` (respostas, 400/404, parâmetros, tipos de mídia de PDF/SARIF/JUnit/CSV)
- [x] T010 [P] [US1] `Systems`: `SystemResource`, `SystemHistoryResource`, `SystemSourceResource`, `SystemEvidenceResource`
- [x] T011 [P] [US1] `Findings`: `FindingResource`, `FindingSuppressionResource`
- [x] T012 [P] [US1] `Motor, Packs, Rules, Guide`: `EngineConfigResource`, `PackResource`, `RuleResource`, `GuideResource`
- [x] T013 [P] [US1] `Relatórios, Revisão do Claude, Pedidos, MCP admin, Webhooks, Saúde, Configurações`
- [x] T014 [US1] [US4] Extrair a verificação do `OpenApiContractIntegrationTest` para `ApiContractCheck` (recebe o JSON do documento) e exigir: descrição em toda rota, resposta de sucesso com tipo de mídia (e esquema quando JSON), 400/404 com `content` apontando para `ApiError` nas rotas com parâmetro de caminho ou corpo, e descrição em todo parâmetro; `ApiContractCheckTest` (unitário) com documentos sintéticos que fazem cada regra falhar
- [ ] T015 LOG, versão, PR B, CI verde, merge com autorização

## PR C — esquemas dos DTOs

- [x] T016 `API/dto/*` e corpos declarados nos resources: `@Schema` com descrição por campo e exemplo (sem valor sensível real); DTOs de resposta ficam para depois
- [x] T017 [US1] `ApiContractCheck`: todo `schema` de corpo de requisição tem descrição por propriedade, e nenhum exemplo parece token ou chave real (`ghp_`, `glpat-`, `sk-`, chave longa em hexadecimal)
- [ ] T018 LOG, versão, PR C, CI verde, merge com autorização

## PR D — CI e docs (pede autorização: toca `.github/`)

- [x] T019 [US5] `.github/workflows/ci.yml`: publicar o OpenAPI do build como artefato e validar a estrutura
- [x] T020 `docs/referencia/auditoria-e-api.md` e `docs/guia/instalacao.md`: onde está o arquivo gerado
- [ ] T021 LOG, versão, PR D, CI verde, merge com autorização
- [ ] T022 (manual, do João) Abrir `/swagger-ui` em modo dev com chave ligada, clicar em Authorize, chamar uma rota protegida e ver 200 (SC-005)
