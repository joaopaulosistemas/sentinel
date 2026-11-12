# Plano de implementação: documentar a API REST com OpenAPI/Swagger

**Spec**: [spec.md](spec.md) | **Constituição**: `.specify/memory/constitution.md`

## Abordagem

- Só documentação do contrato: nenhuma rota, código HTTP ou corpo muda (FR-009). Usa o `quarkus-smallrye-openapi` que já está no projeto; nenhuma dependência nova (VI).
- **Itens transversais num filtro** (`SentinelOpenApiFilter`, registrado em `mp.openapi.filter`): esquemas de segurança (`X-API-Key`, `Bearer`, chave MCP do sistema), os dois erros padrão (`ApiError` de `ApiErrorMapper`, `ApiKeyError` de `ApiKeyFilter`) e o 401 das rotas protegidas. Quais rotas são abertas vem de `ApiKeyFilter.isOpenPath`, a mesma fonte do comportamento real.
- **Itens de cada rota por anotação** (`@Operation`, `@APIResponse`, `@Parameter`) nos 22 resources; esquemas dos corpos por `@Schema` nos DTOs.
- **Trava**: teste de integração `OpenApiContractIntegrationTest` lê `/q/openapi` e falha listando `METODO /caminho` de rota sem resumo, tag, resposta ou 401. Ele aperta a cada PR (descrição, 400/404, parâmetros) até cobrir tudo.

## Fatias (PRs)

| PR | Conteúdo | Tarefas |
|---|---|---|
| A | Filtro (segurança, erros padrão, 401), `McpResource`, teste-guarda | T001–T008 |
| B | Respostas, códigos de erro e parâmetros por grupo de resources; guarda passa a exigir descrição e 400/404 | T009–T015 |
| C | Esquemas dos DTOs (descrição e exemplo por campo) | T016–T018 |
| D | Artefato do CI e docs (**mexe em `.github/`: só com autorização do João**) | T019–T021 |

## Riscos

- O filtro usa a API de modelos do MicroProfile OpenAPI; só o CI compila e prova (Maven não alcança o Maven Central no ambiente de desenvolvimento).
- A descrição de uma resposta de erro só entra quando o código realmente a devolve; ler o resource antes de escrever.
