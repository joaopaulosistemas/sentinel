# Especificação: Escopo front-end e back-end, o que falta fechar

**Branch da funcionalidade**: `001-separar-regras-front-back`
**Criada em**: 2026-11-12 · **Revisada em**: 2026-11-12 (após ler o código)
**Status**: Esclarecida (pronta para `/speckit-plan`)
**Origem**: pedido do João: separar as regras do Sentinel em front-end e back-end, refletir isso na tela Motor de regras e permitir que um projeto só Java desligue tudo de front (e o contrário).

> **Correção.** A primeira versão desta spec descrevia a funcionalidade inteira como se não existisse. Ao ler o código, vimos que ela já está na `main` desde o Dashboard 12.48.0 (um lado, `back`, `front` ou `comum`, em cada regra e analisador) e 12.49.0 (campo `disableSides` por sistema, bloco "Escopo da auditoria" na aba Motor de regras, abas por lado). Esta versão descreve **só o que ainda falta**, e o que já existe aparece como linha de base. Fontes: `docs/evolucao/LOG.md` (entradas 12.48.0 e 12.49.0) e o código em `audit-api/.../pack/RuleSide.java`, `SystemTuning.java` e `AuditPolicy.java`.

## Linha de base: o que já funciona

- Cada regra tem um lado: `metadata.side` na regra, ou dedução pela linguagem e pelos caminhos (`RuleSide`). 255 regras marcadas na 12.48.0: 142 back, 89 front, 24 comuns.
- O ajuste do sistema tem `disableSides` (`back` e/ou `front`, nunca os dois). Lado desligado tira as regras do lado e não roda os analisadores do lado (PMD, OWASP-Dependency e Evidências no back; ESLint e código morto no front). CleanCode e Duplicação perdem só os achados dos arquivos do lado desligado.
- Regras de Docker, Kubernetes e IaC são "comuns" e continuam sempre ligadas. Sistemas antigos continuam iguais.
- A aba Motor de regras mostra o escopo, as abas Back-end, Front-end e Infra e comum, e a coluna Lado.

## Clarifications

### Session 2026-11-12

- Q: As regras de Docker, Kubernetes, SQL e infraestrutura como código ganham interruptor próprio, ficam sempre ligadas ou entram no back-end? → A: Sempre ligadas, fora do interruptor. (Já é assim hoje.)
- Q: O que acontece quando uma regra nova é adicionada sem informar o grupo? → A (João, antes de sabermos que a dedução existia): o pacote falha ao carregar. **Substituída em 2026-11-12**, por delegação do João ("escolha a melhor"): manter a dedução atual e acrescentar um teste de guarda (ver história 3). Motivo: falhar ao carregar quebraria pacotes de terceiros e o princípio V da constituição (plug-and-play), e as 255 regras atuais já passam pela dedução.
- Q: O interruptor também controla as ferramentas de mercado de uma tecnologia só? → A: Sim; as de uma tecnologia só acompanham o grupo, as que servem aos dois lados ou à infraestrutura ficam sempre ligadas.
- Q: O Ranking de Sistemas deve avisar quando um sistema tem um grupo desligado? → A: Sim, com um selo ao lado da nota (ranking e relatórios), sem mudar o cálculo da nota.

## Cenários de usuário e testes *(obrigatório)*

### História 1 — Ver no ranking e nos relatórios que um lado está desligado (Prioridade: P1)

Quem olha o Ranking de Sistemas ou um relatório vê, ao lado da nota, que o sistema foi auditado sem o front-end ou sem o back-end, e não compara essa nota com a de um sistema completo.

**Por que P1**: sem o aviso, um sistema com metade das regras desligadas parece melhor do que realmente é (princípio IV, honestidade nos números).

**Teste independente**: desligar o front-end de um sistema e abrir o ranking e o relatório; o selo "front-end desligado" aparece, e a nota é a mesma que o cálculo de hoje dá.

**Cenários de aceite**:

1. **Dado** um sistema com `disableSides` contendo `front`, **quando** o Ranking é aberto, **então** a linha dele mostra o selo "front-end desligado" ao lado da nota.
2. **Dado** o mesmo sistema, **quando** o relatório dele (e o relatório de todos os sistemas) é gerado, **então** o selo aparece ao lado da nota.
3. **Dado** um sistema com os dois lados ligados, **quando** o ranking e os relatórios são abertos, **então** nenhum selo aparece e nada muda.
4. **Dado** qualquer sistema, **quando** o selo existe, **então** o valor da nota é idêntico ao calculado antes desta funcionalidade.

---

### História 2 — Ferramentas externas acompanham o lado desligado (Prioridade: P1)

Num projeto só Java com o front-end desligado, ferramentas externas que analisam só front-end (por exemplo Stylelint) não rodam, e o resumo da auditoria diz que foram desligadas por causa do lado.

**Por que P1**: hoje as ferramentas externas dos pacotes (Stylelint, dependency-cruiser, Checkstyle) não têm lado e contam como "comum"; um projeto sem front ainda pode receber achados de front por elas.

**Teste independente**: desligar o front-end e auditar um projeto com arquivos de estilo; nenhuma ferramenta de front roda e nenhum achado dela aparece.

**Cenários de aceite**:

1. **Dado** uma ferramenta externa declarada com lado `front` e um sistema com `front` desligado, **quando** ele é auditado, **então** a ferramenta não roda e o resumo registra "lado front-end desligado".
2. **Dado** uma ferramenta que serve aos dois lados ou à infraestrutura (declarada `common` ou sem lado dedutível), **quando** qualquer lado é desligado, **então** ela continua rodando.
3. **Dado** uma ferramenta nova sem `side`, **quando** o pacote é carregado, **então** o lado é deduzido dos padrões de `applies-to`, como nas regras, e na dúvida é comum.

---

### História 3 — Regra de código esquecida como "comum" é barrada por teste (Prioridade: P2)

Quem escreve uma regra nova de código sem `metadata.side` descobre, no teste dos pacotes, que a dedução a classificou como "comum" sem ser de infraestrutura, e é obrigado a declarar o lado.

**Por que P2**: a dedução cai em "comum" na dúvida; sem o teste, uma regra de front esquecida escaparia do interruptor sem ninguém perceber.

**Teste independente**: adicionar uma regra de exemplo, com linguagens mistas e sem `side`, a um pacote de teste; o teste de guarda falha apontando a regra.

**Cenários de aceite**:

1. **Dado** uma regra de código dedutida como comum sem declarar `metadata.side`, **quando** os testes dos pacotes rodam, **então** falham com o id da regra.
2. **Dado** uma regra de Docker, Kubernetes ou IaC, **quando** os mesmos testes rodam, **então** passam sem declarar nada.
3. **Dado** as regras atuais, **quando** o teste novo entra, **então** todas passam; as que precisarem ganham `metadata.side` explícito no mesmo PR.

---

### História 4 — Desligar um lado também economiza tempo no Opengrep (Prioridade: P3)

Hoje o Opengrep lê as regras dos dois lados e os achados do lado desligado são filtrados depois (corretos, mas sem ganho de tempo). O objetivo é não executar as regras do lado desligado.

**Por que P3**: é ganho de desempenho, não de correção, e o próprio LOG o lista como "não feito".

**Teste independente**: auditar o mesmo projeto com o front-end desligado e conferir que o conjunto de achados é idêntico ao de hoje, com menos regras executadas.

**Cenários de aceite**:

1. **Dado** um sistema com um lado desligado, **quando** o Opengrep roda, **então** as regras desse lado não são executadas.
2. **Dado** o mesmo sistema, **quando** a auditoria termina, **então** os achados são exatamente os que o filtro posterior produz hoje.

### Casos de borda

- Pacotes mistos (`core-frontend`, `core-security`, `core-quality`, `core-boas-praticas`) continuam sendo classificados **por regra**, nunca por pacote.
- Sistemas já cadastrados continuam com tudo ligado e sem selo.
- O selo nunca altera nota, severidade nem contagem de achados.
- Os dois lados não podem ser desligados juntos (regra já existente em `TuningJson`).
- Ferramentas SARIF em modo `input` (que só leem arquivos já gerados) também respeitam o lado declarado.

## Requisitos *(obrigatório)*

### Requisitos funcionais

- **FR-001** (já atendido): cada regra de código pertence a um lado; Docker, Kubernetes, SQL e IaC são comuns e nunca desligadas pelo interruptor.
- **FR-002** (já atendido): o usuário liga e desliga cada lado por sistema, no cadastro do sistema.
- **FR-003** (já atendido): lado desligado impede a execução e o aparecimento de achados das regras e analisadores do lado.
- **FR-004** (já atendido): a aba Motor de regras mostra as regras por lado, com o visual atual.
- **FR-005** (já atendido): o lado é declarado na própria regra (`metadata.side`) ou deduzido.
- **FR-006** (já atendido): sistemas existentes continuam com tudo ligado e produzindo o mesmo resultado.
- **FR-007** (já atendido): regras que o usuário adiciona por sistema entram na mesma dedução de lado.
- **FR-008**: cada ferramenta externa de pacote DEVE ter um lado (`side` no YAML ou dedução por `applies-to`); a de lado desligado NÃO DEVE rodar nem gerar achados, e o resumo da auditoria DEVE dizer o motivo. As de lado comum DEVEM rodar sempre.
- **FR-009**: o Ranking de Sistemas e os relatórios DEVEM mostrar, ao lado da nota, um selo com o lado desligado, sem alterar o cálculo da nota.
- **FR-010**: os testes dos pacotes DEVEM falhar quando uma regra de código for dedutida como comum sem declarar `metadata.side`, com o id da regra na mensagem.
- **FR-011** (opcional, P3): o Opengrep NÃO DEVE executar regras de um lado desligado.

### Entidades principais

- **Regra** e **Analisador**: já têm lado.
- **Ferramenta externa de pacote** (`ToolSpec`): passa a ter lado.
- **Ajuste do sistema** (`SystemTuning.disableSides`): já guarda os lados desligados; passa a ser exposto no resumo do sistema.

## Critérios de sucesso *(obrigatório)*

### Resultados mensuráveis

- **SC-001**: com o front-end desligado, nenhuma ferramenta externa de lado `front` roda e nenhum achado dela aparece.
- **SC-002**: em um sistema com os dois lados ligados, o conjunto de achados e a nota ficam idênticos aos da versão anterior.
- **SC-003**: o selo aparece no Ranking e nos relatórios (de um sistema e de todos) em 100 % dos sistemas com lado desligado e em 0 % dos demais.
- **SC-004**: o teste de guarda falha para uma regra de código sem lado declarado que a dedução classifique como comum, e passa com as regras atuais.
- **SC-005**: os testes de precisão e calibração existentes (OWASP Benchmark, `calibracao.sh`) continuam passando sem queda, já que a detecção não muda.

## Premissas

- A verificação roda no CI do GitHub (a sessão do Claude na nuvem não roda Maven nem Docker): cada critério acima vira teste que o CI executa.
- A história 4 (P3) só entra se o Opengrep 1.30.0 permitir excluir regras sem copiar arquivos; isso ainda não foi verificado (ver `research.md`).
