# Contrato: resumo do sistema (API)

Mudança aditiva em um contrato público: segue a regra da constituição de perguntar antes de mudar contrato público da API. O campo é novo e opcional, então clientes antigos continuam funcionando; mesmo assim, o PR deve apontar a mudança no corpo e o OpenAPI precisa refleti-la.

## `GET /api/v1/systems` e `GET /api/v1/systems/{id}`

Resposta: `SystemSummaryResponse` (lista ou objeto). Campo novo:

```json
{
  "id": 7,
  "name": "meu-servico",
  "lastAuditScore": 71.4,
  "disabledSides": ["front"]
}
```

| Campo | Tipo | Observação |
|---|---|---|
| `disabledSides` | array de string (`"back"`, `"front"`) | Nunca nulo; `[]` quando nada está desligado. Ordem alfabética. Nunca contém os dois valores. |

Invariantes:
- `lastAuditScore` e os demais campos têm o mesmo valor que tinham antes da mudança.
- O campo reflete o ajuste atual do sistema, não o da última auditoria. Um sistema auditado com o front ligado e depois alterado para desligado mostra o selo até a próxima auditoria; o PR deve decidir se o selo compara com o ajuste usado na auditoria (ver pergunta aberta abaixo).

## Pergunta aberta para as tarefas

O selo mostra o ajuste **atual** ou o **da última auditoria concluída**? A nota exibida é da última auditoria, então o ajuste atual pode não corresponder a ela. O mais honesto é guardar o escopo usado na auditoria; isso exigiria um campo novo em `AuditRun`. Decidir antes de implementar a história 1, com o João.
