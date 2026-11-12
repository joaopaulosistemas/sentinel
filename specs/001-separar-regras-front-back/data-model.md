# Modelo de dados (Fase 1): escopo front-end e back-end

Nada muda no banco de dados: `disableSides` já fica no JSON de ajustes do sistema (`SystemEngineConfig`, tabela existente). As mudanças são em modelos em memória, no YAML dos pacotes e em um DTO.

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
| `disabledSides` | `List<String>` (ordenada) | `tuning.disableSides()` do sistema; lista vazia quando nenhum lado está desligado |

Compatível para trás: campo novo e opcional no JSON; o dashboard antigo o ignora.

## Selo (apresentação, não é dado guardado)

| Entrada (`disabledSides`) | Texto do selo |
|---|---|
| `[]` | nenhum |
| `["front"]` | front-end desligado |
| `["back"]` | back-end desligado |

## Resumo da auditoria (`SarifResumo`)

Cada ferramenta externa pulada por lado desligado vira uma linha no resumo: `"<nome>: lado front-end desligado no escopo deste sistema"` (mesma redação de `AuditPolicy.analyzerOffNote`).
