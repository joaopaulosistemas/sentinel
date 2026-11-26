# Banco de dados

O Sentinel usa PostgreSQL 16. O esquema é do Flyway (`audit-api/src/main/resources/db/migration`, V1 a V7) e o Hibernate não altera nada (`DB_SCHEMA_MODE=none`).

**Diagrama:** abra [banco-de-dados.html](banco-de-dados.html) no navegador (arquivo único, sem dependências). Ele mostra as 12 tabelas, as colunas principais e as relações.

## Tabelas

| Tabela | Para quê |
|---|---|
| `audited_system` | Sistema auditado: origem do código, perfil, agenda e notificações. Tokens e segredos ficam cifrados (`*_enc`) |
| `audit_run` | Uma execução de auditoria: pontuações, status e quais lados (regras/ferramentas) rodaram (`disabled_sides`) |
| `finding` | Achado de uma auditoria. O `fingerprint` liga o mesmo achado entre execuções |
| `audit_progress` | Progresso ao vivo da execução em andamento (uma linha por execução, descartável) |
| `finding_suppression` | Decisão de suprimir um achado, por `fingerprint` |
| `mcp_access` · `mcp_call_log` | Acesso do servidor MCP ao sistema (só o hash da chave) e registro das chamadas |
| `pending_action` | Ação pedida por um agente via MCP que espera aprovação humana |
| `review_suggestion` · `rule_proposal` | Sugestões de revisão e regras Semgrep propostas |
| `system_engine_config` | Configuração do motor por sistema (JSON) |
| `app_setting` | Configurações globais (chave e valor) |

## Relações

Só três têm chave estrangeira de verdade: `audit_run.system_id`, `finding.audit_run_id` e `finding_suppression.system_id`. As outras nove (`system_id` de MCP, regras, sugestões e configuração; `audit_progress.audit_run_id`; `pending_action.result_audit_run_id`; `rule_proposal.copied_from_id`) são relações lógicas, sem FK. No diagrama aparecem tracejadas. Transformá-las em FK exige uma migração e checar antes se há linhas órfãs.

## Datas e fuso

As colunas de data são `timestamptz` (V5) e a API grava em UTC. O painel converte para o fuso do navegador e os relatórios HTML/PDF usam `REPORT_TIMEZONE` (padrão `America/Sao_Paulo`).

## Migrações

| Versão | O que faz |
|---|---|
| V1 | Baseline: cria as 12 tabelas |
| V2 | Tira checagens antigas de bancos criados pelo Hibernate |
| V3 | Remove o índice redundante `idx_suppression_system` |
| V4 | Completa bancos antigos (o que faltava em relação ao V1) |
| V5 | Datas passam para `timestamptz` |
| V6 | Cria `audit_run.disabled_sides` |
| V7 | Cria `finding.code_context` e `finding.code_context_start` (linhas reais do arquivo ao redor do achado; NULL em achados antigos e em segredos) |

Banco novo roda o V1 e segue até o último. Banco que já existia (criado pelo Hibernate) entra em baseline na versão 1 e recebe as demais. **Não escreva uma migração já aplicada**: o Flyway recusa o start se o arquivo mudar. Para alterar, crie a próxima versão. O CI confere que o esquema depois da subida é igual ao das migrações (`scripts/conferir-esquema.py`).

Backup e restauração: [operação](../guia/operacao.md).
