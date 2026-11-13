---

description: "Tarefas da spec 003: Sentinel Premium 1 — Medição e prova"
---

# Tarefas: Sentinel Premium 1 — Medição e prova

**Entrada**: documentos em `specs/003-medicao-e-prova/` (plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md)
**Pré-requisitos**: plan.md e spec.md (prontos)
**Testes**: **obrigatórios** (constituição, princípio II): cada fatia traz os testes **antes** do código dela.
**Organização**: por história de usuário; as fatias do plano (A a F) são os PRs.

## Formato: `[ID] [P?] [História] Descrição com caminho`

- **[P]**: pode rodar em paralelo (arquivos diferentes, sem dependência de tarefa incompleta)
- **[USn]**: história da spec (US1 linha de base, US2 veredito, US3 reservado, US4 precisão em código real, US5 lacunas)
- ⚠️ **Autorização**: tarefa que o João precisa liberar antes (mexer em `.github/` ou aprovar lista)
- Toda fatia termina com: entrada em `docs/evolucao/LOG.md` (Verificado / NÃO verificado), versão nova quando mudar comportamento (`audit-dashboard/package.json` + `package-lock.json` linhas 3 e 9 + `python3 -I scripts/sincronizar-versao.py`), e `conferir-numeros.py`, `sincronizar-versao.py --check`, `verificar-separacao.py` verdes.

## Mapa de fatias

| Fatia | Fases | PR |
|---|---|---|
| A | 1, 2, 3 (US1) e 4 (US5) | Régua: estatística, `relatorio`, `lacunas` |
| B | 5 (US2) | `comparar` e propostas |
| C | 6 (US3) | Conjunto reservado |
| D | 7 | Coleta real e linha de base (⚠️ `.github/`) |
| E | 8 (US4) | Amostra e revisão de precisão |
| F | 9 | Fechamento |

---

## Fase 1: Preparação (fatia A)

- [X] T001 Criar a pasta de dados de teste `scripts/fixtures/medicao/` com `medidas/` (3 repositórios pequenos: um Java, um JavaScript, um incompleto), `gabaritos/` e `antes.json`/`depois-melhora.json`/`depois-piora.json`/`depois-igual.json` (números só de teste, com comentário `_comentario` dizendo isso)
- [X] T002 Criar `audit-rules/calibracao/repositorios.json` com um registro por repositório hoje em `conjuntos.json` (treino e validação): `grupo`, `origem`, `pilhas` do escopo fixo e `commit` de 40 caracteres (**provisório**: o do HEAD de hoje; a conferência contra os gabaritos está na tarefa de coleta real). O commit é buscado via `git ls-remote` no GitHub (só leitura de metadados, sem clonar código); se algum não puder ser fixado, o registro leva `commit: null` e o relatório o marca "não reproduzível"
- [X] T003 [P] Criar o esqueleto de `scripts/medicao.py` (subcomandos `relatorio`, `comparar`, `reservado`, `amostra`, `revisao`, `lacunas`, cada um devolvendo "não implementado" com código 2) e o esqueleto de `scripts/test_medicao.py` que carrega o módulo por caminho (compatível com `python3 -I`)
- [ ] T004 ⚠️ Autorização: acrescentar **só** `scripts/test_medicao.py` (o outro arquivo de teste só existe na fatia C) à linha "Testes dos scripts de regras" em `.github/workflows/ci.yml` (mesma mudança de uma linha feita no PR #252)

## Fase 2: Fundação (bloqueia todas as histórias; fatia A)

**⚠️ Nenhuma história começa antes desta fase.**

- [X] T005 [P] Testes de `wilson(k, n)` em `scripts/test_medicao.py`: valores conferidos contra `wilson()` de `scripts/medir-volta.py` para os mesmos k e n; `n = 0` devolve (0, 0) sem dividir por zero; `amostra_pequena` verdadeiro para `n < 20` e falso para `n >= 20`
- [X] T006 [P] Testes de `bootstrap_pareado(antes, depois, semente)` em `scripts/test_medicao.py`: mesma semente dá o mesmo intervalo (byte a byte); melhora clara tem limite inferior > 0; sem efeito inclui 0; listas de tamanhos diferentes são erro
- [X] T007 [P] Teste em `scripts/test_medicao.py`: o módulo `scripts/medicao.py` não chama `subprocess`, `os.system`, `eval` nem `exec` (varredura do código-fonte), garantindo que nenhum código auditado é executado (FR-017, princípio VII)
- [X] T008 [P] Testes de carga e validação de dados em `scripts/test_medicao.py`: `repositorios.json` × `conjuntos.json` (repositório em dois grupos, sem registro, pilha fora do escopo, `commit` com tamanho errado) devolvem erro com mensagem útil
- [X] T009 Implementar `wilson`, `amostra_pequena` (constante única `AMOSTRA_MINIMA = 20`) e `bootstrap_pareado` (10.000 reamostragens, `random.Random(semente)`) em `scripts/medicao.py`
- [X] T010 Implementar `carregar_conjuntos()` e `validar_repositorios()` (regras 1 a 3 de `contracts/arquivos.md`) e `gravar_json()` determinístico (chaves ordenadas, indentação 1, `\n` final) em `scripts/medicao.py`

**Checkpoint**: `python3 -I scripts/test_medicao.py` verde.

---

## Fase 3: História 1 — Onde o Sentinel está hoje, em números (P1) 🎯 MVP (fatia A)

**Meta**: um relatório com detecção, alarme falso e tempo por pilha e categoria, com intervalos e "sem medida".
**Teste independente**: gerar o relatório duas vezes sobre `scripts/fixtures/medicao/` e obter arquivos idênticos; toda pilha do escopo com número ou "sem medida".

### Testes (escrever primeiro; devem falhar)

- [X] T011 [P] [US1] Teste: relatório repetido gera `diff` vazio (SC-002), em `scripts/test_medicao.py`
- [X] T012 [P] [US1] Teste: toda taxa traz `n` e `ic95`; `n < 20` traz `amostra_pequena` (SC-003)
- [X] T013 [P] [US1] Teste: pilha do escopo sem gabarito aparece como `sem-medida` com motivo e fica **fora** das médias (FR-003)
- [X] T014 [P] [US1] Teste: medida incompleta (repositório sem `completa`) não vira zero: entra em pendências com o motivo
- [X] T015 [P] [US1] Teste: o `.md` termina com as seções "Verificado" e "NÃO verificado" (FR-016) e traz versão do Sentinel, ferramentas, data e commit de cada repositório (FR-001)
- [X] T016 [P] [US1] Teste: código gerado e de terceiros excluídos são contados em `excluidos` e não entram nas taxas
- [X] T017 [P] [US1] Teste: `relatorio --conjunto reservado` não deixa nenhuma chave `achados`, `arquivo` ou `linha` por achado na saída; só contagens (FR-006)
- [X] T018 [P] [US1] Teste: corrigir um gabarito ou um número publicado acrescenta uma entrada em `historico` (`data`, `motivo`, `antes`, `depois`) e nunca sobrescreve o valor anterior sem registro (FR-015)

### Implementação

- [X] T019 [US1] Implementar a leitura de medidas por repositório (formato atual de `medir-volta.py extrair` + campos novos de `data-model.md`) e o recall por gabarito reaproveitando a regra de acerto (arquivo, linha com tolerância, regras) em `scripts/medicao.py`
- [X] T020 [US1] Implementar a entrada do OWASP Benchmark (JSON de `benchmark-owasp.py`) como categorias com `tp`, `fp`, `taxa_acerto` e `taxa_alarme_falso` com intervalo em `scripts/medicao.py`
- [X] T021 [US1] Implementar o agrupamento por pilha via `repositorios.json` e o estado `medida`/`sem-medida` em `scripts/medicao.py`
- [X] T022 [US1] Implementar o subcomando `relatorio` (saídas `.json` e `.md`, conforme `contracts/cli.md`) em `scripts/medicao.py`; para `--conjunto reservado` descarta achados individuais (arquivo, linha, regra por achado) e grava só contagens
- [X] T023 [US1] Implementar o tempo e o tamanho por repositório (`duracao_s`, `arquivos`) no relatório, com agregados, em `scripts/medicao.py` (FR-018)
- [X] T024 [US1] Implementar `registrar_correcao()` (histórico de correções de gabarito e de números publicados) em `scripts/medicao.py`
- [X] T025 [US1] Rodar o passo 2 do `quickstart.md` e conferir o `diff` vazio

## Fase 4: História 5 — Lacunas de medição visíveis (P3; fatia A)

**Meta**: cada pilha sem medida diz o que falta e a prioridade.
**Teste independente**: toda pilha `sem-medida` do relatório de teste tem uma linha de lacuna.

- [X] T026 [P] [US5] Teste: `lacunas` lista cada pilha `sem-medida` com `falta` e `prioridade`; ordem: mais repositórios `real` e nenhuma medida primeiro, em `scripts/test_medicao.py`
- [X] T027 [US5] Implementar o subcomando `lacunas` e a seção `lacunas[]` do relatório em `scripts/medicao.py`
- [ ] T028 Fechar a fatia A: LOG, versão nova (ferramenta nova), conferências verdes, PR

**Checkpoint**: fatia A em PR; US1 e US5 entregues e testáveis sozinhas.

---

## Fase 5: História 2 — Veredito: fica, sai ou inconclusivo (P1; fatia B)

**Meta**: critérios registrados antes de medir e veredito com antes e depois lado a lado.
**Teste independente**: com 3 mudanças de teste (melhora, piora, sem efeito) o veredito sai correto (SC-004).

### Testes (primeiro)

- [X] T029 [P] [US2] Teste: `melhora` → "fica" (saída 0); `piora` → "sai" (1); `igual` → "inconclusivo" (3) com os três pares de fixtures
- [X] T030 [P] [US2] Teste: piora de **uma** categoria acima da tolerância dá "sai" mesmo com média melhor (cenário 3)
- [X] T031 [P] [US2] Teste: ganho cujo intervalo inclui zero, ou amostra pequena, nunca dá "fica" (cenário 2)
- [X] T032 [P] [US2] Teste: conjuntos, `commit`s ou versões de ferramentas diferentes além da mudança → recusa com saída 3 e lista do que difere (cenário 4)
- [X] T033 [P] [US2] Teste: proposta sem hash na medição, ou com hash diferente do arquivo atual → `pos_medida` e nunca "fica" (cenário 5, FR-009)
- [X] T034 [P] [US2] Teste: tempo acima do aceito impede "fica"; sem limite declarado só destaca o aumento
- [X] T035 [P] [US2] Teste: proposta cujo `linha_de_base` não existe, ou não aponta para uma medição, é recusada com mensagem clara (SC-007)

### Implementação

- [X] T036 [US2] Implementar `sha256` da proposta e a validação do formato (`contracts/arquivos.md`) em `scripts/medicao.py`, exigindo que `linha_de_base` exista e aponte para uma medição real (SC-007)
- [X] T037 [US2] Implementar a comparabilidade (conjunto, commits, ferramentas) e a recusa explicada em `scripts/medicao.py`
- [X] T038 [US2] Implementar o subcomando `comparar` com bootstrap pareado e o veredito em `scripts/medicao.py`; gravar `docs/evolucao/medicao/vereditos/<proposta>.json`
- [X] T039 [P] [US2] Criar `audit-rules/calibracao/propostas/LEIAME.md` com o formato da proposta e um exemplo; as propostas reais nascem nas specs 004 a 007
- [X] T040 [US2] Garantir que as travas atuais seguem rodando e valendo (FR-012): rodar `benchmark-owasp.py`, `comparar-calibracao.py` e `precisao-regras.py --verifica` contra os dados atuais e registrar que não mudaram
- [X] T041 Fechar a fatia B: LOG, versão, conferências, PR

**Checkpoint**: US2 testável sem as demais.

---

## Fase 6: História 3 — Conjunto reservado que ninguém lê (P1; fatia C)

**Meta**: terceiro grupo protegido pelo CI, com registro de consultas.
**Teste independente**: mover um reservado para treino, citá-lo fora da lista permitida ou editar linha antiga do registro faz o CI falhar.

### Testes (primeiro)

- [ ] T042 [P] [US3] Criar `scripts/test_verificar_separacao.py` com fixtures em `scripts/fixtures/medicao/separacao/` e teste: repositório em dois grupos, incluindo o reservado, é erro (checagem 5)
- [ ] T043 [P] [US3] Teste: gabarito do reservado fora de `gabaritos/reservado/` ou sem `fonte` em alguma falha é erro (checagem 6)
- [ ] T044 [P] [US3] Teste: nome de repositório reservado em arquivo fora da lista permitida é erro (checagem 7)
- [ ] T045 [P] [US3] Teste: `consultas.json` com linha editada ou removida em relação à base é erro; linha acrescentada é aceita (checagem 8)
- [ ] T046 [P] [US3] Teste: medição do reservado com a chave `achados`, `arquivo` ou `linha` é erro na checagem 7 (FR-006)
- [ ] T047 [P] [US3] Teste: `promover` move reservado → treino, atualiza `conjuntos.json`, `repositorios.json`, `estado.json` e o registro, e o repositório nunca volta a reservado
- [ ] T048 [P] [US3] Teste: medir o reservado sem `--marco` é recusado; com marco, grava a consulta (data, versão, marco, motivo)

### Implementação

- [ ] T049 [US3] Estender `scripts/verificar-separacao.py` com as checagens 5 a 8 (a 7 também barra, em medições do reservado, as chaves `achados`, `arquivo` e `linha`) mantendo as opções e os códigos de saída atuais; manter a leitura de `descobertos.txt`
- [ ] T050 ⚠️ Autorização: acrescentar `scripts/test_verificar_separacao.py` à linha de testes dos scripts em `.github/workflows/ci.yml` (nasce aqui, não na fatia A)
- [ ] T051 [US3] Estender `conjuntos.json` com o grupo `reservado` (`repositorios_fixos`, `regra`, `marcos`) e `audit-rules/calibracao/gabaritos/reservado/LEIAME.json` com a regra de conduta (ninguém lê o código; gabarito só de fonte pública)
- [ ] T052 [US3] Implementar `reservado consultas` e `reservado promover` e o registro `audit-rules/calibracao/reservado/consultas.json` em `scripts/medicao.py`
- [ ] T053 ⚠️ Autorização: **propor a lista inicial de repositórios reservados** só por metadados públicos (linguagem, tamanho, atividade, existência de lista pública de falhas), **sem abrir o código**, com a fonte pública do gabarito de cada um; o João aprova a lista antes de ela entrar em `conjuntos.json`
- [ ] T054 [US3] Incluir a lista aprovada e atualizar o hash com `python3 -I scripts/verificar-separacao.py --atualizar-hash`
- [ ] T055 Fechar a fatia C: LOG, versão, conferências, PR

**Checkpoint**: US3 testável sozinha; o CI de separação segue verde.

---

## Fase 7: Coleta real e linha de base (fatia D; completa US1)

**Meta**: medir de verdade, no GitHub, treino, validação e o reservado uma única vez (marco 0).
**Depende de**: fatias A, B e C. **Só se prova no GitHub.**

- [ ] T056 [P] Estender `scripts/auditoria-completa.sh` para escrever `Duração (s)` e `Arquivos de código` no resumo sem alterar as linhas atuais; teste com saída simulada em `scripts/test_medicao.py` (leitura dos campos novos)
- [ ] T057 ⚠️ Autorização: criar `.github/workflows/medicao.yml` (disparo manual; busca cada repositório **no commit fixado**, roda a auditoria completa, publica as medidas como artefato; permissões mínimas e ações fixadas por SHA como os demais; sem executar código dos alvos)
- [ ] T058 Rodar o workflow para treino e validação; conferir que as medidas são completas ou têm motivo de incompletude
- [ ] T059 Conferir cada gabarito de treino no **commit fixado**: comparar o recall medido com o da calibração anterior (`audit-rules/calibracao/baselines/`); se divergir além da tolerância, fixar em `repositorios.json` o commit em que o gabarito foi montado e registrar em `historico` (os gabaritos foram montados em commits antigos; linhas podem ter deslocado)
- [ ] T060 ⚠️ Autorização: rodar o workflow **uma vez** para o reservado no marco `marco-0-linha-de-base`, registrando a consulta
- [ ] T061 Gerar `docs/evolucao/medicao/linha-de-base.{json,md}` com `medicao.py relatorio` para os três conjuntos e as lacunas; o João revisa
- [ ] T062 Repetir a medição de um repositório e conferir números idênticos (SC-002 em ambiente real) e que o CI existente segue verde (SC-009)
- [ ] T063 Fechar a fatia D: LOG com **Verificado / NÃO verificado**, versão, conferências, PR

---

## Fase 8: História 4 — Precisão em código real, confirmada por pessoa (P2; fatia E)

**Meta**: precisão por regra só com vereditos humanos, concordância com o agente.
**Teste independente**: amostra de teste com vereditos preenchidos produz precisão, pendentes e concordância corretos.

### Testes (primeiro)

- [ ] T064 [P] [US4] Teste: mesma semente dá a mesma amostra; regras com mais achados são cobertas; cada achado traz localização e trecho (cenário 1, FR-013)
- [ ] T065 [P] [US4] Teste: `--cego` omite o veredito do agente e não altera as demais colunas
- [ ] T066 [P] [US4] Teste: precisão por regra usa só linhas com veredito, informa quantas faltam e marca "sem evidência" abaixo do mínimo (cenário 2)
- [ ] T067 [P] [US4] Teste: concordância e lista de divergências; abaixo de 0,80 a saída é 1 (cenário 3)
- [ ] T068 [P] [US4] Teste: o formato de linha de `docs/evolucao/revisao-humana.md` continua aceito por `scripts/concordancia-revisao.py` (compatibilidade)

### Implementação

- [ ] T069 [US4] Criar `audit-rules/calibracao/codigo-real.json` com projetos abertos reais (Java/Spring e Angular primeiro), escolhidos por metadados públicos; ⚠️ o João aprova a lista
- [ ] T070 [US4] Implementar `amostra` (com `--cego`) e `revisao` em `scripts/medicao.py`
- [ ] T071 [US4] Gerar a primeira amostra (100 achados em 10 repositórios, critério do placar) e atualizar `docs/evolucao/revisao-humana.md`; **a revisão em si é do João** e fica registrada como pendente
- [ ] T072 Fechar a fatia E: LOG, versão, conferências, PR. SC-008 só fecha quando os vereditos existirem

---

## Fase 9: Fechamento (fatia F)

- [ ] T073 [P] Atualizar `docs/evolucao/PLACAR.md` (medição independente, conjunto reservado) apenas com a evidência gerada; sem subir nota sem evidência
- [ ] T074 [P] Atualizar `docs/sdd.md` e `docs/evolucao/AGENTE.md` com o uso de `medicao.py` e do reservado (regra: o agente `sentinel-ciclo` nunca lê o reservado)
- [ ] T075 [P] Atualizar `specs/003-medicao-e-prova/spec.md` (status) e, se o código divergiu, `plan.md`
- [ ] T076 Rodar o `quickstart.md` inteiro e registrar o que ficou pendente (SC-008, passo 7)
- [ ] T077 Fechar a fatia F: LOG, conferências, PR

---

## Dependências e ordem de execução

- **Fase 1 → Fase 2** (bloqueia tudo) → **US1** → **US5**; **US2** e **US3** dependem só da Fase 2 e podem ir em paralelo à US1 se houver mais de uma pessoa; **Fase 7** depende de A, B e C; **US4** depende de A e da lista de código real; **Fase 9** por último.
- Dentro de cada fase: testes (falham) → implementação → fechamento.
- Tarefas ⚠️ param e perguntam: T004, T050, T053, T057, T060, T069.

### Oportunidades de paralelismo

- Fase 2: T005 a T008 juntas.
- US1: T011 a T018 juntas.
- US2: T029 a T035 juntas; US3: T042 a T048 juntas; US4: T064 a T068 juntas.

## Estratégia de implementação

- **MVP**: fatia A (US1 + US5) já dá a régua automática sobre as medidas existentes.
- **Entrega incremental**: A → B → C → D → E → F; cada PR com CI verde e autorização do João para o merge.
- **Antes de implementar**: rodar `/speckit-analyze` (não foi feito na spec 001).
- **Honestidade**: nenhuma meta numérica é inventada; elas nascem depois de T061 (linha de base).
