# Modelo de dados (Fase 1): escopo front-end e back-end

`disableSides` já fica no JSON de ajustes do sistema (`SystemEngineConfig`, tabela existente). Muda: uma coluna nova e anulável em `audit_run` (abaixo), modelos em memória, o YAML dos pacotes e dois DTOs.

## Auditoria (`AuditRun`)

Arquivo: `audit-api/src/main/java/com/sentinel/audit/model/AuditRun.java`. Migração nova `V6__lados_desligados_na_auditoria.sql` em `audit-api/src/main/resources/db/migration/`.

| Campo | Tipo | Regra |
|---|---|---|
| `disabledSides` (coluna `disabled_sides`) | `VARCHAR(20)`, anulável | Lados desligados no ajuste do sistema quando a auditoria começou: `"back"`, `"front"` ou vazio (`""`) quando ambos estavam ligados. `NULL` = auditoria anterior a esta mudança (escopo desconhecido) |

- Gravado uma vez, quando a auditoria começa (o valor vem de `tuning.disableSides()` já usado para montar a política); nunca é atualizado depois.
- Coluna anulável e sem valor padrão: a migração não reescreve linhas existentes e é reversível (basta ignorar a coluna).

## Ferramenta externa de pacote (`ToolSpec`)

Arquivo: `audit-api/src/main/java/com/sentinel/audit/pack/ToolSpec.java` (record). Ganha um componente.

| Campo | Tipo | Regra |
|---|---|---|
| `side` | `RuleSide` (`BACK`, `FRONT`, `COMMON`) | Vem de `side:` no YAML; se faltar, `RuleSide.infer(["regex"], appliesTo)`; se `side:` for inválido, o carregamento do pacote registra erro como as outras chaves inválidas de `ToolSpecParser` |

Regras de validação:
- A chave `side` entra na lista de chaves permitidas de `ToolSpecParser` (hoje: `id`, `name`, `command`, `input`, `category`, `timeout-seconds`, `severity-map`, `stdout`, `applies-to`).
- Valores aceitos: `back`, `front`, `common` (os mesmos de `RuleSide.parse`).
- Ferramenta em modo `input` (só lê SARIF existente) também tem lado.

## Ajuste do sistema (`SystemTuning`)

Sem mudança de estrutura. `disableSides` continua sendo `Set<String>` com `back` e/ou `front`, nunca os dois (validado em `TuningJson`).

## Resumo do sistema (`SystemSummaryResponse`)

| Campo novo | Tipo | Valor |
|---|---|---|
| `disabledSides` | `List<String>` (ordenada) | Escopo da **última auditoria concluída** (`AuditRun.disabledSides`); lista vazia quando os dois lados estavam ligados ou quando o escopo é desconhecido (`NULL`) |

O `SystemSnapshot` do relatório de portfólio (`PortfolioReportService`) recebe o mesmo campo.

Compatível para trás: campo novo e opcional no JSON; o dashboard antigo o ignora.

## Selo (apresentação, não é dado guardado)

| Entrada (`disabledSides`) | Texto do selo |
|---|---|
| `[]` | nenhum |
| `["front"]` | front-end desligado |
| `["back"]` | back-end desligado |

## Resumo da auditoria (`SarifResumo`)

Cada ferramenta externa pulada por lado desligado vira uma linha no resumo: `"<nome>: lado front-end desligado no escopo deste sistema"` (mesma redação de `AuditPolicy.analyzerOffNote`).
