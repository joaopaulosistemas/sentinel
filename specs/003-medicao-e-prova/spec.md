# Especificação: Sentinel Premium 1 — Medição e prova

**Ramo**: `spec/003-medicao-e-prova` | **Criada**: 2026-11-12 | **Status**: Rascunho esclarecido (Q1 respondida em 2026-11-12); pronta para `/speckit-plan`
**Pedido**: "Sentinel Premium — Plano de Ação": fazer o Sentinel entender código (não só padrões) e **provar com números**. Esta é a primeira das seis specs do plano e a única que não muda o que o Sentinel detecta: ela cria a régua. As outras cinco (menos alarme falso, modelo estrutural, rastreio entre arquivos, arquitetura verificável, prova externa) só começam depois que esta régua existir, porque sem medida "ficou melhor" é opinião.

## Linha de base: o que já existe (levantado no repositório em 2026-11-12)

- **Detecção em código de teste conhecido**: o OWASP Benchmark (2.740 casos Java com gabarito oficial) roda no CI e dá taxa de acerto, taxa de alarme falso e pontuação por categoria, com uma trava de regressão (queda de mais de 0,02). Os casos 1–1370 serviram para escrever regras; 1371–2740 só medem. Pontuação média registrada no placar: 0,44 (treino 0,48, validação 0,42). Mede só Java.
- **Detecção em repositórios vulneráveis de propósito**: há 14 gabaritos de treino e 1 de validação (falhas conhecidas por repositório). Existe cálculo de acerto por falha conhecida e de intervalo de confiança de 95% para proporções. Gabarito de validação só pode vir de fonte pública, nunca da leitura do código.
- **Separação treino × validação**: conferida no CI (hoje 51 repositórios de treino e 8 de validação, sem violações), com impressão digital da composição dos conjuntos.
- **Precisão**: `docs/evolucao/precisao.md` soma 658 achados revisados, 91 regras e vários repositórios, mas a revisão foi feita pelo agente em amostras pequenas. Segundo o próprio arquivo, nenhum veredito humano está preenchido em `revisao-humana.md`, então a concordância entre agente e pessoa nunca foi calculada.
- **O que não existe**: (a) um conjunto que ninguém lê e só se mede em marcos, para o número final não ser fruto de olhar repetido; (b) medida das pilhas fora de Java (Angular, React, Vue, TypeScript/JavaScript, HTML, CSS, Docker, Kubernetes e SQL aparecem sem medida comparável); (c) um relatório único com detecção, ruído e tempo lado a lado; (d) critérios de aceite escritos *antes* de medir uma mudança, com veredito "fica / sai / inconclusivo"; (e) registro de quantas vezes cada conjunto foi consultado; (f) o tempo de auditoria tratado como métrica ao lado da qualidade.

## Cenários de usuário e testes *(obrigatório)*

### História 1 — Saber onde o Sentinel está hoje, em números, num só lugar (Prioridade: P1)

João (ou quem avalia o Sentinel) abre **um relatório** que mostra, para a versão atual e para cada pilha do escopo: quantas falhas conhecidas o Sentinel acha, quantos alarmes falsos dá, quanto tempo leva. Toda taxa vem com o total de casos e a faixa de incerteza. Pilha sem medida aparece escrita como "sem medida", nunca como zero nem omitida.

**Por que P1**: é a "linha de partida" de todo o Premium. Sem ela, nenhuma melhoria futura pode ser provada nem nenhuma regressão percebida.

**Teste independente**: gerar o relatório duas vezes sobre a mesma versão e os mesmos conjuntos e obter os mesmos números; conferir que cada pilha do escopo fixo (constituição, princípio VI) aparece com número ou com "sem medida".

**Cenários de aceite**:

1. **Dado** a versão atual do Sentinel e os conjuntos definidos, **quando** gero o relatório, **então** ele lista detecção, alarme falso e tempo por pilha e por categoria, com a versão do Sentinel, as versões das ferramentas usadas, a data e o commit fixo de cada repositório medido.
2. **Dado** uma taxa calculada sobre poucos casos, **quando** ela aparece no relatório, **então** vem com numerador, denominador, faixa de 95% e um aviso de "amostra pequena" se o total estiver abaixo do mínimo definido nas premissas.
3. **Dado** uma pilha sem gabarito (por exemplo, Kubernetes), **quando** gero o relatório, **então** ela aparece como "sem medida" com o motivo, e não entra em nenhuma média.
4. **Dado** que repito a medição sem mudar nada, **quando** comparo os dois relatórios, **então** os números são idênticos.

---

### História 2 — Provar que uma mudança melhorou, ou removê-la (Prioridade: P1)

Antes de medir qualquer capacidade nova do Premium, registra-se o que contaria como sucesso: qual métrica, qual ganho mínimo, quanto ruído a mais e quanto tempo a mais se aceita. A medição então devolve um veredito: **fica**, **sai** ou **inconclusivo**, com os números do antes e do depois lado a lado.

**Por que P1**: é o compromisso do plano ("o que entra só fica se a medida disser"). Sem critério escrito antes, qualquer resultado vira "ótimo".

**Teste independente**: com três mudanças de teste, uma que melhora de verdade, uma que piora e uma que não muda nada, o veredito sai correto nas três.

**Cenários de aceite**:

1. **Dado** uma proposta de mudança com critérios registrados, **quando** o antes e o depois são medidos nos mesmos conjuntos e com as mesmas versões de tudo, exceto a mudança, **então** o relatório diz "fica" se todos os critérios foram cumpridos e a faixa de incerteza do ganho não inclui zero.
2. **Dado** que a faixa do ganho inclui zero, ou a amostra é pequena, **quando** gero o veredito, **então** ele é "inconclusivo", nunca "fica".
3. **Dado** que a mudança piora uma categoria acima da tolerância, **quando** gero o veredito, **então** ele é "sai", mesmo que a média geral suba.
4. **Dado** que o antes e o depois foram medidos com conjuntos ou ferramentas diferentes, **quando** peço a comparação, **então** o relatório recusa e diz o que difere.
5. **Dado** que os critérios foram escritos ou alterados depois de a medição existir, **quando** gero o veredito, **então** o relatório marca os critérios como "pós-medida" e não emite "fica".

---

### História 3 — Um conjunto reservado que ninguém lê (Prioridade: P1)

Existe um terceiro grupo de repositórios, além de treino e validação: o **reservado**. Nenhum agente nem pessoa lê o código deles para escrever regra; só se medem números, e apenas nos marcos (ao fim de cada spec do Premium). Cada consulta fica registrada, de modo que se veja quantas vezes o conjunto foi "olhado".

**Por que P1**: a validação atual é medida a cada volta; olhar o mesmo conjunto muitas vezes o contamina aos poucos. O reservado é o número que vale perante terceiros.

**Teste independente**: mover um repositório reservado para treino, ou gravar no repositório um trecho de achado dele, faz o CI falhar; uma consulta ao reservado só é aceita com motivo e fica no registro.

**Cenários de aceite**:

1. **Dado** o conjunto reservado definido, **quando** o CI roda, **então** confere que cada repositório pertence a um único grupo e que a composição do reservado bate com a impressão digital registrada.
2. **Dado** uma mudança que contenha código, trecho ou achado individual de repositório reservado em qualquer arquivo versionado, **quando** o CI roda, **então** falha; só contagens e gabarito de fonte pública são permitidos.
3. **Dado** que alguém precisa ler um repositório reservado, **quando** o move para treino, **então** o movimento é explícito, registrado e irreversível: o repositório deixa de contar como reservado para sempre.
4. **Dado** uma medição do reservado, **quando** ela ocorre, **então** o registro guarda data, versão, motivo e o total de consultas daquela versão.

---

### História 4 — Precisão em código real, confirmada por uma pessoa (Prioridade: P2)

Para código real (não vulnerável de propósito), a precisão de cada regra é calculada sobre uma amostra sorteada de forma reproduzível, em que uma pessoa registra o veredito (verdadeiro, falso ou dúvida). O relatório mostra a precisão só dos achados revisados, o quanto falta revisar e a concordância entre o veredito do agente e o da pessoa.

**Por que P2**: é a metade "prova" da qualidade (a outra é o recall). Depende de tempo humano, então vem depois de a régua automática existir.

**Teste independente**: com uma amostra de teste de vereditos preenchidos, o relatório calcula precisão por regra com faixa de 95%, ignora corretamente os não revisados e calcula a concordância.

**Cenários de aceite**:

1. **Dado** achados em código real, **quando** gero a amostra, **então** ela é sorteada de forma reproduzível, cobre as regras com mais achados e cada achado vem com localização e trecho para a pessoa julgar.
2. **Dado** vereditos preenchidos, **quando** gero o relatório, **então** a precisão de cada regra usa apenas achados com veredito, informa quantos faltam e marca "sem evidência" a regra com revisões abaixo do mínimo.
3. **Dado** vereditos do agente e da pessoa para os mesmos achados, **quando** calculo a concordância, **então** o relatório a mostra e lista cada divergência para correção.

---

### História 5 — Lacunas de medição visíveis e priorizadas (Prioridade: P3)

O relatório lista, por pilha, o que falta para ter medida (gabarito, fonte, repositórios) e em que ordem fechar as lacunas, para o Premium não afirmar ganho onde não há régua.

**Por que P3**: fechar lacunas é trabalho contínuo; basta que sejam visíveis desde o primeiro dia.

**Teste independente**: toda pilha "sem medida" do relatório tem uma linha com o que falta.

**Cenários de aceite**:

1. **Dado** o relatório de linha de base, **quando** leio a seção de lacunas, **então** cada pilha sem medida traz o que falta e a prioridade.

---

### Casos de borda

- Um repositório medido some, é reescrito ou muda de dono: cada repositório é fixado por commit, e o relatório diz qual commit foi medido; se o commit desaparecer, a medição fica marcada como não reproduzível, não zerada.
- Uma ferramenta de análise muda de versão entre duas medições: a comparação é recusada (ou o relatório destaca a diferença como causa possível) em vez de atribuir a mudança ao Sentinel.
- A auditoria de um repositório falha ou estoura o tempo: conta como medição incompleta com o motivo, nunca como "zero achados".
- O mesmo problema é achado por duas regras ou dois analisadores: a contagem não pode dobrar; vale a regra de deduplicação já usada.
- Código gerado ou de terceiros embutido (bibliotecas, arquivos "não edite"): fica fora da contagem de detecção e de ruído, e o relatório diz quanto foi excluído.
- O gabarito está errado (falha listada que não é falha, ou falha real que faltou): a correção é registrada com o motivo e o valor anterior fica no histórico; número já publicado errado é corrigido, não escondido.
- Categoria com poucos casos no gabarito: aparece com aviso de amostra pequena e não pode, sozinha, sustentar um veredito "fica".
- Mudança de pilha tem tempo de auditoria muito maior: o tempo entra no veredito como critério declarado; sem limite declarado, o relatório apenas destaca o aumento.
- Nenhum código de repositório auditado é executado, compilado ou instalado durante a medição (apenas análise estática), conforme a constituição.

## Requisitos *(obrigatório)*

### Requisitos funcionais

- **FR-001**: O Sentinel MUST produzir um relatório de medição com, para cada pilha do escopo e cada categoria de falha: casos conhecidos, casos achados, alarmes falsos e tempo de auditoria, registrando a versão do Sentinel, as versões das ferramentas, a data e o commit fixo de cada repositório.
- **FR-002**: Toda taxa do relatório MUST trazer numerador, denominador e faixa de incerteza de 95%; taxa sobre menos casos que o mínimo das premissas MUST levar o aviso "amostra pequena".
- **FR-003**: Pilha ou categoria sem gabarito MUST aparecer como "sem medida" com o motivo, nunca como zero, e MUST ficar fora de qualquer média.
- **FR-004**: Medir de novo as mesmas entradas MUST dar os mesmos números; o relatório MUST guardar o que é preciso para repetir a medição.
- **FR-005**: Todo repositório medido MUST pertencer a exatamente um grupo: treino, validação ou reservado; o CI MUST barrar duplicidade e mudança não registrada de composição.
- **FR-006**: O CI MUST falhar quando um arquivo versionado contiver código, trecho ou achado individual de repositório reservado; só contagens e gabarito de fonte pública são permitidos.
- **FR-007**: Mover um repositório do reservado para qualquer outro grupo MUST ser um ato explícito e registrado, após o qual ele nunca mais conta como reservado.
- **FR-008**: Toda medição do reservado MUST ser registrada com data, versão, motivo e o total de consultas da versão; medir o reservado fora de um marco MUST exigir motivo escrito.
- **FR-009**: Toda proposta de mudança MUST registrar, antes da medição, a métrica, o ganho mínimo, o aumento aceito de alarme falso e o aumento aceito de tempo; critérios registrados depois da medição MUST ser marcados como "pós-medida".
- **FR-010**: O veredito MUST ser "fica", "sai" ou "inconclusivo", com antes e depois lado a lado: "fica" só se todos os critérios forem cumpridos e a faixa do ganho excluir zero; piora de categoria acima da tolerância MUST dar "sai" mesmo com média melhor.
- **FR-011**: A comparação MUST ser recusada, com explicação, quando conjuntos, versões de ferramentas ou entradas diferirem além da mudança sob avaliação.
- **FR-012**: As travas de regressão que já existem (OWASP Benchmark, comparação de calibração, separação treino/validação, revisão de precisão) MUST continuar rodando e valendo; esta spec não as substitui.
- **FR-013**: Para código real, o Sentinel MUST gerar amostra de revisão sorteada de modo reproduzível, aceitar veredito de pessoa (verdadeiro, falso, dúvida) e calcular precisão por regra apenas sobre achados com veredito, informando quantos faltam e marcando "sem evidência" abaixo do mínimo.
- **FR-014**: O Sentinel MUST calcular a concordância entre o veredito do agente e o da pessoa e listar cada divergência.
- **FR-015**: Correções de gabarito ou de número já publicado MUST ser registradas com motivo e valor anterior; nada é sobrescrito sem histórico.
- **FR-016**: Todo relatório MUST terminar com "Verificado" e "NÃO verificado", com o que foi e o que não foi medido.
- **FR-017**: A medição MUST usar apenas análise estática: nenhum código de repositório auditado é executado, compilado ou instalado; nada de uso interno de empregador é copiado para o repositório do Sentinel.
- **FR-018**: O tempo de auditoria por repositório, junto de seu tamanho, MUST ser registrado em toda medição, para que o custo seja visível ao lado da qualidade.
- **FR-019**: O relatório MUST listar as lacunas de medição por pilha, com o que falta e a prioridade (História 5).

### Entidades principais

- **Repositório medido**: código alvo, fixado por commit; tem origem (construído vulnerável de propósito ou código real), pilha(s) e grupo (treino, validação ou reservado).
- **Conjunto**: o conjunto dos repositórios de um grupo, com impressão digital da composição e histórico de movimentos.
- **Gabarito**: lista de falhas conhecidas de um repositório, com a fonte pública de onde veio (obrigatória em validação e reservado) e histórico de correções.
- **Medição**: execução sobre um conjunto, com data, versão do Sentinel, versões das ferramentas, números por pilha e categoria e tempo.
- **Linha de base**: a primeira medição completa antes do Premium, que serve de "antes" para tudo que vem depois.
- **Proposta de mudança**: uma capacidade ou correção sob avaliação, com critérios de sucesso registrados antes da medição.
- **Veredito**: resultado da comparação antes × depois (fica / sai / inconclusivo), com os números que o sustentam.
- **Amostra de revisão**: achados em código real sorteados para a pessoa julgar, com vereditos do agente e da pessoa.
- **Registro de consulta do reservado**: quem, quando, versão e motivo de cada medição do conjunto reservado.

## Critérios de sucesso *(obrigatório)*

### Resultados mensuráveis

- **SC-001**: Uma pessoa obtém, em um único relatório, detecção, alarme falso e tempo de todas as pilhas do escopo fixo; 100% das pilhas aparecem com número ou com "sem medida" e motivo.
- **SC-002**: Duas medições seguidas da mesma versão com os mesmos conjuntos dão números 100% idênticos.
- **SC-003**: 100% das taxas publicadas trazem total de casos e faixa de 95%; 100% das taxas sobre amostra abaixo do mínimo trazem o aviso.
- **SC-004**: Em três mudanças de teste (melhora conhecida, piora conhecida e sem efeito), o veredito sai correto nas três.
- **SC-005**: 0 repositórios do conjunto reservado têm código, trecho ou achado individual versionado, e 0 estão em mais de um grupo; a verificação roda sozinha em cada alteração.
- **SC-006**: 100% das medições do conjunto reservado têm data, versão e motivo no registro.
- **SC-007**: A linha de base é registrada antes que qualquer capacidade do Premium (specs 2 a 6) seja medida; 100% das propostas posteriores citam a linha de base que usaram como "antes".
- **SC-008**: A revisão de precisão em código real alcança, no mínimo, o critério já definido no placar do projeto para o eixo de credibilidade: ao menos 100 achados revisados em ao menos 10 repositórios, ao menos 60 deles confirmados por uma pessoa, e a concordância agente × pessoa calculada e publicada.
- **SC-009**: Nenhum dos verificadores já existentes é desligado ou enfraquecido; o CI continua verde em todas as alterações desta spec.

### Como cada critério é verificado (job do CI)

| Critério | Prova |
|---|---|
| SC-002, SC-003, SC-004 | Testes unitários da lógica de medição e de veredito, com dados de teste fixos (job de testes dos scripts) |
| SC-005, SC-006 | Verificação de separação e de impressão digital (job de separação treino e validação, estendido ao reservado) |
| SC-001, SC-007, SC-009 | Execução da medição de linha de base e conferência no CI; o relatório gerado fica como artefato do job |
| SC-008 | Cálculo sobre `revisao-humana.md` com os vereditos preenchidos pela pessoa; depende de tempo humano e **só pode ser dado como atendido quando a pessoa preencher** |

Os nomes exatos dos jobs ficam para o `/speckit-plan`.

## Premissas

- Esta spec **reaproveita** o que existe (benchmark, cálculo de acerto, separação, revisão) e o estende; não troca nada que funciona.
- **Metas numéricas** (por exemplo, "pontuação ≥ X na pilha Y") **não são fixadas aqui**: só podem ser honestas depois de ver a linha de base. João as define em seguida, e cada spec posterior as cita.
- Conjuntos novos entram "como estão": nenhum agente lê o código do reservado; o gabarito do reservado vem de fonte pública, nunca da leitura do código.
- **Código real** quer dizer, por padrão, projetos de código aberto públicos em Java/Spring e Angular primeiro, depois as demais pilhas. Código de empregador ou de cliente fica fora (esclarecimento de 2026-11-12).
- O veredito humano de precisão é dado por João. Revisão por pessoa de fora (que dá o "selo externo") é assunto da spec 6.
- Mínimo para chamar uma taxa de "amostra pequena": 20 casos (ajustável pelo João; o critério é um só para todo o relatório).
- O tempo de auditoria é medido e mostrado; o **limite de tempo aceito** é definido pelo João depois da linha de base.
- A parte em Java e o Docker só são provados no CI do GitHub (a sessão do Claude na nuvem não roda Maven nem Docker).
- Mostrar o relatório no dashboard é desejável, mas fica fora desta spec; aqui o relatório é um documento versionado e um artefato de CI.
- Fora do escopo desta spec: mudar qualquer regra, analisador ou pontuação; reduzir alarme falso (spec 2); novos modelos de código (specs 3 a 5); revisão externa (spec 6); novas tecnologias fora do escopo fixo da constituição.

## Esclarecimentos

### Sessão 2026-11-12

- Q: O conjunto de "código real" pode incluir código de empregador ou de clientes? → A: Não. Só código aberto público (Java/Spring e Angular primeiro, depois as demais pilhas). Código corporativo fica de fora desta spec; se um dia entrar, será uma spec própria, com autorização da empresa.
