# Claude (MCP) consultando o Sentinel

O Sentinel é um servidor MCP: o Claude Code (ou outro cliente MCP) lê o resultado das auditorias de **um sistema**, usando a chave **desse** sistema.

**Princípio:** o motor de regras é a única fonte da nota. O Claude consulta, sugere e propõe, sempre fora da nota. Tudo é configurado por sistema, e o que escreve vem desligado.

## A área Claude (MCP)

Tudo sobre o Claude consultando o Sentinel fica em um item de menu só: **Claude (MCP)** (`/claude`), com cinco abas. A aba aberta e o sistema escolhido ficam na URL (`?aba=conectar&systemId=2`), então dá para guardar ou mandar o link.

| Aba | Para que serve |
|---|---|
| **Visão geral** | Quantos sistemas estão conectados, quantas decisões esperam você e quantos estão recusando chamadas. Cada sistema mostra o selo (Conectado, Esperando, Recusando, Desligado, Falta conectar) e o botão que leva ao próximo passo. |
| **Conectar** | Escolhe o sistema e mostra o assistente de quatro passos abaixo. |
| **Decisões** | Uma caixa só com tudo o que espera uma pessoa, de todos os sistemas: pedidos do Claude (**Aprovar** ou **Recusar**), regras no funil (próximo passo de cada uma) e sugestões abertas (**Tratada** ou **Dispensar**). O que você decide **não some**: vai para **Decididas**, com o resultado e a hora, e dá para **desfazer** quando o Sentinel permite (tabela abaixo). Embaixo fica a **Revisão completa** de um sistema: métricas, funil com evidências e sugestões. O número de decisões esperando aparece na aba; o do menu conta os pedidos do Claude. |
| **Atividade** | As chamadas de todos os sistemas em uma lista, da mais recente para a mais antiga, com filtro por sistema e **Só recusadas**. Cada recusa traz o motivo e o que fazer, com o link para os passos. |
| **Permissões** | Escolhe o sistema e ajusta o que o Claude pode ver (nível de dados), as ferramentas liberadas e os parâmetros das chamadas. Gerar ou revogar a chave continua na aba Conectar. |

A aba **Claude (MCP)** de Sistemas → **Editar** só mostra a situação da ligação e leva a esta área, sem repetir os passos. Em **Informações de Auditoria**, a aba **Revisão do Claude** é um resumo (pedidos esperando, regras no funil, sugestões abertas e o próximo passo) com o link para a revisão completa; a **Reauditoria** só avisa quantos pedidos esperam. Uma regra no funil só aparece em **Decisões** quando há algo para a pessoa fazer: em observação, só quando já tem achados e precisão suficientes para promover. Nada daqui entra na nota até uma regra ser promovida por você.

### Desfazer uma decisão

| Decisão | Desfazer | Como |
|---|---|---|
| Sugestão tratada ou dispensada | Sim | **Reabrir** (`POST …/suggestions/{sid}/status` com `OPEN`) |
| Pedido recusado | Sim | **Reabrir** (`POST /api/v1/pending-actions/{id}/{actionId}/reopen`): volta a esperar, com 1 hora nova de validade, se a fila (5 pendentes) tiver lugar. Pedido que venceu sem decisão não volta |
| Pedido aprovado de **suprimir achado** | Sim | **Desfazer** (`…/undo`): remove a supressão e o achado volta a contar. O pedido continua aprovado, marcado como **Desfeito**, com a hora |
| Pedido aprovado de **iniciar auditoria** | Não | A auditoria já começou |
| Regra descartada | Sim | **Restaurar** (`POST /api/v1/claude-review/{id}/rules/{rid}/restore`): volta para a etapa em que estava (a observação recomeça do zero, porque a regra não rodou enquanto esteve descartada), se o sistema tiver lugar entre as 30 regras em andamento |
| Regra promovida | Não | Ela já pesa nas auditorias e na nota |

Regras descartadas e pedidos aprovados **antes** desta versão não guardam a etapa anterior nem a supressão criada: aparecem como decididos, sem botão, e a tela diz o motivo. O Sentinel não tem identidade de pessoa por usuário, então o registro guarda **quando** foi decidido ou desfeito, não **quem**.

## Ligar para um sistema

Na aba **Conectar** (ou em Sistemas → **Editar** → **Claude (MCP)**) há um assistente de quatro passos. O selo no topo diz em que ponto você está, e cada passo mostra se já foi feito:

1. **Gerar a chave** e copiar na hora (ela não aparece de novo; o Sentinel guarda só o hash).
2. **Ligar o acesso** (**Permitir consulta pelo Claude (MCP)**).
3. **Registrar no Claude Code:** escolher o sistema operacional, copiar o comando e colar no terminal.
4. **Conferir a conexão:** abrir o Claude Code e digitar `/mcp`. A tela confere sozinha e passa a mostrar **Conectado**.

Os **Ajustes** (o que o Claude pode ver e quais ferramentas ficam liberadas) ficam na aba **Permissões**.

Sem a chave, com o acesso desligado ou com a chave de outro sistema, a resposta HTTP é sempre a mesma `401`, exista o sistema ou não. Para quem está do lado de dentro, a tela explica o motivo: em um sistema que existe, cada recusa é anotada no registro de chamadas (ferramenta `acesso`) como `nenhuma chave gerada`, `chamada sem chave`, `chave diferente da vigente` ou `acesso desligado`. A chave apresentada nunca é gravada. A conexão aceita aparece como `conexão` (no máximo uma vez a cada 5 minutos), e é ela que vira o selo **Conectado**. Se nada aparece no registro, o endereço ou o número do sistema na URL não existe.

## Conectar o Claude Code

Você precisa do Claude Code instalado no computador. O comando que a aba monta, com o número do sistema:

```
claude mcp add --scope user --transport http sentinel-conta-corrente-api https://SEU-SENTINEL/mcp/7 --header "Authorization: Bearer snt_..."
```

- O endereço é `/mcp/<número do sistema>`. O número vale sempre; o nome também funciona, mas falha com acento, espaço ou nome repetido.
- `--scope user` registra para qualquer pasta. Sem ele, o registro vale só na pasta em que o comando foi rodado.
- `localhost` só funciona no computador onde o Sentinel roda. Em outro computador, use o endereço do servidor.
- Já registrou antes e deu erro? Rode `claude mcp remove <nome>` e registre de novo.
- Depois de conectar, peça por exemplo: "Use o sentinel para listar os achados críticos do último relatório."

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
| `buscar_achados` | leitura | Achados abertos, do mais grave ao menos grave (filtros: `severidade`, `categoria`, `confianca`, `somenteNovos`, `limite` até 50). Cada achado traz `confianca` (ALTA/MÉDIA/BAIXA em inglês: HIGH/MEDIUM/LOW) e `motivoConfianca` |
| `diff_auditoria` | leitura | Variação da nota, achados novos e resolvidos desde a auditoria anterior |
| `o_que_corrigir_primeiro` | leitura | Problemas por regra, ordenados pelo ganho de nota calculado pelo próprio Sentinel |
| `status_pedidos` | leitura | Pedidos do Claude e o resultado de cada um |
| `registrar_sugestao` | escrita | Guarda uma sugestão (título, detalhe, regra/arquivo/linha) na aba Revisão do Claude. Uma pessoa marca como tratada ou dispensa |
| `propor_regra` | escrita | Propõe uma regra Semgrep (uma só, id `local-<id>`). Entra no funil abaixo |
| `iniciar_auditoria` | escrita | Cria um **pedido** para auditar o sistema numa branch. Nada roda até uma pessoa aprovar |
| `suprimir_achado` | escrita | Cria um pedido para suprimir um achado como **falso positivo**. Só vale para achado da última auditoria concluída |

Cada chamada fica registrada na aba (quando, ferramenta, resultado, tempo). O resultado em si não é guardado.

## Acompanhar o Claude durante a auditoria

Na tela de execução da auditoria (`/audits/<id>`), enquanto ela roda, aparece a faixa **Claude (MCP)** com o estado deste sistema:

- **desligado** (com link **Configurar**, que abre a aba Claude (MCP) do sistema);
- **ligado, sem consultas nesta auditoria**;
- **ligado**, com o número de consultas desde o início da auditoria (e quantas foram recusadas), a última e a ferramenta usada.

Cada chamada do Claude também entra no quadro **Eventos** ("Claude (MCP) chamou `buscar_achados`"), na ordem em que aconteceu. Pedidos do Claude esperando aprovação aparecem como link para **Claude (MCP) → Decisões**.

É só informação: lê a configuração, as últimas chamadas e os pedidos pendentes, e não escreve nada. A nota continua vindo apenas do motor de regras. A faixa some quando a auditoria termina; o histórico completo fica na aba Claude (MCP) do sistema. Cada chamada guarda também os parâmetros recebidos (campo e valor curtos, até 60 caracteres; campos com `key`, `token`, `secret`, `senha`, `password` ou `authorization` no nome saem como `***`), mostrados na coluna Parâmetros. O MCP continua enxergando um sistema só, o da chave usada.

## Revisão do Claude: sugestões e regras

As duas primeiras ferramentas de escrita só gravam na **Revisão do Claude** (resumo em Informações de Auditoria; completa em **Claude (MCP) → Decisões**). Não criam achado, não mudam regra do motor e não entram na nota. O quadro no topo da revisão completa mostra **quanto o Claude acerta**: sugestões aproveitadas (tratadas ÷ tratadas + dispensadas) e regras promovidas (promovidas ÷ promovidas + descartadas), medidas pelo que as pessoas decidiram (`GET /api/v1/claude-review/{id}/metrics`). Sem decisão, a taxa aparece como "sem decisão ainda".

### Funil de uma regra proposta

1. **Proposta** — validada na entrada: formato, um único casador, sem `fix`, expressão regular sem repetição aninhada.
2. **Revisada** por uma pessoa (botão na revisão). Até aqui a regra **não roda**.
3. **Teste de amostra** — feito fora do Sentinel (por exemplo `semgrep --test` pelo Claude Code); a pessoa registra passou/falhou.
4. **Observação** — a regra roda nas auditorias do sistema em **modo sombra**: aparece, mas não pesa na nota. Falso positivo = achado suprimido como falso positivo.
5. **Promovida** (pesa na nota) ou **descartada**. Para promover: pelo menos uma auditoria depois do início, 5 achados ou mais e precisão de 90% ou mais (`sentinel.review.min-hits`, `sentinel.review.min-precision`).

A regra nasce valendo só no sistema onde foi proposta. **Copiar para outro sistema** a coloca em observação lá; depois de promovida lá, a original fica "pronta para o pacote compartilhado". Entrar no pacote é uma mudança no repositório dos pacotes, feita por uma pessoa. Achado de julgamento (sem regra que uma máquina verifique) continua como sugestão, fora da nota para sempre.

## Triagem em lote com o Claude

Peça ao Claude, na sua assinatura (sem API paga): "busque os achados de baixa confiança (`buscar_achados` com `confianca=LOW`), diga quais parecem falso positivo e peça a supressão". O Claude só enxerga título, regra, arquivo e o motivo da confiança, nunca o trecho de código; por isso a decisão dele é uma sugestão. Cada `suprimir_achado` vira um pedido pendente e uma pessoa aprova em **Claude (MCP) → Decisões**.

## Reauditoria e pedidos com aprovação

`iniciar_auditoria` e `suprimir_achado` **nunca executam**: só criam um pedido pendente. Quem aprova ou recusa é uma pessoa, em **Claude (MCP) → Decisões**. Em **Informações de Auditoria → Reauditoria** ficam "Reauditar agora" e o antes e depois (nota, resolvidos, novos, persistentes).

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
