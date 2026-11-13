# Especificação: Sentinel Premium 3 — Modelo estrutural do código

**Ramo**: `spec/005-modelo-estrutural-codigo` | **Criada**: 2026-11-12 | **Status**: Esclarecida (5 perguntas, 2026-11-13); próximo: /speckit-plan
**Pedido**: "Sentinel Premium — Plano de Ação", eixo **profundidade**: o Sentinel precisa entender o código (classes, métodos, chamadas, rotas, anotações), e não só procurar padrões de texto. Este modelo é a base das specs 006 (rastreio entre arquivos) e 007 (arquitetura verificável). Depende da spec 003.

## Linha de base: o que já existe (levantado no repositório em 2026-11-12)

- A maior parte das regras próprias (311 em 16 pacotes) procura **padrões** no texto/árvore de um arquivo por vez, e são executadas por motor de regras declarativo. Isso é rápido e plug-and-play, mas não responde perguntas como "quem chama este método?" ou "este endpoint chega a uma consulta ao banco?".
- Há analisadores Java próprios (por exemplo, o de arquitetura, que enxerga camadas e entidade exposta em controller) e um rastreio entre arquivos em Java com Joern, **opcional**, que roda como etapa à parte e tem um job próprio no CI.
- Para TypeScript, JavaScript, Angular, React e Vue o Sentinel depende de analisadores de mercado via SARIF e de regras de padrão; não há um modelo próprio unificado de componentes, serviços, rotas e chamadas.
- Não há uma forma única de **consultar** o código por estrutura (listar endpoints, achar quem chama X, ver quais classes têm uma anotação), nem um relatório de **quanto** do código o Sentinel entendeu.

## Esclarecimentos

### Sessão 2026-11-13

- Q: Por onde as pessoas fazem as consultas por estrutura? → A: só por comando da CLI (saída em texto e JSON); as regras usam o modelo por dentro; sem rota nova na API e sem tela no painel nesta spec.
- Q: O que uma regra baseada no modelo faz com arquivo não modelado? → A: a regra de padrão equivalente continua valendo nesse arquivo, e o relatório de cobertura o lista como "não modelado" com o motivo.
- Q: Como tratar chamada a biblioteca de terceiros? → A: registrar como relação "externa", com o nome qualificado da biblioteca e do método, sem entrar no código dela.
- Q: Como registrar chamada de API do frontend com caminho montado por partes? → A: registrar a parte fixa com a variável marcada, indicando que o caminho é "parcial" (ex.: `/clientes/{variável}`), sem calcular o valor.
- Q: O modelo é guardado entre auditorias? → A: não; é reconstruído a cada auditoria; só o resumo de cobertura e o tempo de construção ficam guardados com o resultado da auditoria.

## Cenários de usuário e testes *(obrigatório)*

### História 1 — Consultar o código por estrutura, no backend (Prioridade: P1)

Quem audita ou escreve regras pode perguntar ao Sentinel, para um repositório Java (Spring ou Quarkus): quais são os endpoints, quais métodos eles chamam, onde há acesso a banco, quais classes têm certa anotação, quem usa certo método. A resposta é determinística: o mesmo código dá a mesma resposta.

**Por que P1**: é o que separa "procurar texto" de "entender código"; as specs 006 e 007 dependem disso.

**Teste independente**: num repositório de teste com estrutura conhecida, as consultas devolvem exatamente os endpoints, chamadas e anotações esperados, e duas execuções dão a mesma resposta.

**Cenários de aceite**:

1. **Dado** um projeto Spring ou Quarkus, **quando** peço a lista de endpoints, **então** recebo cada rota com método HTTP, caminho, classe e método que a atende.
2. **Dado** um método, **quando** pergunto quem o chama, **então** recebo os chamadores diretos, incluindo os de outros arquivos e módulos do projeto.
3. **Dado** uma chamada que o Sentinel não consegue resolver (reflexão, injeção por nome), **quando** consulto, **então** ela aparece como "não resolvida" com o motivo, e não some.

---

### História 2 — O mesmo, no frontend (Prioridade: P1)

Para Angular, React, Vue e TypeScript/JavaScript, o Sentinel enxerga componentes, serviços, rotas, chamadas de API e dependências entre módulos, e responde as mesmas perguntas de estrutura.

**Por que P1**: o escopo do produto é full stack; o modelo só no backend deixa metade do código fora.

**Teste independente**: num repositório de teste de cada framework, a lista de componentes, rotas e chamadas à API confere com o gabarito do teste.

**Cenários de aceite**:

1. **Dado** um projeto Angular, React ou Vue, **quando** peço componentes e rotas, **então** recebo a lista com arquivo e relação entre eles.
2. **Dado** uma chamada de API no frontend, **quando** consulto, **então** vejo qual componente ou serviço a faz e para qual caminho. Se o caminho for montado por partes, vejo a parte fixa, a variável marcada e a indicação "parcial".

---

### História 3 — Regras que usam o modelo acertam mais (Prioridade: P1)

Um piloto de três regras que hoje erram por olhar só texto é reescrito para usar o modelo. O resultado é medido pela spec 003: fica só se detectar mais ou errar menos, sem custo inaceitável.

**Por que P1**: sem esse teste, o modelo é só uma infraestrutura bonita; o valor é provado em regra real.

**Teste independente**: para cada regra do piloto, medição antes × depois nos conjuntos de treino e validação com veredito registrado.

**Cenários de aceite**:

1. **Dado** uma regra do piloto, **quando** a versão com modelo é medida contra a de padrão, **então** o relatório traz o veredito "fica", "sai" ou "inconclusivo" com os números.
2. **Dado** que a regra nova perde uma falha que a antiga achava, **quando** a medição roda, **então** isso aparece como regressão e impede o "fica".
3. **Dado** um arquivo não modelado, **quando** o Sentinel audita, **então** a regra de padrão equivalente ainda o cobre.
4. **Dado** que ambas as versões convivem, **quando** o Sentinel audita, **então** as regras atuais continuam rodando; a nova só substitui a antiga depois do "fica".

---

### História 4 — O Sentinel diz o que não entendeu (Prioridade: P2)

Para cada repositório, o relatório informa quanto do código foi modelado e quanto ficou de fora (arquivos que não compilam, construções dinâmicas, código gerado), para ninguém confiar numa análise mais completa do que ela é.

**Por que P2**: honestidade sobre a cobertura é o que torna o modelo confiável; vem depois de ele existir.

**Teste independente**: repositório de teste com arquivo quebrado e uso de reflexão: o relatório lista ambos como não modelados, com motivo.

**Cenários de aceite**:

1. **Dado** um arquivo com erro de sintaxe, **quando** o Sentinel modela o projeto, **então** o resto continua modelado e o arquivo aparece como "não modelado" com o motivo.
2. **Dado** o relatório de cobertura, **quando** o leio, **então** vejo percentuais por repositório e por tipo de construção, sem média que esconda lacunas.

---

### História 5 — O custo em tempo fica dentro do orçamento (Prioridade: P3)

Construir o modelo não pode tornar a auditoria inviável. O tempo adicional é medido em todo repositório do conjunto e comparado com o orçamento fixado por João depois da linha de base.

**Por que P3**: só dá para fixar o orçamento depois de medir; mas a medida precisa existir desde o primeiro dia.

**Teste independente**: o relatório de medição da spec 003 traz o tempo com e sem o modelo para cada repositório.

**Cenários de aceite**:

1. **Dado** um repositório grande, **quando** o modelo é construído, **então** o tempo adicional é registrado e comparado com o orçamento; estourar o orçamento impede o "fica".

---

### Casos de borda

- Código com erros de sintaxe, ou que não compila: modela-se o que for possível e o resto é relatado.
- Geração de código por anotação (Lombok, MapStruct) e proxies do framework: o que o modelo não vê é declarado como não resolvido, não como inexistente.
- Repositórios muito grandes ou com muitos módulos: o modelo tem limite de tempo e de memória; ao atingi-lo, o relatório diz o que ficou de fora.
- Nomes iguais em pacotes diferentes: a resolução tem de distinguir pelo contexto de importação.
- Código gerado e bibliotecas embutidas: ficam fora do modelo por padrão, com contagem.
- Mudança de versão da linguagem ou do framework: a cobertura é medida de novo antes de se comparar números.
- Nenhum código do repositório auditado é executado ou compilado (princípio VII): o modelo vem só de leitura estática.

## Requisitos *(obrigatório)*

### Requisitos funcionais

- **FR-001**: O Sentinel MUST construir, para um repositório, um modelo estrutural com unidades (classes, funções/métodos, componentes), relações (chamadas, uso, herança, importação), pontos de entrada (rotas e endpoints), acessos a dados e anotações/decoradores, sem executar o código.
- **FR-002**: O modelo MUST cobrir, na primeira entrega, Java com Spring e Quarkus, e TypeScript/JavaScript com Angular, React e Vue; as demais pilhas do escopo (HTML, CSS, SQL, Docker, Kubernetes) ficam como evolução.
- **FR-003**: O modelo MUST ser determinístico: o mesmo código e as mesmas versões dão sempre o mesmo modelo.
- **FR-004**: O Sentinel MUST oferecer consultas por estrutura (listar endpoints, achar chamadores e chamados, achar elementos por anotação, listar acessos a dados) usáveis por regras (internamente) e por pessoas (comando da CLI, com saída em texto e JSON). Esta spec MUST NOT criar rota na API pública nem tela no painel.
- **FR-005**: Tudo que o modelo não conseguir resolver MUST ser marcado como "não resolvido" com o motivo, e MUST NOT ser tratado como inexistente.
- **FR-006**: O Sentinel MUST gerar um relatório de cobertura do modelo por repositório e por tipo de construção.
- **FR-007**: O modelo MUST poder ser usado por regras sem alterar as regras atuais: regras de padrão continuam rodando enquanto as novas são medidas. Em arquivo não modelado, a regra de padrão equivalente MUST continuar valendo mesmo depois do "fica", e o arquivo MUST aparecer no relatório de cobertura.
- **FR-008**: Uma regra baseada no modelo MUST substituir a de padrão equivalente somente com veredito "fica" da spec 003.
- **FR-009**: O tempo de construção do modelo MUST ser registrado por repositório e comparado com o orçamento definido por João; estourar o orçamento MUST impedir o "fica".
- **FR-010**: O modelo MUST ser reaproveitável pelas specs 006 e 007 sem reconstrução própria.
- **FR-011**: O modelo MUST NOT exigir tecnologia fora do escopo fixo da constituição nem custo de IA em tempo de auditoria.
- **FR-012**: A funcionalidade MUST rodar onde o Sentinel já roda (Docker, máquina do trabalho e de casa), sem exigir instalação de ferramentas na máquina.
- **FR-013**: Chamadas a código de bibliotecas de terceiros MUST ser registradas como relações "externas" com o nome qualificado (biblioteca e método), sem que o modelo leia o interior da biblioteca; elas MUST estar disponíveis às consultas e às specs 006 e 007.
- **FR-014**: Chamadas de API do frontend com caminho montado por partes MUST ser registradas com a parte fixa e a parte variável marcada, sinalizadas como "parciais", e MUST poder ser casadas com endpoints do backend; o modelo MUST NOT calcular nem inventar o valor da variável.
- **FR-015**: O modelo MUST ser reconstruído a cada auditoria e MUST NOT ser guardado entre auditorias; apenas o resumo de cobertura e o tempo de construção MUST ser guardados com o resultado da auditoria (para a medição da spec 003).

### Entidades principais

- **Modelo estrutural**: representação do repositório (unidades, relações, pontos de entrada, acessos a dados), com versão e data.
- **Unidade**: classe, método/função ou componente, com localização e metadados.
- **Relação**: chamada, uso, herança ou importação entre unidades; pode estar "resolvida" ou "não resolvida".
- **Ponto de entrada**: rota HTTP, endpoint, tarefa agendada ou evento.
- **Consulta**: pergunta estrutural e sua resposta determinística.
- **Relatório de cobertura**: o que foi modelado e o que ficou de fora, com motivo.
- **Regra piloto**: regra reescrita sobre o modelo, com o veredito da spec 003.

## Critérios de sucesso *(obrigatório)*

### Resultados mensuráveis

- **SC-001**: Em repositórios de teste com estrutura conhecida, 100% dos endpoints, chamadas e anotações esperados são devolvidos, e nenhum item extra é inventado.
- **SC-002**: Duas construções do modelo sobre o mesmo código dão resultado 100% idêntico.
- **SC-003**: O relatório de cobertura existe para todos os repositórios do conjunto de treino e informa, para cada tipo de construção, o percentual modelado e o motivo do que ficou de fora.
- **SC-004**: Das três regras do piloto, as que receberem "fica" têm a melhora registrada com intervalo; as que receberem "sai" ou "inconclusivo" não substituem a regra atual (um resultado negativo honesto também conta como entrega).
- **SC-005**: O tempo adicional de auditoria fica dentro do orçamento que João fixar depois da linha de base, em 100% dos repositórios do conjunto de treino.
- **SC-006**: Nenhuma regra atual é desligada nem enfraquecida; o CI continua verde.

### Como cada critério é verificado (job do CI)

| Critério | Prova |
|---|---|
| SC-001, SC-002 | Testes de unidade sobre repositórios de teste pequenos e conhecidos (jobs de testes da API e da JVM) |
| SC-003, SC-005 | Medição da spec 003, com cobertura e tempo, sobre o conjunto de treino |
| SC-004 | Comparação antes × depois da spec 003 para cada regra piloto |
| SC-006 | Jobs de regras, Semgrep e benchmark existentes |

Os nomes exatos dos jobs ficam para o `/speckit-plan`.

## Premissas

- A **escolha da técnica** para construir o modelo (motor próprio, ferramenta de terceiros já usada, biblioteca de análise sintática) **fica para o plano**, onde será decidida com base em medida e respeitando a regra do plano de não introduzir tecnologia nova antes da data combinada. A spec descreve o que o modelo faz, não como.
- O Joern, hoje opcional para Java, continua existindo; o novo modelo convive com ele e é comparado com ele pela spec 003.
- A spec 003 está pronta antes de qualquer regra do piloto ser medida.
- O orçamento de tempo e as metas numéricas não são fixados aqui.
- As três regras do piloto são escolhidas a partir dos falsos positivos e falsos negativos já registrados em `precisao.md` e nos gabaritos, sem ler repositórios de validação ou reservados.
- Fora do escopo: consulta pela API e pelo painel (exigiria autorização do João por mudar o contrato público); rastrear dados inseguros entre arquivos (spec 006); verificar arquitetura (spec 007); novas pilhas de linguagem.
