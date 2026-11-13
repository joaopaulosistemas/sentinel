# Especificação: Sentinel Premium 2 — Menos alarme falso e só o que é novo no PR

**Ramo**: `spec/004-menos-alarme-falso` | **Criada**: 2026-11-12 | **Status**: Esclarecida (5 perguntas, 2026-11-13); próximo: /speckit-plan
**Pedido**: "Sentinel Premium — Plano de Ação", eixo **precisão**: menos alarmes falsos e, em pull request, só os achados que o PR introduziu. Depende da spec 003 (régua): nenhuma redução de ruído vale sem a medida de que a detecção não caiu.

## Linha de base: o que já existe (levantado no repositório em 2026-11-12)

- **Modo sombra**: regra com status de rascunho ou experimental, ou marcada no perfil, aparece no relatório mas não pesa na nota nem derruba o pipeline. Existe também supressão de achado.
- **Precisão medida**: `docs/evolucao/precisao.md` guarda veredito por regra. Há um verificador que só aceita rebaixar uma regra no perfil se houver amostra mínima e precisão baixa medida, e a regra não tiver sido corrigida depois da amostra. Exemplos do arquivo: `sentinel-ts-console-log` com 4 verdadeiros de 12 revisados; `eslint-security/detect-non-literal-fs-filename` com 0 de 6.
- **Código gerado**: o mesmo arquivo registra que 3.320 dos 3.368 achados de `sentinel-ts-any-explicito` estavam em arquivos com cabeçalho "não edite". Já existe exclusão de bibliotecas embutidas (jQuery etc.), mas não de código gerado em geral.
- **Modo PR**: a auditoria com branch base mantém os achados dos **arquivos alterados**, não das **linhas** alteradas. Um arquivo antigo com 40 achados comenta os 40 em qualquer PR que o toque.
- **Comparação entre auditorias**: existe a rota de diferença (novos, resolvidos, persistentes) contra a auditoria anterior, mas o fluxo de PR não a usa para silenciar o que já existia.
- **Já existe no código** (levantado em 2026-11-13, ver `research.md`): identidade do achado sem linha (`Fingerprints`), marca de "novo" contra a última auditoria completa (`Baseline`), detecção de código gerado por cabeçalho (`GeneratedFiles`, que hoje só rebaixa a confiança) e nível de confiança por sinais do achado (`FindingConfidence`).
- **O que falta**: critério único de "novo ou piorado" por achado (hoje a base só guarda identidades, não severidade nem contagem) e tratamento de renomeação; exclusão de código gerado que não seja segurança da nota e do comentário; agrupamento de achados repetidos; faixa de confiança **da regra** vinda da precisão medida; e prova de que cada redução de ruído não custou detecção.

## Esclarecimentos

### Sessão 2026-11-13

- Q: Quais são os cortes numéricos das faixas de confiança de cada achado? → A: sobre o limite inferior do intervalo de confiança de 95 % da precisão medida: alta ≥ 80 %, média de 50 % a 79 %, baixa < 50 %; sem amostra suficiente, "sem medida".
- Q: Achado de segurança que já existia, em arquivo alterado pelo PR, aparece no comentário? → A: não; o comentário traz só novos ou piorados e uma linha de contagem dos antigos deixados de fora ("N achados antigos fora deste comentário"); os antigos seguem no painel e no relatório completo.
- Q: O que conta como achado "piorado" num PR? → A: a severidade do achado subiu, ou a quantidade de ocorrências da mesma regra no mesmo arquivo aumentou (o excedente é o piorado).
- Q: Em código gerado, o que é "estilo e qualidade" (sai da nota) e o que é "segurança" (continua)? → A: vale a categoria que a regra já tem no catálogo: só o que a regra classifica como segurança permanece; estilo, qualidade, manutenção e desempenho saem. Sem classificação nova.
- Q: Quando não dá para decidir o que é novo (base muito atrás, histórico reescrito), o que o Sentinel faz? → A: avisa no comentário e usa o comportamento atual por arquivo; não bloqueia o PR por não ter decidido, e nunca omite achado em silêncio.

## Cenários de usuário e testes *(obrigatório)*

### História 1 — O PR mostra só o que ele introduziu (Prioridade: P1)

Quem abre um PR vê no comentário e nas anotações apenas os achados que o PR criou ou piorou. Um arquivo que já tinha problemas antes não gera comentário sobre eles, mesmo que o PR o altere. Os achados antigos continuam existindo no painel, só não poluem a revisão.

**Por que P1**: ruído em PR é o que faz a equipe parar de ler o comentário do Sentinel.

**Teste independente**: PR de teste que muda uma linha num arquivo com achados antigos: o comentário traz só o achado novo (e nenhum dos antigos).

**Cenários de aceite**:

1. **Dado** um arquivo com achados pré-existentes, **quando** um PR altera uma linha sem criar achado, **então** o comentário diz "nenhum achado novo" e não lista os antigos.
2. **Dado** um PR que cria um achado numa linha nova, **quando** o Sentinel audita, **então** só esse achado aparece, com localização.
3. **Dado** um PR que move um trecho antigo de lugar sem mudá-lo, **quando** o Sentinel audita, **então** o achado movido não é tratado como novo.
4. **Dado** um PR que piora um achado existente (a severidade subiu, ou a mesma regra passou a ter mais ocorrências no mesmo arquivo), **quando** o Sentinel audita, **então** ele aparece como "piorou", com o antes e o depois (no caso de mais ocorrências, só o excedente).

---

### História 2 — Ruído conhecido sai de cena, com prova (Prioridade: P1)

Código gerado (marcado como não editável, pastas de saída de gerador) e regras com baixa precisão **medida** deixam de pesar no relatório e na nota. A regra de decisão é a da spec 003: a redução só fica se a detecção nos conjuntos de treino e validação não cair além da tolerância.

**Por que P1**: é o ganho direto de "menos alarme falso" e a prova de que não escondemos problema real.

**Teste independente**: ligar a exclusão de código gerado e o rebaixamento por precisão num conjunto de teste: o total de achados cai e o recall do gabarito continua dentro da tolerância; o veredito da 003 sai "fica".

**Cenários de aceite**:

1. **Dado** um arquivo com cabeçalho de código gerado, **quando** o Sentinel audita, **então** seus achados de estilo e qualidade não entram na nota e o relatório diz quantos foram deixados de fora e por quê.
2. **Dado** uma regra com amostra suficiente e precisão abaixo do limite, **quando** o perfil é aplicado, **então** a regra passa a sombra com a medida citada; sem amostra suficiente, nada muda.
3. **Dado** que a exclusão faria perder uma falha conhecida do gabarito, **quando** a medição roda, **então** o veredito é "sai" e a exclusão não é liberada.
4. **Dado** um achado de **segurança** em código gerado, **quando** o Sentinel audita, **então** ele continua aparecendo (só ruído de estilo e qualidade é excluído).

---

### História 3 — Achados repetidos viram um só (Prioridade: P2)

Quando a mesma causa gera dezenas de achados (a mesma regra no mesmo padrão, em muitos lugares), o relatório mostra um item com a contagem e a lista de locais, em vez de dezenas de linhas.

**Por que P2**: reduz a leitura sem esconder nada: a contagem e os locais continuam acessíveis.

**Teste independente**: um repositório de teste com o mesmo problema em 30 lugares aparece como 1 item de 30 ocorrências, e a nota continua a mesma de antes do agrupamento.

**Cenários de aceite**:

1. **Dado** uma regra que dispara muitas vezes pela mesma causa, **quando** abro o relatório, **então** vejo um item com a contagem e a lista expansível de locais.
2. **Dado** o agrupamento, **quando** comparo a nota, **então** ela não muda por causa do agrupamento.

---

### História 4 — Cada achado diz quão confiável é (Prioridade: P2)

Cada achado traz uma indicação de confiança baseada na precisão **medida** da regra (alta, média, baixa ou "sem medida"), para quem lê saber por onde começar.

**Por que P2**: transforma o resultado da spec 003 em informação útil na ponta; mas só funciona depois que há medida.

**Teste independente**: regras com precisão conhecida de teste recebem a faixa certa; regra sem amostra mostra "sem medida".

**Cenários de aceite**:

1. **Dado** uma regra com precisão medida, **quando** o achado aparece, **então** a faixa é decidida pelo limite inferior do intervalo de 95 % da precisão (alta ≥ 80 %, média de 50 % a 79 %, baixa < 50 %) e o achado cita a amostra e o intervalo.
2. **Dado** uma regra sem amostra suficiente, **quando** o achado aparece, **então** a confiança é "sem medida", nunca um valor inventado.

---

### Casos de borda

- Arquivo renomeado ou movido no PR: o achado antigo não vira novo só por mudar de caminho.
- Branch base muito atrás ou histórico reescrito: o Sentinel avisa no comentário que não consegue decidir o que é novo e cai para o comportamento atual (por arquivo); o aviso não bloqueia o PR.
- Mudança que apenas formata o arquivo (espaços, quebras de linha): não cria achados novos.
- Achado que muda de linha porque o código acima mudou: continua sendo o mesmo achado.
- Regra corrigida depois da amostra de precisão: a medida antiga não vale para a regra nova (já é a regra do verificador atual).
- Supressão existente de um achado antigo: continua valendo e não reaparece como novo.
- Primeira auditoria de um sistema (sem base): tudo é "existente"; o PR mostra o que o PR introduz comparado à base da branch.

## Requisitos *(obrigatório)*

### Requisitos funcionais

- **FR-001**: Em auditoria de PR, o Sentinel MUST comentar e anotar apenas achados **novos ou piorados** pelo PR, decididos por achado, e não por arquivo alterado.
- **FR-002**: Um achado MUST ser considerado o mesmo, e portanto não novo, quando só mudou de linha, de caminho por renomeação, ou por reformatação.
- **FR-003**: Quando não for possível decidir o que é novo, o Sentinel MUST dizer isso no comentário e no resultado e usar o comportamento atual por arquivo, nunca omitir achados em silêncio; não poder decidir MUST NOT, por si só, bloquear o PR.
- **FR-004**: Os achados antigos MUST continuar visíveis no painel e no relatório completo; a regra "só o novo" vale para o comentário e as anotações do PR.
- **FR-005**: Achados de estilo e qualidade em código gerado MUST ficar fora da nota e do comentário, com a contagem excluída e o motivo no relatório; achados de segurança em código gerado MUST continuar aparecendo. O corte usa a categoria que a regra já tem no catálogo: só o que a regra classifica como segurança permanece; estilo, qualidade, manutenção e desempenho saem.
- **FR-006**: Rebaixar uma regra por baixa precisão MUST exigir medida registrada (amostra mínima e precisão abaixo do limite) e citá-la; sem medida, a regra não muda.
- **FR-007**: Achados repetidos pela mesma causa MUST poder ser mostrados agrupados, com contagem e locais; o agrupamento MUST NOT alterar a nota.
- **FR-008**: Cada achado MUST mostrar a confiança da regra derivada da precisão medida, ou "sem medida". A faixa sai do limite inferior do intervalo de confiança de 95 %: alta ≥ 80 %, média de 50 % a 79 %, baixa < 50 %.
- **FR-009**: Toda redução de ruído desta spec MUST ser avaliada pelo veredito da spec 003, com critérios registrados antes; só "fica" libera a mudança para o padrão.
- **FR-010**: Nenhuma redução de ruído MUST reduzir a detecção de falhas conhecidas nos conjuntos de treino e validação além da tolerância já usada pelas travas do projeto.
- **FR-011**: As funcionalidades atuais (modo sombra, supressão, rota de diferença entre auditorias) MUST continuar funcionando como hoje para quem não ativar as novas.
- **FR-012**: Todo relatório de PR MUST dizer quantos achados foram deixados de fora por serem antigos, para o corte ser auditável, numa linha de contagem ("N achados antigos fora deste comentário"). O comentário não lista nem resume achados antigos, nem os de segurança; eles ficam no painel e no relatório completo (FR-004).

### Entidades principais

- **Achado novo / piorado / existente**: classificação de um achado em relação à base do PR. "Piorado" = severidade subiu, ou a quantidade de ocorrências da mesma regra no mesmo arquivo aumentou (o excedente).
- **Identidade do achado**: o que faz dois achados serem "o mesmo" entre duas versões do código.
- **Faixa de confiança**: alta, média, baixa ou sem medida, derivada do limite inferior do intervalo de 95 % da precisão revisada da regra.
- **Exclusão por código gerado**: registro do que foi excluído, quanto e por quê.
- **Grupo de achados**: conjunto de ocorrências com a mesma causa, com contagem e locais.

## Critérios de sucesso *(obrigatório)*

### Resultados mensuráveis

- **SC-001**: Em PRs de teste com alterações em arquivos que já tinham achados, 100% dos achados antigos ficam fora do comentário e 100% dos novos aparecem.
- **SC-002**: Em PRs de teste que só reformatam, movem ou renomeiam arquivos, 0 achados novos são criados por isso.
- **SC-003**: A detecção de falhas conhecidas nos conjuntos de treino e validação não cai além da tolerância das travas existentes depois de todas as mudanças desta spec (verificado pela spec 003).
- **SC-004**: Cada redução de ruído tem veredito "fica" da spec 003 antes de entrar no padrão; as que receberem "sai" ou "inconclusivo" não entram.
- **SC-005**: A redução do número de achados em comentários de PR, em código real, atinge a meta que João fixar depois da linha de base da spec 003; a medida registra o antes e o depois com os mesmos conjuntos.
- **SC-006**: 100% das regras rebaixadas por esta spec citam a amostra e a precisão que justificaram.
- **SC-007**: O agrupamento não altera a nota em nenhum dos repositórios de teste.

### Como cada critério é verificado (job do CI)

| Critério | Prova |
|---|---|
| SC-001, SC-002, SC-007 | Testes de unidade e de integração com repositório de teste e PR simulado (jobs de testes da API e integração HTTP) |
| SC-003, SC-004 | Medição da spec 003 sobre treino e validação (jobs de benchmark e de separação, estendidos) |
| SC-005 | Comparação antes × depois da spec 003 em código real; meta numérica fixada por João |
| SC-006 | Verificador de precisão já existente, estendido (job de testes dos scripts) |

Os nomes exatos dos jobs ficam para o `/speckit-plan`.

## Premissas

- A spec 003 está pronta (linha de base, veredito, conjunto reservado) antes de qualquer mudança desta spec ser medida.
- A meta numérica de redução de ruído **não é fixada aqui**: João a define depois da linha de base.
- "Código gerado" inclui arquivos com cabeçalho de não edição e pastas padrão de saída de geradores; a lista exata fica para o plano.
- Achado de segurança nunca é silenciado por ser antigo no painel; a regra "só novo" afeta o que se comenta no PR.
- O que já funciona (sombra, supressão, diferença entre auditorias) é reaproveitado.
- Fora do escopo: criar regras novas; entender melhor o código (specs 005 a 007); revisão externa (spec 008).
