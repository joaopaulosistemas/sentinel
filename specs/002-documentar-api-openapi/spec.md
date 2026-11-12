# Especificação: Documentar toda a API REST do Sentinel com OpenAPI/Swagger

**Ramo**: `spec/002-documentar-api-openapi` | **Criada**: 2026-11-12 | **Status**: Rascunho
**Pedido**: "criar uma spec para swagger, criar para todos os resources do sentinel"

## Linha de base: o que já existe (levantado no código em 2026-11-12)

- Há 22 classes com `@Path` e 70 rotas. 21 classes têm `@Tag` e 67 das 70 rotas têm `@Operation` (resumo/descrição).
- A única classe sem documentação é `McpResource` (`/mcp/{system}`, 3 rotas).
- Não existe nenhum `@APIResponse`, `@Parameter` ou `@Schema`: o Swagger mostra o caminho e o resumo, mas não diz que respostas e erros cada rota devolve, nem o que significa cada parâmetro, nem o formato dos corpos.
- O esquema de segurança não está declarado no OpenAPI, embora a API aceite a chave em `X-API-Key` ou `Authorization: Bearer` (`ApiKeyFilter`). Com isso, o botão "Authorize" do Swagger UI não existe e testar rotas protegidas ali não funciona.
- O erro padrão (`ApiErrorMapper`) não aparece no contrato.
- O Swagger UI (`/swagger-ui`) só fica ativo em modo dev (`docs/referencia/auditoria-e-api.md`); o documento OpenAPI não é conferido pelo CI.

## Cenários de usuário e testes *(obrigatório)*

### História 1 — Quem integra consegue usar qualquer rota só lendo o Swagger (Prioridade: P1)

Uma pessoa que integra o Sentinel (CI de outro time, script, o próprio dashboard) abre o Swagger e, para qualquer rota, vê: o que ela faz, cada parâmetro (caminho, query, cabeçalho) com significado e exemplo, o formato do corpo de entrada e de saída, e todas as respostas possíveis, incluindo erros.

**Por que P1**: é o objetivo do pedido; hoje a descrição para na primeira linha.

**Teste independente**: abrir o documento OpenAPI gerado e conferir, rota por rota, que nenhuma ficou sem resumo, sem resposta de sucesso descrita e sem as respostas de erro que ela realmente devolve.

**Cenários de aceite**:

1. **Dado** o documento OpenAPI gerado, **quando** listo as 70 rotas, **então** todas têm resumo, descrição, tag e ao menos uma resposta de sucesso com o tipo do corpo.
2. **Dado** uma rota com parâmetro de caminho ou query, **quando** a abro no Swagger, **então** cada parâmetro tem descrição e, quando há conjunto fechado de valores, a lista deles.
3. **Dado** uma rota que valida entrada ou busca um sistema/auditoria pelo id, **quando** a abro, **então** constam 400 e 404 (e os demais códigos que o código realmente devolve) com o formato do erro padrão.

---

### História 2 — Testar rotas protegidas direto no Swagger (Prioridade: P1)

Com a chave de API configurada, a pessoa clica em "Authorize", informa a chave uma vez e todas as chamadas do Swagger UI passam a enviá-la.

**Por que P1**: sem isso o Swagger só serve para ler; com a chave ligada (padrão de produção) as chamadas dão 401.

**Teste independente**: documento OpenAPI declara o esquema de chave de API e as rotas protegidas o exigem; as rotas públicas (saúde, webhooks, MCP) aparecem sem exigência.

**Cenários de aceite**:

1. **Dado** o documento OpenAPI, **quando** leio os esquemas de segurança, **então** constam `X-API-Key` e `Bearer`.
2. **Dado** uma rota que o `ApiKeyFilter` deixa passar sem chave, **quando** a vejo no documento, **então** ela não exige segurança; todas as demais exigem.

---

### História 3 — O MCP e as rotas esquecidas entram no documento (Prioridade: P2)

`McpResource` (`/mcp/{system}`) e qualquer rota futura aparecem documentados e agrupados.

**Por que P2**: são 3 rotas hoje, mas sem a história 4 nada impede o esquecimento de voltar.

**Teste independente**: o documento não contém rota sem tag.

**Cenários de aceite**:

1. **Dado** o documento OpenAPI, **quando** procuro `/mcp/{system}`, **então** ele está lá com tag, descrição e o formato JSON-RPC descrito em palavras.

---

### História 4 — Um teste impede que rota nova fique sem documentação (Prioridade: P1)

Uma rota nova (ou alterada) sem resumo, tag, resposta de sucesso ou respostas de erro faz o build falhar, listando a rota. O mesmo vale se o documento deixar de ser gerado.

**Por que P1**: sem a trava, a cobertura apodrece (a constituição, princípio II, pede teste junto com o código).

**Teste independente**: teste Java sobre o documento gerado, com caso sintético que prova que ele falha quando uma rota fica sem documentação.

**Cenários de aceite**:

1. **Dado** o documento gerado pelo build, **quando** o teste roda, **então** passa com as 70 rotas atuais e falha listando `METODO /caminho` de qualquer rota incompleta.
2. **Dado** o documento publicado, **quando** o CI roda, **então** o arquivo OpenAPI é válido (estrutura e referências) e o CI mostra o número de rotas conferidas.

---

### História 5 — O contrato fica publicado sem precisar subir a API (Prioridade: P3)

O CI guarda o OpenAPI em arquivo (YAML) como artefato, e a documentação aponta para ele; o Swagger UI continua só em dev.

**Por que P3**: ajuda quem integra e quem revisa PR, mas não bloqueia as outras.

**Teste independente**: o artefato do CI abre num leitor de OpenAPI.

### Casos de borda

- Rota que devolve arquivo (PDF, SARIF, JUnit, CSV de exportação): resposta descrita com o tipo de mídia correto, não JSON.
- Rota com cabeçalho `Accept`/`Content-Type` curinga (`McpResource` consome qualquer tipo).
- Rotas que devolvem erro por outro caminho que não o `ApiErrorMapper` (por exemplo 401 do filtro, 429, 503): documentar só o que o código de fato produz.
- Campo sensível (token Git, chave): o exemplo do esquema nunca traz valor real.
- Mudança só de documentação nunca altera o comportamento nem o contrato de dados das rotas (nomes, tipos, códigos).

## Requisitos *(obrigatório)*

### Requisitos funcionais

- **FR-001**: Toda rota REST (70 hoje) deve ter resumo, descrição, tag, resposta de sucesso com tipo de corpo e mídia, e as respostas de erro que o código devolve.
- **FR-002**: Todo parâmetro (caminho, query, cabeçalho) deve ter descrição; valores de conjunto fechado listam as opções.
- **FR-003**: Os corpos de entrada e saída (DTOs) devem ter esquema com descrição por campo e exemplo, sem valores reais sensíveis.
- **FR-004**: O erro padrão da API deve constar no contrato e ser referenciado pelas respostas de erro.
- **FR-005**: O documento deve declarar o esquema de segurança (`X-API-Key` e `Bearer`) e marcar como públicas apenas as rotas que o filtro libera.
- **FR-006**: `McpResource` deve ser documentado e agrupado como as demais.
- **FR-007**: Um teste deve falhar listando a rota quando alguma ficar sem resumo, tag, resposta de sucesso ou respostas de erro; o teste prova a falha com caso sintético.
- **FR-008**: O CI deve validar o documento OpenAPI e publicá-lo como artefato.
- **FR-009**: Nenhuma mudança de comportamento: caminhos, métodos, códigos e corpos ficam como estão (o teste do contrato existente e a bateria Java continuam verdes).
- **FR-010**: `docs/referencia/auditoria-e-api.md` descreve como abrir o Swagger, autenticar, e onde está o arquivo gerado.

### Entidades principais

- **Rota**: método + caminho, com tag, resumo, descrição, parâmetros, respostas.
- **Esquema de dado (DTO)**: corpo de entrada/saída com campos descritos (21 arquivos em `dto`, além de modelos usados direto nas rotas).
- **Erro padrão**: formato devolvido por `ApiErrorMapper`.
- **Esquema de segurança**: chave em `X-API-Key` ou `Authorization: Bearer`.

## Critérios de sucesso *(obrigatório)*

### Resultados mensuráveis

- **SC-001**: 70 de 70 rotas com resumo, descrição, tag e resposta de sucesso tipada (hoje: 67 de 70 com resumo; 0 com resposta tipada).
- **SC-002**: 100 % das rotas que validam entrada ou buscam por id descrevem 400/404 (conferido pelo teste, não por leitura).
- **SC-003**: 100 % dos parâmetros com descrição.
- **SC-004**: o teste da FR-007 falha num caso sintético e passa no código real; uma rota nova sem documentação derruba o build.
- **SC-005**: o botão "Authorize" do Swagger UI aceita a chave e uma rota protegida responde 200 em modo dev com chave ligada.
- **SC-006**: zero diferença de comportamento: a bateria Java existente passa sem alteração de teste.

## Premissas

- O Swagger UI continua apenas em dev; produção não expõe a interface (decisão atual da documentação), mas o arquivo OpenAPI é gerado no build e publicado pelo CI.
- Documentar usa as anotações do MicroProfile OpenAPI que o Quarkus já traz (`quarkus-smallrye-openapi` está no projeto); nenhuma dependência nova.
- O dashboard e o CLI não mudam; nada aqui toca em `.github/` sem pedir antes (constituição, princípio IX). A publicação do artefato no CI (FR-008) é a única parte que exige alterar `.github/workflows/ci.yml` e só entra com autorização explícita do João.
- Texto em português, como o restante do projeto.
- Divisão sugerida em PRs: A (esquema de segurança, erro padrão, `McpResource`, teste-guarda) → B (respostas e parâmetros por grupo de resources) → C (esquemas dos DTOs) → D (CI e docs).
