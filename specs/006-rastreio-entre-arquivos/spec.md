# Especificação: Sentinel Premium 4 — Rastreio de dado inseguro entre arquivos

**Ramo**: `spec/006-rastreio-entre-arquivos` | **Criada**: 2026-11-12 | **Status**: Esclarecida (5 perguntas, 2026-11-13); plano, pesquisa e tarefas prontos (`plan.md`, `tasks.md`); próximo: implementar só quando o João mandar
**Pedido**: "Sentinel Premium — Plano de Ação", eixo **profundidade**: seguir um dado que vem de fora (parâmetro de requisição, corpo, cabeçalho) até um ponto perigoso (consulta ao banco, comando, caminho de arquivo, HTML), atravessando métodos, classes e arquivos. Começa por **uma** classe de falha, **injeção de SQL**, e só depois se repete para outras. Depende das specs 003 (régua) e 005 (modelo estrutural).

## Linha de base: o que já existe (levantado no repositório em 2026-11-12)

- Há regras de padrão que seguem o dado **dentro de um arquivo** (as de nome `taint`), e um rastreio entre arquivos em Java com Joern, **opcional**, com job próprio no CI. Fora de Java não há rastreio entre arquivos.
- O OWASP Benchmark (Java) mede, por categoria, taxa de acerto e de alarme falso; a categoria de injeção de SQL é uma delas. As mais fracas hoje, segundo o placar, são injeção de comando, XPath, travessia de caminho, LDAP e fronteira de confiança.
- Os caminhos reais de aplicações costumam atravessar camadas (controlador → serviço → repositório). Padrões de um arquivo só não enxergam isso.
- Não há hoje uma explicação passo a passo do caminho que o dado percorreu em cada achado.

## Esclarecimentos

### Sessão 2026-11-13

- Q: Onde aparece um rastro perdido? → A: só no relatório de cobertura do rastreio (contagem, lista e motivo por caso); não gera achado, não entra na nota nem no comentário do PR; nunca é apresentado como "seguro".
- Q: Valor validado ou convertido antes da consulta gera achado? → A: não, quando há um sanitizador **declarado** na lista da classe de falha; o relatório de cobertura registra qual sanitizador encerrou o rastro; sem sanitizador declarado, o achado existe.
- Q: O rastreio segue o dado por método de biblioteca de terceiros (concatenar, `StringBuilder`, `String.format`, `List.add`)? → A: só pelos **propagadores declarados** em dados, por classe de falha; fora da lista, o rastro é "não resolvido" com o motivo, no relatório de cobertura do rastreio.
- Q: O que define "o mesmo problema" achado por mais de um mecanismo? → A: mesma classe de falha e mesmo destino (arquivo e linha da execução perigosa, comparados sem depender de reformatação); o caminho do rastreio fica anexado ao achado único, com todas as fontes listadas.
- Q: Quais execuções de SQL são destinos na primeira fatia? → A: JDBC (`Statement` e `PreparedStatement` montado por texto), `JdbcTemplate`, consulta nativa e JPQL/HQL pelo `EntityManager` e pelo Panache do Quarkus; MyBatis, jOOQ e Hibernate Criteria ficam para depois, por entrarem só como dados.

## Cenários de usuário e testes *(obrigatório)*

### História 1 — Achar injeção de SQL que atravessa camadas (Prioridade: P1)

Quando um parâmetro de requisição chega a uma consulta montada por concatenação passando por controlador, serviço e repositório em arquivos diferentes, o Sentinel acha o problema e mostra o caminho completo.

**Por que P1**: é o caso comum em sistemas reais e o que hoje escapa dos padrões de um arquivo só.

**Teste independente**: repositório de teste com 3 camadas e a falha plantada: o achado aparece com o caminho entrada → saída; trocar a concatenação por consulta parametrizada faz o achado desaparecer.

**Cenários de aceite**:

1. **Dado** um parâmetro HTTP que passa por dois métodos em arquivos diferentes e chega a uma consulta concatenada, **quando** o Sentinel audita, **então** há um achado de injeção de SQL com a lista ordenada de passos.
2. **Dado** o mesmo código com consulta parametrizada, **quando** o Sentinel audita, **então** não há achado.
3. **Dado** que o valor passa por um sanitizador declarado (lista de permitidos ou conversão para número) antes de chegar à consulta, **quando** o Sentinel audita, **então** não há achado, e o relatório de cobertura do rastreio registra qual sanitizador encerrou o rastro; sem sanitizador declarado, o achado existe.

---

### História 2 — Cada achado conta o caminho (Prioridade: P1)

Todo achado de rastreio mostra a origem (onde o dado entra), cada passo (arquivo, linha, o que acontece) e o destino (onde é perigoso), e diz o que faltou para o tornar seguro.

**Por que P1**: sem explicação, ninguém confia nem corrige; e é isso que diferencia de um alerta de padrão.

**Teste independente**: o achado de teste traz origem, passos e destino com localização exata e a sugestão de correção.

**Cenários de aceite**:

1. **Dado** um achado de rastreio, **quando** o abro, **então** vejo origem, passos e destino, cada um com arquivo e linha.
2. **Dado** um caminho muito longo, **quando** o abro, **então** ele aparece completo ou marcado como truncado, nunca encurtado sem aviso.

---

### História 3 — Menos alarme falso que o padrão atual (Prioridade: P1)

O rastreio só fica se, medido pela spec 003 em injeção de SQL, ele acha igual ou mais e erra menos que o padrão atual e que o Joern opcional. Resultado ruim é registrado e o rastreio não entra.

**Por que P1**: é a regra de decisão do plano.

**Teste independente**: medição antes × depois na categoria de injeção de SQL do Benchmark e nos gabaritos, com veredito.

**Cenários de aceite**:

1. **Dado** a categoria de injeção de SQL nos conjuntos de treino e validação, **quando** o rastreio é medido, **então** o relatório compara acerto e alarme falso com o padrão atual e com o Joern, e emite o veredito.
2. **Dado** que o rastreio piora outra categoria, **quando** a medição roda, **então** o veredito é "sai" para essa parte.

---

### História 4 — Uma segunda classe de falha prova que a fatia se repete (Prioridade: P2)

Depois de injeção de SQL funcionar, o mesmo mecanismo é aplicado a uma segunda classe, escolhida entre as de pior pontuação na linha de base da spec 003, sem reescrever o mecanismo.

**Por que P2**: mostra que é uma capacidade e não um caso isolado.

**Teste independente**: a segunda classe entra só com definição das origens e destinos, com veredito da spec 003.

**Cenários de aceite**:

1. **Dado** a linha de base da spec 003, **quando** escolho a segunda classe, **então** ela é uma das de menor pontuação medida e a escolha é registrada.
2. **Dado** que a segunda classe é adicionada, **quando** o mecanismo roda, **então** não houve mudança no mecanismo, só nas definições de origem e destino.

---

### História 5 — Convivência com o que existe (Prioridade: P2)

As regras de padrão e o Joern continuam rodando. Um achado achado por mais de um mecanismo aparece uma vez só, com as fontes listadas.

**Por que P2**: a regra do plano é "o que já roda continua; o novo só fica se a medida disser".

**Teste independente**: o mesmo problema achado pelo padrão e pelo rastreio vira um achado com duas fontes.

**Cenários de aceite**:

1. **Dado** um problema achado pelo padrão e pelo rastreio com a mesma classe de falha e o mesmo destino, **quando** o relatório sai, **então** há um achado só, com ambas as fontes e o caminho do rastreio anexado.

---

### Casos de borda

- ORM e consultas derivadas (nomes de método do repositório, parâmetros nomeados): são seguros e não podem gerar alarme.
- Consulta montada em várias etapas, em coleções ou construtores de texto: o rastreio deve seguir o valor, ou declarar que perdeu o rastro.
- Valor passando por lambda, fluxo reativo ou chamada assíncrona: se o rastro for perdido, o resultado é "não resolvido" (no relatório de cobertura do rastreio), não "seguro".
- Reflexão e injeção por nome, que o modelo não resolve (spec 005): vão para "não resolvido".
- Função de sanitização própria do projeto: só vale como sanitizador se estiver numa lista declarada, não por ter nome bonito.
- Ciclos de chamadas, recursão e caminhos muito numerosos: há limite de profundidade e de tempo, e o relatório diz quando foi atingido.
- Procedimento armazenado e SQL dinâmico dentro do banco: fora do alcance da primeira fatia, declarado.
- Código gerado: fica fora, com contagem.
- Nenhum código auditado é executado (princípio VII).

## Requisitos *(obrigatório)*

### Requisitos funcionais

- **FR-001**: O Sentinel MUST seguir um dado de uma origem não confiável (entrada externa) até um destino perigoso, atravessando métodos, classes e arquivos do mesmo projeto, usando o modelo estrutural da spec 005.
- **FR-002**: A primeira fatia MUST tratar injeção de SQL em Java com Spring e Quarkus, com destinos em JDBC (`Statement` e `PreparedStatement` montado por texto), `JdbcTemplate`, consulta nativa e JPQL/HQL pelo `EntityManager` e pelo Panache do Quarkus (MyBatis, jOOQ e Hibernate Criteria ficam fora, entrando depois só como dados); outras classes e o frontend ficam para fatias posteriores.
- **FR-003**: Origens, destinos, sanitizadores e propagadores MUST ser declarados em dados (listas por classe de falha), não no código do mecanismo, para que novas classes entrem sem reescrevê-lo.
- **FR-004**: Cada achado de rastreio MUST mostrar origem, passos ordenados (arquivo, linha, ação) e destino; caminho truncado MUST ser marcado.
- **FR-005**: Consulta parametrizada, construção por ORM seguro e valor que passa por sanitizador declarado (lista de permitidos ou conversão para tipo numérico) MUST NOT gerar achado, e o relatório de cobertura do rastreio MUST registrar o sanitizador que encerrou o rastro.
- **FR-006**: Quando o rastro for perdido (reflexão, dinâmico, limite de profundidade), o resultado MUST ser "não resolvido" com o motivo, nunca "seguro". O caso MUST aparecer no relatório de cobertura do rastreio (contagem, lista e motivo) e MUST NOT gerar achado, nem entrar na nota ou no comentário do PR.
- **FR-007**: As regras de padrão e o Joern MUST continuar rodando; o mesmo problema achado por mais de um mecanismo MUST aparecer uma vez, com as fontes; "o mesmo" significa mesma classe de falha e mesmo destino (arquivo e linha da execução perigosa, sem depender de reformatação), e o caminho do rastreio MUST ficar anexado ao achado único.
- **FR-008**: O rastreio MUST ser medido pela spec 003 contra o padrão atual e contra o Joern, em injeção de SQL, nos conjuntos de treino e validação, antes de entrar no padrão.
- **FR-009**: O rastreio MUST NOT piorar a detecção de outras categorias além da tolerância das travas existentes.
- **FR-010**: Limites de profundidade, de caminhos e de tempo MUST ser configuráveis e registrados no relatório quando atingidos.
- **FR-011**: O tempo adicional de auditoria MUST ser medido e comparado com o orçamento definido por João.
- **FR-012**: A segunda classe de falha MUST ser escolhida entre as de menor pontuação na linha de base da spec 003 e a escolha registrada.
- **FR-013**: Ao passar por método de biblioteca de terceiros, o dado MUST continuar sendo seguido somente se o método estiver na lista de **propagadores** declarada para a classe de falha; fora da lista, o rastro MUST ser "não resolvido" com o motivo, nunca "seguro" e nunca propagado por suposição.

### Entidades principais

- **Origem**: ponto onde dado externo entra (parâmetro, corpo, cabeçalho, arquivo lido).
- **Destino**: ponto perigoso por classe de falha (execução de consulta, comando, caminho de arquivo, saída HTML).
- **Sanitizador**: operação declarada que torna o dado seguro para um destino.
- **Propagador**: operação de biblioteca declarada que transmite o dado adiante (concatenar, montar texto, guardar em coleção).
- **Caminho**: sequência ordenada de passos da origem ao destino.
- **Achado de rastreio**: achado que contém o caminho e a classe de falha, com as fontes que o detectaram.
- **Definição de classe de falha**: lista de origens, destinos, sanitizadores e propagadores de uma classe.

## Critérios de sucesso *(obrigatório)*

### Resultados mensuráveis

- **SC-001**: Nos repositórios de teste com falhas de injeção de SQL plantadas atravessando 2 ou mais arquivos, 100% das plantadas são achadas com caminho completo, e 0 alarmes aparecem nas versões parametrizadas.
- **SC-002**: Na categoria de injeção de SQL, o veredito da spec 003 sobre treino e validação é "fica" (acerto maior ou igual e alarme falso menor ou igual ao padrão atual e ao Joern, com a melhora em pelo menos um dos dois, com intervalo que exclui zero); se não for, o rastreio não entra e o resultado é registrado.
- **SC-003**: Nenhuma outra categoria do Benchmark cai além da tolerância das travas existentes.
- **SC-004**: 100% dos achados de rastreio trazem origem, passos e destino com localização.
- **SC-005**: 100% dos casos de rastro perdido nos repositórios de teste aparecem no relatório de cobertura do rastreio como "não resolvido" com motivo; 0 aparecem como seguros e 0 viram achado.
- **SC-006**: A segunda classe entra sem alterar o mecanismo, com veredito registrado.
- **SC-007**: O tempo adicional fica dentro do orçamento fixado por João.

### Como cada critério é verificado (job do CI)

| Critério | Prova |
|---|---|
| SC-001, SC-004, SC-005 | Testes de unidade e de integração com repositórios de teste (jobs de testes da API e da JVM) |
| SC-002, SC-003 | Benchmark e medição da spec 003, incluindo o job do Joern para a comparação |
| SC-006 | Teste que adiciona definição de classe sem tocar o mecanismo |
| SC-007 | Medição de tempo da spec 003 |

Os nomes exatos dos jobs ficam para o `/speckit-plan`.

## Premissas

- A spec 005 (modelo) e a 003 (régua) estão prontas antes de a medição desta spec ser levada em conta.
- Injeção de SQL é a primeira fatia por ser comum, ter categoria no Benchmark e gabarito público, e por permitir comparação com o Joern.
- A ferramenta usada para o rastreio não é decidida aqui (ver spec 005).
- Frontend (rastro em TypeScript/Angular/React/Vue) é uma fatia seguinte, depois de o mecanismo provar valor em Java.
- Fora do escopo: reduzir ruído geral (spec 004); verificar arquitetura (spec 007); revisão externa (spec 008).
