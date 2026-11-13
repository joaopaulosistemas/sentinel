# Especificação: Sentinel Premium 5 — Arquitetura verificável

**Ramo**: `spec/007-arquitetura-verificavel` | **Criada**: 2026-11-12 | **Status**: Esclarecida (4 perguntas, 2026-11-13); próximo: /speckit-plan
**Pedido**: "Sentinel Premium — Plano de Ação", eixo **profundidade**: o Sentinel deve descobrir a arquitetura que o código **realmente** tem, compará-la com a arquitetura que a equipe **declarou** e provar, com violações plantadas, que o verificador as acha. Depende das specs 003 e 005 (e usa a 004 para falar só do que é novo em PR).

## Linha de base: o que já existe (levantado no repositório em 2026-11-12)

- O pacote `core-architecture` e o analisador de política de arquitetura aplicam um arquivo `architecture.yml`: camadas e componentes, dependências proibidas entre camadas, convenções por arquivo, arquivos obrigatórios e política de versões de componentes. Há também um analisador de arquitetura em Java (camadas, entidade exposta em controlador).
- A dimensão de arquitetura pesa 20% da nota.
- A arquitetura é **declarada à mão**: o Sentinel não descobre a estrutura real, e por isso não mostra onde o código diverge do que a equipe acha que tem.
- Não há medida de quão bem o verificador acha violações: não existe teste com violações plantadas de propósito nem medida de falsos alarmes em projetos que respeitam a arquitetura.
- Não há acompanhamento da **erosão** ao longo do tempo (violações novas por PR, ciclos, acoplamento crescente).

## Esclarecimentos

### Sessão 2026-11-13

- Q: Como uma classe recebe camada quando a arquitetura não foi declarada? → A: pelas anotações e estereótipos do framework vindos do modelo estrutural (controller, serviço, repositório, entidade); classe sem sinal fica "sem camada", declarada como tal, nunca adivinhada.
- Q: A data de uma exceção declarada é de registro ou de validade? → A: de validade, obrigatória; vencida a exceção, a dependência volta a ser desvio; o relatório mostra a exceção vencida, a data de registro e a de validade.
- Q: Qual medida de acoplamento por módulo? → A: instabilidade de Martin (dependências que saem ÷ saem + entram, de 0 a 1) mais a contagem bruta de dependências entre módulos; definição fixa e registrada.
- Q: O que é violação nova, resolvida e persistente na tendência? → A: a violação é identificada pela regra violada mais o par origem→destino (classe, módulo ou camada), sem a linha; nova = não existia na auditoria anterior, resolvida = sumiu, persistente = continua.

## Cenários de usuário e testes *(obrigatório)*

### História 1 — Ver a arquitetura que o código realmente tem (Prioridade: P1)

O Sentinel mostra o mapa recuperado do código: módulos e camadas encontrados, dependências entre eles, pontos de entrada e acessos a dados, derivado do modelo estrutural (spec 005), sem que ninguém tenha declarado nada.

**Por que P1**: antes de verificar contra a meta, é preciso saber o que existe; e já ajuda sozinho a quem herda um sistema.

**Teste independente**: num repositório de teste com estrutura conhecida, o mapa recuperado bate com o gabarito de módulos e dependências.

**Cenários de aceite**:

1. **Dado** um projeto multimódulo, **quando** peço o mapa, **então** recebo módulos, camadas inferidas por anotação e estereótipo, classes "sem camada" listadas como tal e dependências entre eles, cada uma com evidência (arquivo e linha de um uso).
2. **Dado** uma dependência que o Sentinel não consegue resolver, **quando** vejo o mapa, **então** ela aparece como "não resolvida", não ausente.

---

### História 2 — Declarar a arquitetura-alvo e ver os desvios (Prioridade: P1)

A equipe declara a arquitetura que quer (camadas e quem pode depender de quem, módulos proibidos, ciclos proibidos). O Sentinel compara com o mapa real e lista cada desvio com a evidência do caminho (qual classe depende de qual, onde).

**Por que P1**: é a verificação em si; e passa a enxergar dependências indiretas, não só o que está escrito em um arquivo.

**Teste independente**: repositório de teste com uma violação direta e uma indireta (passando por uma classe intermediária): as duas aparecem com evidência.

**Cenários de aceite**:

1. **Dado** uma regra "a camada web não depende da camada de dados", **quando** uma classe web usa uma classe de dados diretamente, **então** o desvio aparece com a evidência.
2. **Dado** a mesma regra, **quando** a dependência passa por uma classe intermediária, **então** o desvio indireto aparece com o caminho completo.
3. **Dado** a arquitetura declarada no formato atual, **quando** o Sentinel audita, **então** as verificações de hoje continuam funcionando sem mudança.

---

### História 3 — Medir o verificador com violações plantadas (Prioridade: P1)

Num conjunto de teste, violações conhecidas são inseridas de propósito em código que respeita a arquitetura. O Sentinel deve achá-las; e em código limpo não deve acusar nada. Os dois números entram na régua da spec 003.

**Por que P1**: sem isso, "arquitetura verificável" é só uma frase.

**Teste independente**: o conjunto de teste produz acerto e alarme falso com intervalo e veredito.

**Cenários de aceite**:

1. **Dado** um repositório limpo com N violações plantadas, **quando** o verificador roda, **então** o relatório traz quantas foram achadas e quantos alarmes falsos apareceram.
2. **Dado** o veredito da spec 003, **quando** o verificador novo é comparado ao atual, **então** ele só substitui o atual se o veredito for "fica".

---

### História 4 — Erosão ao longo do tempo (Prioridade: P2)

O Sentinel acompanha, por auditoria e por PR, as violações novas, os ciclos entre módulos e o acoplamento, para mostrar se a arquitetura está melhorando ou piorando. Em PR, só o que o PR introduziu (spec 004).

**Por que P2**: transforma o verificador em acompanhamento contínuo; depende de histórico.

**Teste independente**: duas versões de teste, uma com mais acoplamento que a outra: a tendência aponta a piora.

**Cenários de aceite**:

1. **Dado** duas auditorias do mesmo sistema, **quando** abro a tendência, **então** vejo violações novas, resolvidas e persistentes, e a variação de ciclos e acoplamento.
2. **Dado** um PR que cria um ciclo entre módulos, **quando** o Sentinel audita, **então** o ciclo aparece como achado do PR.

---

### História 5 — Sinais clássicos de arquitetura ruim (Prioridade: P3)

Ciclos entre módulos, classes que sabem demais (muitas dependências) e módulos instáveis aparecem como métricas com limites declaráveis.

**Por que P3**: complementa; o valor vem depois do mapa e da verificação.

**Teste independente**: repositório de teste com um ciclo e uma classe com dependências excessivas: ambos são reportados.

**Cenários de aceite**:

1. **Dado** um ciclo entre dois módulos, **quando** o Sentinel audita, **então** o ciclo é reportado com os dois lados.

---

### Casos de borda

- Monorepo com vários sistemas: o mapa separa por sistema declarado e não mistura dependências.
- Injeção de dependência por interface ou por nome: o que o modelo não resolve fica "não resolvido".
- Módulo de teste ou código gerado: fica fora do mapa por padrão, com contagem.
- Arquitetura declarada com camadas que o código nunca usou: aparece como "declarada e sem evidência".
- Dependência permitida por exceção justificada: a exceção é declarada com motivo, data de registro e data de validade, e aparece no relatório; vencida, volta a ser desvio.
- Frameworks que criam dependência em tempo de execução (proxies): declaradas como limite.
- Projeto sem arquitetura declarada: o mapa recuperado é entregue sem desvios, e o relatório sugere por onde começar a declarar.

## Requisitos *(obrigatório)*

### Requisitos funcionais

- **FR-001**: O Sentinel MUST recuperar, a partir do modelo estrutural, o mapa de módulos, camadas e dependências do projeto, cada dependência com pelo menos uma evidência (arquivo e linha). A camada de uma classe MUST ser inferida pelas anotações e estereótipos do framework vindos do modelo; classe sem sinal MUST aparecer como "sem camada", e a inferência MUST NOT usar nome de pacote nem adivinhação.
- **FR-002**: O Sentinel MUST aceitar a arquitetura-alvo no formato atual de política e estendê-lo para dependências indiretas, ciclos proibidos e exceções com motivo.
- **FR-003**: Cada desvio MUST mostrar o caminho da dependência (direta ou indireta) com localização.
- **FR-004**: O mapa e os desvios MUST marcar "não resolvido" para o que o modelo não conseguiu determinar.
- **FR-005**: O Sentinel MUST medir o verificador de arquitetura com violações plantadas em código limpo e em projetos que respeitam a arquitetura, e registrar acerto e alarme falso na régua da spec 003.
- **FR-006**: O verificador novo MUST substituir o atual somente com veredito "fica" da spec 003; as verificações atuais continuam rodando até lá.
- **FR-007**: O Sentinel MUST acompanhar, por auditoria, violações novas, resolvidas e persistentes (a violação é identificada pela regra violada mais o par origem→destino, sem a linha), ciclos e, por módulo, a instabilidade de Martin (dependências que saem ÷ (saem + entram), de 0 a 1) mais a contagem bruta de dependências entre módulos; a definição MUST ficar registrada e não mudar sem nova medição.
- **FR-008**: Em auditoria de PR, apenas violações introduzidas ou pioradas pelo PR MUST ser comentadas (conforme a spec 004).
- **FR-009**: Exceções declaradas MUST ter motivo, data de registro e data de validade obrigatória, e MUST aparecer no relatório com os três; dentro da validade não contam como desvio; vencida, a dependência MUST voltar a ser desvio e a exceção MUST aparecer como vencida.
- **FR-010**: O Sentinel MUST NOT exigir que a arquitetura seja declarada para entregar o mapa recuperado.
- **FR-011**: O peso da dimensão de arquitetura na nota MUST ser mantido; qualquer mudança de peso MUST ser registrada e reavaliada em conjunto (regra do placar).
- **FR-012**: A funcionalidade MUST rodar no ambiente atual do Sentinel, sem instalar ferramentas na máquina e sem executar código auditado.

### Entidades principais

- **Mapa recuperado**: módulos, camadas inferidas, dependências com evidência, pontos de entrada.
- **Arquitetura-alvo**: camadas, regras de dependência, ciclos proibidos e exceções, declaradas pela equipe.
- **Desvio**: diferença entre o mapa e a arquitetura-alvo, com caminho e evidência.
- **Violação plantada**: desvio inserido de propósito em código de teste, com gabarito.
- **Tendência de erosão**: série de violações, ciclos e acoplamento ao longo das auditorias.
- **Exceção**: dependência permitida por motivo, com data de registro e data de validade.

## Critérios de sucesso *(obrigatório)*

### Resultados mensuráveis

- **SC-001**: Em repositórios de teste de estrutura conhecida, o mapa recuperado contém 100% dos módulos e dependências do gabarito, cada uma com evidência.
- **SC-002**: Nas violações plantadas, o verificador acha as diretas e as indiretas; o acerto e o alarme falso são publicados com intervalo de 95%, e o veredito da spec 003 é "fica" ou, se não for, o verificador atual permanece.
- **SC-003**: Em projetos de teste que respeitam a arquitetura, o alarme falso é o publicado na régua, sem média que esconda lacunas.
- **SC-004**: Nenhuma verificação atual de arquitetura deixa de funcionar; o CI continua verde.
- **SC-005**: Em PRs de teste, 100% das violações e ciclos introduzidos aparecem, e 0 das antigas.
- **SC-006**: A tendência de erosão aponta a direção correta em 100% dos pares de versões de teste (um mais acoplado que o outro).
- **SC-007**: 100% das dependências "não resolvidas" aparecem como tal.

### Como cada critério é verificado (job do CI)

| Critério | Prova |
|---|---|
| SC-001, SC-005, SC-006, SC-007 | Testes de unidade e de integração com repositórios de teste (jobs de testes da API, JVM e integração HTTP) |
| SC-002, SC-003 | Medição da spec 003 com violações plantadas e código limpo |
| SC-004 | Jobs de pacotes de regras e de testes existentes |

Os nomes exatos dos jobs ficam para o `/speckit-plan`.

## Premissas

- Os modelos da spec 005 e a régua da spec 003 existem antes.
- As violações plantadas usam repositórios de treino ou criados para o teste; o reservado não é lido.
- O formato atual de política é compatível com o estendido; migração, se necessária, é do plano.
- Tendência e métricas de acoplamento usam as definições mais comuns, escolhidas no plano e registradas.
- Pode ser exibido no painel numa evolução; esta spec exige o relatório e a API de resultado, não a tela.
- Fora do escopo: rastreio de dados (spec 006); revisão externa (spec 008).
