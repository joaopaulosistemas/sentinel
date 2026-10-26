# Claude (MCP) consultando o Sentinel

O Sentinel é um servidor MCP: o Claude Code (ou outro cliente MCP) lê o resultado das auditorias de **um sistema**, usando a chave **desse** sistema.

**Princípio:** o motor de regras é a única fonte da nota. O Claude consulta, sugere e propõe, sempre fora da nota. Tudo é configurado por sistema, e o que escreve vem desligado.

## Ligar para um sistema

1. Dashboard → **Sistemas** → **Editar** → aba **Claude (MCP)**.
2. **Gerar chave** e copiar na hora (ela não aparece de novo; o Sentinel guarda só o hash).
3. Marcar **Permitir consulta pelo Claude (MCP)**.
4. Escolher **o que o Claude pode ver** e quais ferramentas ficam liberadas.

Sem a chave, com o acesso desligado ou com a chave de outro sistema, a resposta é sempre a mesma `401`, exista o sistema ou não.

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

O trecho de código, a descrição do achado e a recomendação gerada por IA **não saem em nenhum nível**. Achados de segredo nunca levam o valor. O sistema `antifraude-engine` é fixo em "sensível".

Isto controla o que o **Sentinel entrega**. O Claude Code, rodando no computador de quem o usa, lê a pasta do projeto por conta própria:
quem não pode expor o código de um sistema a um modelo precisa tratar isso como regra de uso, não só como configuração. Antes de subir
um sistema de produção para "arquivo e linha", confirme a política da empresa e o plano em uso.

## Ferramentas

Sem escolha na aba, só as de leitura ficam ligadas. As de escrita precisam ser liberadas uma a uma.

| Ferramenta | Tipo | O que faz |
| --- | --- | --- |
| `status_auditoria` | leitura | Nota geral e por dimensão, achados por severidade, branch, commit e data da última auditoria concluída |
| `buscar_achados` | leitura | Achados abertos, do mais grave ao menos grave (filtros: `severidade`, `categoria`, `somenteNovos`, `limite` até 50) |
| `diff_auditoria` | leitura | Variação da nota, achados novos e resolvidos desde a auditoria anterior |
| `o_que_corrigir_primeiro` | leitura | Problemas por regra, ordenados pelo ganho de nota calculado pelo próprio Sentinel |
| `status_pedidos` | leitura | Pedidos do Claude e o resultado de cada um |
| `registrar_sugestao` | escrita | Guarda uma sugestão (título, detalhe, regra/arquivo/linha) na aba Revisão do Claude. Uma pessoa marca como tratada ou dispensa |
| `propor_regra` | escrita | Propõe uma regra Semgrep (uma só, id `local-<id>`). Entra no funil abaixo |
| `iniciar_auditoria` | escrita | Cria um **pedido** para auditar o sistema numa branch. Nada roda até uma pessoa aprovar |
| `suprimir_achado` | escrita | Cria um pedido para suprimir um achado como **falso positivo**. Só vale para achado da última auditoria concluída |

Cada chamada fica registrada na aba (quando, ferramenta, resultado, tempo). O resultado em si não é guardado.

## Revisão do Claude: sugestões e regras

As duas primeiras ferramentas de escrita só gravam em **Informações de Auditoria → Revisão do Claude**. Não criam achado, não mudam regra do motor e não entram na nota. O quadro no topo da aba mostra **quanto o Claude acerta**: sugestões aproveitadas (tratadas ÷ tratadas + dispensadas) e regras promovidas (promovidas ÷ promovidas + descartadas), medidas pelo que as pessoas decidiram (`GET /api/v1/claude-review/{id}/metrics`). Sem decisão, a taxa aparece como "sem decisão ainda".

### Funil de uma regra proposta

1. **Proposta** — validada na entrada: formato, um único casador, sem `fix`, expressão regular sem repetição aninhada.
2. **Revisada** por uma pessoa (botão na aba). Até aqui a regra **não roda**.
3. **Teste de amostra** — feito fora do Sentinel (por exemplo `semgrep --test` pelo Claude Code); a pessoa registra passou/falhou.
4. **Observação** — a regra roda nas auditorias do sistema em **modo sombra**: aparece, mas não pesa na nota. Falso positivo = achado suprimido como falso positivo.
5. **Promovida** (pesa na nota) ou **descartada**. Para promover: pelo menos uma auditoria depois do início, 5 achados ou mais e precisão de 90% ou mais (`sentinel.review.min-hits`, `sentinel.review.min-precision`).

A regra nasce valendo só no sistema onde foi proposta. **Copiar para outro sistema** a coloca em observação lá; depois de promovida lá, a original fica "pronta para o pacote compartilhado". Entrar no pacote é uma mudança no repositório dos pacotes, feita por uma pessoa. Achado de julgamento (sem regra que uma máquina verifique) continua como sugestão, fora da nota para sempre.

## Reauditoria e pedidos com aprovação

`iniciar_auditoria` e `suprimir_achado` **nunca executam**: só criam um pedido pendente. Quem aprova ou recusa é uma pessoa, em **Informações de Auditoria → Reauditoria**, onde também ficam "Reauditar agora" e o antes e depois (nota, resolvidos, novos, persistentes).

- Cada pedido vale 1 hora; no máximo 5 pendentes por sistema; pedido repetido é recusado.
- A branch é validada (sem `-` no início, sem `..`).
- O texto do pedido é montado pelo Sentinel, não copiado do Claude.
- Supressão pedida pelo Claude só pode ser falso positivo e expira em 90 dias.
- Apagar o sistema apaga os pedidos.

## Limites de uso

- **Chamadas:** 60 por minuto por sistema com chave válida (`sentinel.mcp.rate-per-minute`). Passou disso, a resposta é `429` com `Retry-After`.
- **Chave errada:** 10 tentativas por minuto por nome de sistema (`sentinel.mcp.failed-per-minute`); depois, `429` mesmo para a chave certa até a janela passar. O custo é que quem erra a chave de propósito pode travar o sistema por um minuto; a resposta de chave errada continua idêntica exista o sistema ou não.
- O limite fica em memória e zera quando a API reinicia.

## Ajustes do motor por sistema

Cada sistema pode ajustar o motor por cima do perfil dele (abas **Motor de regras** e **Analisadores** em Sistemas → Editar). O que o Claude vê pelo MCP já reflete esses ajustes, porque a nota e os achados vêm do mesmo motor. O Claude não altera esses ajustes.

## Detalhes técnicos

- `POST /mcp/{sistema}`, JSON-RPC 2.0, uma mensagem por requisição. Sem sessão e sem fluxo SSE (`GET` responde `405`).
- A rota não usa a `API_KEY` global; o nginx do dashboard encaminha `/mcp/` à API sem acrescentá-la.
- Configuração do acesso: `/api/v1/mcp-access/{sistemaId}` (sob a `API_KEY` global, como o resto de `/api/v1`).
- Tabelas criadas pelo Hibernate (`update`) na primeira subida: `mcp_access`, `mcp_call_log`, `review_suggestion`, `rule_proposal`, `pending_action` e `system_engine_config` (ajuste do motor). Apagar o sistema apaga todas.
- Arquitetura: `docs/arquitetura/ARQUITETURA.md`, seção 3.11.
