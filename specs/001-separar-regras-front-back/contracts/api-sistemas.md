# Contrato: resumo do sistema (API)

Mudança aditiva em um contrato público: um campo novo e opcional, então clientes antigos continuam funcionando. O PR B deve apontar a mudança no corpo e o OpenAPI precisa refleti-la. Aceita pelo João por delegação em 2026-11-12 ("escolha a melhor").

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
| `disabledSides` | array de string (`"back"`, `"front"`) | Nunca nulo. Ordem alfabética. Nunca contém os dois valores. `[]` quando a última auditoria concluída rodou com os dois lados ligados, quando o sistema não tem auditoria concluída ou quando ela é anterior à funcionalidade (escopo desconhecido). |

Invariantes:
- `lastAuditScore` e os demais campos têm o mesmo valor que tinham antes da mudança.
- O campo reflete o escopo **usado na última auditoria concluída**, o mesmo conjunto de achados que gerou a nota exibida. Mudar o ajuste do sistema depois não muda o selo até a próxima auditoria concluir.
- Auditoria concluída antes desta mudança não mostra selo (escopo desconhecido), mesmo que o ajuste atual tenha um lado desligado.

## Relatórios

Os relatórios de um sistema (HTML/PDF) e o de portfólio mostram o selo a partir do mesmo valor: o escopo da auditoria que originou a nota do relatório.
