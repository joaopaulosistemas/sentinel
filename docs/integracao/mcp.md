# Claude (MCP) consultando o Sentinel

O Sentinel é um servidor MCP: o Claude Code (ou outro cliente MCP) lê o resultado das auditorias de **um sistema**, usando a chave **desse** sistema.
**Só lê.** A nota continua sendo calculada apenas pelo motor de regras; nada que o Claude disser entra nela.

## Ligar para um sistema

1. Dashboard → **Sistemas** → **Editar** → aba **Claude (MCP)**.
2. **Gerar chave** e copiar na hora (ela não aparece de novo; o Sentinel guarda só o hash).
3. Marcar **Permitir consulta pelo Claude (MCP)**.
4. Escolher **o que o Claude pode ver** e quais ferramentas ficam liberadas.

Sem a chave, com o acesso desligado ou com a chave de outro sistema, a resposta é sempre a mesma `401`.

## Conectar o Claude Code

```
claude mcp add --transport http sentinel-conta-corrente-api https://SEU-SENTINEL/mcp/conta-corrente-api --header "Authorization: Bearer snt_..."
```

A aba mostra esse comando já com a chave recém-gerada. O endereço é `/mcp/<nome do sistema>` (também vale o número do sistema).

## O que o Claude vê

| Nível | Títulos | Arquivo e linha | Contagens |
| --- | --- | --- | --- |
| Só números e títulos (padrão) | sim | não | sim |
| Arquivo e linha | sim | sim | sim |
| Sensível | não | não | só por regra e severidade |

O trecho de código, a descrição do achado e a recomendação gerada por IA **não saem em nenhum nível**. Achados de segredo nunca levam o valor.

Isto controla o que o **Sentinel entrega**. O Claude Code, rodando no computador de quem o usa, lê a pasta do projeto por conta própria:
quem não pode expor o código de um sistema a um modelo precisa tratar isso como regra de uso, não só como configuração. Antes de subir
um sistema de produção para "arquivo e linha", confirme a política da empresa e o plano em uso.

## Ferramentas (fase 2: todas só leem)

| Ferramenta | O que devolve |
| --- | --- |
| `status_auditoria` | Nota geral e por dimensão, achados por severidade, branch, commit e data da última auditoria concluída |
| `buscar_achados` | Achados abertos, do mais grave ao menos grave (filtros: `severidade`, `categoria`, `somenteNovos`, `limite` até 50) |
| `diff_auditoria` | Variação da nota, achados novos e resolvidos desde a auditoria anterior |
| `o_que_corrigir_primeiro` | Problemas por regra, ordenados pelo ganho de nota calculado pelo próprio Sentinel |

Cada chamada fica registrada na aba (quando, ferramenta, resultado, tempo). O resultado em si não é guardado.

## Detalhes técnicos

- `POST /mcp/{sistema}`, JSON-RPC 2.0, uma mensagem por requisição. Sem sessão e sem fluxo SSE (`GET` responde `405`).
- A rota não usa a `API_KEY` global; o nginx do dashboard encaminha `/mcp/` à API sem acrescentá-la.
- Configuração do acesso: `/api/v1/mcp-access/{sistemaId}` (sob a `API_KEY` global, como o resto de `/api/v1`).
- Tabelas novas `mcp_access` e `mcp_call_log`, criadas pelo Hibernate (`update`) na primeira subida. Apagar o sistema apaga as duas.
- Fora desta fase: ferramentas que escrevem (iniciar auditoria, suprimir achado, propor regra), limite de chamadas por minuto e relatório pelo MCP.
