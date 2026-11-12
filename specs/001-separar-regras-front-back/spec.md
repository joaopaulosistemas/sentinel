# Especificação: Separar as regras em front-end e back-end

**Branch da funcionalidade**: `001-separar-regras-front-back`
**Criada em**: 2026-11-12
**Status**: Rascunho (3 pontos em aberto, ver "Esclarecimentos pendentes")
**Origem**: pedido do João: separar as regras do Sentinel em front-end e back-end no backend, refletir isso na tela Motor de regras, e permitir que um projeto só Java desligue tudo de front (e o contrário). Há um protótipo fiel da tela, feito como canvas de design; a implementação aguarda o "vá" dele.

> Esta spec é um exemplo vivo do fluxo: foi escrita só com o que o João já disse e com o que o repositório mostra. O que ninguém decidiu ainda está marcado como `[NEEDS CLARIFICATION]` e deve ser resolvido com `/speckit-clarify` antes do plano.

## Cenários de usuário e testes *(obrigatório)*

### História 1 — Desligar todo o front-end num projeto só Java (Prioridade: P1)

Quem cadastra um sistema que é só back-end (por exemplo, Spring Boot sem interface) quer um interruptor único para não rodar nenhuma regra de front-end, em vez de desmarcar regra por regra ou pacote por pacote.

**Por que P1**: é o motivo do pedido. Sem isso o resto é só organização visual.

**Teste independente**: cadastrar um sistema, desligar o grupo "front-end" e auditar; nenhum achado de regra de front-end aparece e as regras de back-end continuam rodando.

**Cenários de aceite**:

1. **Dado** um sistema com o grupo front-end desligado, **quando** ele é auditado, **então** nenhuma regra classificada como front-end é executada nem gera achado.
2. **Dado** um sistema com o grupo back-end desligado e o front-end ligado, **quando** ele é auditado, **então** o inverso acontece.
3. **Dado** um sistema com os dois grupos ligados (situação de hoje), **quando** ele é auditado, **então** o resultado é idêntico ao de antes desta funcionalidade.

---

### História 2 — Ver e entender a separação na tela Motor de regras (Prioridade: P1)

Quem abre o Motor de regras de um sistema vê as regras organizadas em front-end e back-end, hoje misturadas, e entende de qual grupo cada regra é.

**Por que P1**: sem a tela o interruptor da história 1 seria invisível; o João já aprovou o protótipo como referência visual.

**Teste independente**: abrir a tela de um sistema e conferir que cada regra aparece em exatamente um grupo, com contagem por grupo.

**Cenários de aceite**:

1. **Dado** o Motor de regras de um sistema, **quando** a tela abre, **então** as regras aparecem separadas em front-end e back-end, com a contagem de cada grupo e o estado (ligado/desligado) do grupo.
2. **Dado** uma regra de back-end, **quando** o usuário filtra por front-end, **então** ela não aparece.
3. **Dado** a tela atual do sistema, **quando** a separação entra, **então** o visual atual é mantido (a mudança é a organização, não um redesenho).

---

### História 3 — Regras novas entram já classificadas, sem mexer em código (Prioridade: P2)

Quem adiciona uma regra nova, ou um pacote novo, define em qual grupo ela entra, sem recompilar.

**Por que P2**: o Sentinel é plug-and-play (Constituição, princípio V); uma separação que exigisse código a cada regra quebraria isso.

**Teste independente**: adicionar uma regra de exemplo em um pacote, indicando o grupo, e vê-la no grupo certo na tela, sem build.

**Cenários de aceite**:

1. **Dado** uma regra nova com o grupo informado, **quando** o pacote é carregado, **então** ela aparece no grupo informado.
2. **Dado** uma regra nova sem grupo informado, **quando** o pacote é carregado, **então** `[NEEDS CLARIFICATION: o que acontece? falha ao carregar, vai para um grupo padrão ou é inferida pela linguagem/caminho?]`.

---

### Casos de borda

- Hoje a divisão por pacote não serve: `core-frontend` declara `languages: [java, javascript, typescript]` e traz regras de Angular e de acessibilidade ao lado de "consulta em laço / N+1", que é de back-end; `core-security`, `core-quality` e `core-boas-praticas` também misturam Java e TypeScript. A classificação precisa ser **por regra**, não por pacote.
- Regras de Docker, Kubernetes, SQL e IaC não são nem front nem back `[NEEDS CLARIFICATION: viram um terceiro grupo (infraestrutura/dados) ou ficam ligadas sempre?]`.
- Ferramentas de mercado via SARIF (`market-tools`) rodam sobre vários tipos de arquivo e não são regras individuais do Sentinel `[NEEDS CLARIFICATION: o interruptor também desliga as ferramentas de mercado de cada lado, por exemplo ESLint e Stylelint no front e PMD no back?]`.
- Sistemas já cadastrados antes da funcionalidade devem continuar com tudo ligado, sem ação do usuário.
- A nota de risco de um sistema com um grupo desligado não pode ser comparada diretamente com a de um sistema completo `[NEEDS CLARIFICATION: o ranking de sistemas deve indicar que um grupo está desligado?]`.

## Requisitos *(obrigatório)*

### Requisitos funcionais

- **FR-001**: Cada regra do motor DEVE pertencer a exatamente um grupo de tecnologia, dentre front-end e back-end (mais o que o esclarecimento sobre infraestrutura decidir).
- **FR-002**: O usuário DEVE poder ligar e desligar cada grupo por sistema, no cadastro do sistema (Constituição, princípio V), sem afetar outros sistemas.
- **FR-003**: Um grupo desligado DEVE impedir a execução e o aparecimento de achados das suas regras na auditoria desse sistema.
- **FR-004**: A tela Motor de regras DEVE mostrar as regras separadas por grupo, com contagem e estado do grupo, mantendo o visual atual.
- **FR-005**: O grupo de uma regra DEVE ser declarado na própria definição da regra, de modo que regras e pacotes novos entrem sem alterar código.
- **FR-006**: Sistemas existentes DEVEM continuar com todos os grupos ligados e produzir o mesmo resultado de antes.
- **FR-007**: Regras que o usuário adiciona por sistema DEVEM poder ser colocadas em um dos grupos.

### Entidades principais

- **Regra**: uma verificação do motor; passa a ter um grupo de tecnologia.
- **Grupo de tecnologia**: front-end ou back-end (e, se decidido, outros); pode ser ligado ou desligado por sistema.
- **Sistema**: o projeto auditado; guarda o estado de cada grupo.

## Critérios de sucesso *(obrigatório)*

### Resultados mensuráveis

- **SC-001**: Em um sistema só Java com o front-end desligado, a auditoria produz zero achados de regras de front-end e executa zero dessas regras.
- **SC-002**: Em um sistema com os dois grupos ligados, o conjunto de achados fica idêntico ao da versão anterior (nenhuma regressão).
- **SC-003**: 100 % das regras existentes ficam classificadas em um grupo, e um teste automático barra regra nova sem grupo, a menos que o esclarecimento 2 decida outro comportamento para esse caso.
- **SC-004**: Os testes de precisão e calibração existentes (OWASP Benchmark, `calibracao.sh`) continuam passando sem queda, já que nada na detecção deve mudar.

## Premissas

- A separação não altera o que cada regra detecta; muda só a organização e a possibilidade de desligar por grupo.
- O protótipo da tela já aprovado pelo João é a referência visual.
- A verificação roda no CI do GitHub (a sessão do Claude na nuvem não roda Maven nem Docker), então cada critério acima precisa virar teste que o CI executa.

## Esclarecimentos pendentes

1. Regras de infraestrutura e dados (Docker, Kubernetes, SQL, IaC) formam um terceiro grupo ou ficam fora do interruptor?
2. Regra sem grupo: falha, usa padrão ou é inferida?
3. O interruptor também controla as ferramentas de mercado de cada lado?
