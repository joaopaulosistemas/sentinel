# Especificação: Sentinel Premium 6 — Prova externa

**Ramo**: `spec/008-prova-externa` | **Criada**: 2026-11-12 | **Status**: Rascunho (1 pergunta aberta: Q1)
**Pedido**: "Sentinel Premium — Plano de Ação", eixo **prova**: transformar os números internos em evidência que terceiros possam conferir e repetir. É a última das seis specs e a que fecha a promessa do plano: "provar com números". Depende de todas as anteriores, principalmente da 003 (régua e conjunto reservado).

## Linha de base: o que já existe (levantado no repositório em 2026-11-12)

- **Números internos**: o OWASP Benchmark roda no CI com linha de base; há calibração em repositórios públicos, gabaritos de treino e de validação e `precisao.md` (658 achados revisados, 91 regras) com amostras pequenas.
- **Revisão humana**: `docs/evolucao/revisao-humana.md` existe com linhas de revisão, mas nenhum veredito humano está preenchido; a concordância entre agente e pessoa nunca foi calculada.
- **Credibilidade no placar**: o eixo de credibilidade pede, para a nota máxima, pelo menos 100 achados revisados em pelo menos 10 repositórios públicos, com 60 conferidos por pessoa e concordância de 80%, e um terço dos repositórios em Java/Spring ou Angular.
- **Padrões de saída**: o Sentinel gera SARIF e SBOM; o placar pede, para a nota máxima, aceite pelo GitHub Code Scanning em execução real, e não há evidência registrada de que isso tenha ocorrido.
- **Comparação com o mercado**: não há comparação medida, nos mesmos conjuntos, entre o Sentinel e ferramentas conhecidas.
- **O que falta**: um relatório público de método e números que outra pessoa consiga repetir; revisão feita por alguém fora do projeto; comparação honesta com ferramentas de mercado; a medição final no conjunto reservado.

## Cenários de usuário e testes *(obrigatório)*

### História 1 — Um relatório que um terceiro consegue repetir (Prioridade: P1)

Um relatório de qualidade descreve o método, os conjuntos (com commits fixos), as versões do Sentinel e das ferramentas, os números por pilha com intervalo de confiança e os limites do que não foi medido. Alguém de fora, com os mesmos conjuntos e versões, obtém os mesmos números.

**Por que P1**: sem repetição por terceiros não é prova, é afirmação.

**Teste independente**: uma pessoa que não participou do projeto segue as instruções do relatório e chega aos mesmos números.

**Cenários de aceite**:

1. **Dado** o relatório, **quando** uma pessoa de fora segue o método com os conjuntos e as versões descritos, **então** os números dela coincidem com os publicados.
2. **Dado** uma pilha sem medida, **quando** o relatório a menciona, **então** diz "sem medida" e o que falta, sem estimar.
3. **Dado** qualquer taxa, **quando** a leio, **então** ela traz total de casos e intervalo.

---

### História 2 — Revisão por pessoa independente (Prioridade: P1)

Uma amostra de achados em código real, sorteada de forma reproduzível, é julgada por uma pessoa de fora do projeto, que não vê o veredito do agente nem o de João. O relatório publica a precisão por regra a partir desses vereditos e a concordância com os vereditos internos.

**Por que P1**: é o selo que o plano chama de "revisão externa".

**Teste independente**: com uma amostra de vereditos de teste, o cálculo de precisão e de concordância está correto, e o revisor não recebe o veredito interno.

**Cenários de aceite**:

1. **Dado** a amostra sorteada, **quando** o revisor externo a recebe, **então** ela vem sem vereditos anteriores.
2. **Dado** os vereditos do revisor, **quando** o relatório é gerado, **então** traz precisão por regra, com intervalo, e a concordância com os vereditos internos, listando cada divergência.
3. **Dado** divergência relevante, **quando** o relatório é fechado, **então** a regra ou o veredito interno é corrigido e registrado, não escondido.

---

### História 3 — Comparação honesta com ferramentas de mercado (Prioridade: P2)

O Sentinel é comparado, nos mesmos conjuntos e com as mesmas regras de contagem, com ferramentas de mercado conhecidas, mostrando onde ganha, onde perde e onde empata, com intervalos.

**Por que P2**: dá contexto aos números; mas só vale se for justa e reproduzível.

**Teste independente**: a comparação usa os mesmos conjuntos, contagem idêntica e versões fixadas, e traz um parágrafo de limites.

**Cenários de aceite**:

1. **Dado** as ferramentas escolhidas, **quando** a comparação roda, **então** todas são medidas nos mesmos conjuntos e com a mesma definição de acerto e de alarme falso.
2. **Dado** um resultado desfavorável ao Sentinel, **quando** o relatório sai, **então** ele é publicado como os demais.

---

### História 4 — Medição final no conjunto reservado (Prioridade: P1)

No marco final, o conjunto reservado é medido uma vez para a versão a publicar, e o número entra no relatório com o registro da consulta (spec 003).

**Por que P1**: é o único número que nunca influenciou o desenvolvimento.

**Teste independente**: o registro do reservado mostra uma única medição final da versão, com motivo.

**Cenários de aceite**:

1. **Dado** a versão a publicar, **quando** o reservado é medido, **então** o resultado é registrado com data, versão e motivo, e o relatório cita o total de consultas já feitas.
2. **Dado** que o resultado no reservado é pior que no treino e na validação, **quando** o relatório sai, **então** a diferença é publicada e explicada.

---

### História 5 — Aceite em ambiente real (Prioridade: P2)

O resultado do Sentinel em formato padrão é aceito por um serviço externo real (por exemplo, o painel de segurança do GitHub) num repositório de verdade, com evidência de que os achados aparecem como esperado.

**Por que P2**: fecha um item pendente do placar e mostra integração real.

**Teste independente**: um repositório real recebe o resultado e os achados aparecem no serviço.

**Cenários de aceite**:

1. **Dado** um repositório de teste real, **quando** o resultado é enviado, **então** o serviço o aceita e exibe os achados, e a evidência é anexada ao relatório.

---

### Casos de borda

- O revisor externo não pode ou não quer concluir: o relatório publica o que foi revisado e diz o que ficou pendente, sem completar com vereditos internos.
- Divergência grande entre revisor e agente: as regras divergentes ficam "sem evidência de precisão" até serem corrigidas e medidas de novo.
- Repositório do conjunto sai do ar ou muda: a medição daquele repositório fica marcada como não reproduzível.
- Ferramenta de mercado muda de versão ou licença: a comparação fixa versões, e uma mudança exige nova medição.
- Resultado no reservado pior que o esperado: publica-se mesmo assim.
- Dados privados: nenhum trecho de código de repositório sem permissão de publicação vai para o relatório público; só contagens e referências a fontes públicas.
- Um número publicado depois de corrigido: a versão anterior fica no histórico com o motivo.
- Nenhum código auditado é executado (princípio VII).

## Requisitos *(obrigatório)*

### Requisitos funcionais

- **FR-001**: O Sentinel MUST gerar um relatório de prova com método, conjuntos (commits fixos), versões, números por pilha com intervalo de 95%, limites e o que não foi verificado.
- **FR-002**: O relatório MUST conter tudo o que uma pessoa de fora precisa para repetir a medição e obter os mesmos números.
- **FR-003**: Pilha ou categoria sem medida MUST ser declarada como tal, sem estimativa.
- **FR-004**: A amostra para revisão externa MUST ser sorteada de modo reproduzível em código real, e o revisor externo MUST receber a amostra sem os vereditos internos.
- **FR-005**: O relatório MUST publicar a precisão por regra a partir dos vereditos externos e a concordância entre externos e internos, listando divergências.
- **FR-006**: Divergências relevantes MUST resultar em correção registrada de regra ou de veredito interno.
- **FR-007**: A comparação com ferramentas de mercado MUST usar os mesmos conjuntos, a mesma definição de acerto e de alarme falso e versões fixadas, e MUST publicar resultados desfavoráveis.
- **FR-008**: O conjunto reservado MUST ser medido para a versão a publicar, com registro da consulta e total de consultas informado no relatório.
- **FR-009**: O relatório MUST mostrar a diferença entre treino, validação e reservado, explicando-a quando houver.
- **FR-010**: O Sentinel MUST ter evidência de aceite real do formato padrão de resultados por um serviço externo.
- **FR-011**: Nada que não possa ser publicado (código ou achados de repositório sem permissão) MUST entrar no relatório público; só contagens e referências a fontes públicas.
- **FR-012**: O relatório MUST terminar com Verificado e NÃO verificado, e MUST guardar histórico das correções de números já publicados.

### Entidades principais

- **Relatório de prova**: documento versionado com método, conjuntos, números, limites e histórico de correções.
- **Amostra externa**: achados sorteados para o revisor independente, sem vereditos internos.
- **Veredito externo**: julgamento do revisor (verdadeiro, falso, dúvida) por achado.
- **Concordância**: relação entre vereditos externos e internos, com lista de divergências.
- **Comparação de mercado**: medição de outras ferramentas nos mesmos conjuntos.
- **Medição final**: consulta única do reservado para a versão a publicar.
- **Evidência de aceite**: registro do serviço externo recebendo e exibindo os resultados.

## Critérios de sucesso *(obrigatório)*

### Resultados mensuráveis

- **SC-001**: Uma pessoa de fora, seguindo o relatório, obtém 100% dos números iguais aos publicados (dentro da tolerância declarada para o tempo).
- **SC-002**: A revisão externa cobre ao menos o critério do placar de credibilidade: 100 achados em 10 repositórios públicos, 60 deles julgados por pessoa de fora, e a concordância é calculada e publicada; abaixo de 80% de concordância, as regras divergentes ficam "sem evidência" até corrigidas.
- **SC-003**: 100% das taxas publicadas trazem total de casos e intervalo de 95%.
- **SC-004**: O conjunto reservado tem exatamente uma medição final por versão publicada, com registro; 100% das consultas ao reservado constam do relatório.
- **SC-005**: A comparação de mercado cobre pelo menos duas ferramentas nos mesmos conjuntos, com versões fixadas, e publica todos os resultados, favoráveis ou não.
- **SC-006**: Existe pelo menos uma evidência de aceite real do formato padrão por um serviço externo.
- **SC-007**: 0 trechos de código ou achados individuais de repositórios não publicáveis constam do relatório público.

### Como cada critério é verificado (job do CI)

| Critério | Prova |
|---|---|
| SC-001, SC-003, SC-007 | Repetição do método por terceiro e verificação automática do relatório (job de testes dos scripts e verificação de separação estendida) |
| SC-002 | Cálculo sobre os vereditos externos; **só pode ser dado como atendido quando o revisor externo concluir** |
| SC-004 | Registro do conjunto reservado (spec 003) |
| SC-005 | Medição da spec 003 com as ferramentas de mercado em job dedicado ou manual, com resultado como artefato |
| SC-006 | Evidência anexada ao relatório; depende de execução real fora do CI |

Os nomes exatos dos jobs ficam para o `/speckit-plan`.

## Premissas

- As specs 003 a 007 foram entregues e medidas antes; esta spec fecha o ciclo com a prova.
- O limite de 100 achados, 10 repositórios, 60 julgados por pessoa e 80% de concordância é o que o placar do projeto já define; metas adicionais são fixadas por João depois da linha de base.
- A publicação do relatório é, por padrão, **privada** até João decidir publicar; esta spec produz o relatório e o torna publicável.
- A escolha das ferramentas de mercado e de seus planos é do plano (cuidado com licença).
- Fora do escopo: novas capacidades de detecção; criar código novo de análise.

## Pergunta em aberto *(resolver com `/speckit-clarify` antes do plano)*

- **Q1**: [NEEDS CLARIFICATION: quem será o revisor externo dos achados — uma pessoa conhecida sua (colega de profissão), um profissional contratado ou uma comunidade (por exemplo, pedido público de revisão)? Isso decide o prazo, a independência da prova e se algum custo existe.]
