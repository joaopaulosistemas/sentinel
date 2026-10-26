# Claude (MCP) consultando o Sentinel

O Sentinel é um servidor MCP: o Claude Code (ou outro cliente MCP) lê o resultado das auditorias de **um sistema**, usando a chave **desse** sistema.
**Lê, e só escreve na área Revisão do Claude (fase 4, desligado por padrão).** A nota continua sendo calculada apenas pelo motor de regras; nada que o Claude disser entra nela.

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

## Ferramentas (leitura ligada; escrita só se você liberar)

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
- Fora desta fase: ferramentas que mexem em auditoria ou achado (iniciar auditoria, suprimir achado), limite de chamadas por minuto e relatório pelo MCP.

## Ajustes do motor por sistema (fase 3)

Cada sistema pode ajustar o motor por cima do perfil dele (abas **Motor de regras** e **Analisadores** em Sistemas → Editar). O que o Claude vê pelo MCP já reflete esses ajustes, porque a nota e os achados vêm do mesmo motor. O Claude não altera esses ajustes.


## Revisão do Claude (fase 4)

Duas ferramentas gravam, **desligadas por padrão** (libere na aba Claude (MCP)). Elas só escrevem na aba **Informações de Auditoria → Revisão do Claude**; não criam achado, não mudam regra do motor e não entram na nota.

| Ferramenta | O que faz |
|---|---|
| `registrar_sugestao` | Guarda uma sugestão (título, detalhe, regra/arquivo/linha). Uma pessoa marca como tratada ou dispensa. |
| `propor_regra` | Propõe uma regra Semgrep (uma só, id `local-<id>`). Entra no funil abaixo. |

### Funil de uma regra proposta

1. **Proposta** — validada na entrada: formato, um único casador, sem `fix`, expressão regular sem repetição aninhada.
2. **Revisada** por uma pessoa (botão na aba). Até aqui a regra **não roda**.
3. **Teste de amostra** — feito fora do Sentinel (por exemplo `semgrep --test` pelo Claude Code); a pessoa registra passou/falhou.
4. **Observação** — a regra roda nas auditorias do sistema em **modo sombra**: aparece, mas não pesa na nota. Falso positivo = achado suprimido como falso positivo.
5. **Promovida** (pesa na nota) ou **descartada**. Para promover: pelo menos uma auditoria depois do início, 5 achados ou mais e precisão de 90% ou mais (`sentinel.review.min-hits`, `sentinel.review.min-precision`).

A regra nasce valendo só no sistema onde foi proposta. **Copiar para outro sistema** a coloca em observação lá; depois de promovida lá, a original fica "pronta para o pacote compartilhado". Entrar no pacote é uma mudança no repositório dos pacotes, feita por uma pessoa. Achado de julgamento (sem regra que uma máquina verifique) continua como sugestão, fora da nota para sempre.

## Reauditoria e pedidos (fase 5)

Três ferramentas novas. As duas que escrevem ficam **desligadas por padrão** e só funcionam depois de liberadas na aba Claude (MCP) do sistema.

| Ferramenta | Tipo | O que faz |
|---|---|---|
| `iniciar_auditoria` | escrita | Cria um **pedido** para auditar o sistema numa branch. Nada roda até uma pessoa aprovar. |
| `suprimir_achado` | escrita | Cria um pedido para suprimir um achado como **falso positivo**. Só vale para achado da última auditoria concluída do sistema. |
| `status_pedidos` | leitura | Lista os pedidos e o resultado de cada um. |

Regras: o Claude nunca executa; só cria o `PendingAction`. Cada pedido vale 1 hora, há no máximo 5 pendentes por sistema e pedido repetido é recusado. A branch é validada (sem `-` no início, sem `..`). O texto do pedido é montado pelo Sentinel, não copiado do Claude. A supressão pedida pelo Claude só pode ser falso positivo e expira em 90 dias. Quem aprova ou recusa é uma pessoa, em Informações de Auditoria → Reauditoria, onde também ficam "Reauditar agora" e o antes e depois (nota, resolvidos, novos, persistentes). A nota continua sendo calculada só pelo motor de regras.

## Limites e medição (fase 6)

- **Limite de chamadas:** 60 por minuto por sistema com chave válida (`sentinel.mcp.rate-per-minute`). Passou disso, a resposta é 429 com `Retry-After`.
- **Chave errada:** 10 tentativas por minuto por nome de sistema (`sentinel.mcp.failed-per-minute`); depois disso, 429 mesmo para a chave certa até a janela passar. O custo é que quem erra a chave de propósito pode travar o sistema por um minuto; a resposta de chave errada continua idêntica exista o sistema ou não. O limite fica em memória e zera ao reiniciar a API.
- **Quanto o Claude acerta:** `GET /api/v1/claude-review/{id}/metrics` e o quadro no topo da aba Revisão do Claude. Mostra sugestões aproveitadas (tratadas ÷ tratadas + dispensadas) e regras promovidas (promovidas ÷ promovidas + descartadas). Sem decisão ainda, a taxa aparece como "sem decisão ainda". Mede o julgamento das pessoas sobre o Claude e não entra na nota.
