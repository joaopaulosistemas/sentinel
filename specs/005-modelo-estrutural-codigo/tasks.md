---

description: "Tarefas da spec 005: Sentinel Premium 3 — Modelo estrutural do código"
---

# Tarefas: Sentinel Premium 3 — Modelo estrutural do código

**Entrada**: `specs/005-modelo-estrutural-codigo/` (plan.md, spec.md, research.md, data-model.md, contracts/cli.md, quickstart.md)
**Testes**: **obrigatórios** (princípio II): cada fatia traz os testes **antes** do código.
**Organização**: por história; as fatias do plano (A a E) são os PRs. **Nada roda sem o João mandar implementar** (regime atual: só especificação).

## Formato: `[ID] [P?] [História] Descrição com caminho`

- **[P]**: paralelizável (arquivos diferentes, sem dependência pendente)
- **[USn]**: US1 backend, US2 frontend, US3 regras piloto, US4 cobertura, US5 tempo
- ⚠️ **Autorização**: o João precisa liberar antes (dependência nova ou `.github/`)
- Toda fatia termina com entrada em `docs/evolucao/LOG.md` (Verificado / NÃO verificado) e, se mudar comportamento, versão nova (`audit-dashboard/package.json` + `package-lock.json` linhas 3 e 9 + `python3 -I scripts/sincronizar-versao.py`).

## Mapa de fatias

| Fatia | Fase | PR |
|---|---|---|
| A | 1 e 2 | Tipos do modelo, serialização, contrato da CLI |
| B | 3 (US1) | Leitor Java e consultas de backend ⚠️ dependência nova |
| C | 4 (US2) | Leitor TypeScript/JavaScript e frontend |
| D | 5 (US4) e 6 (US5) | Cobertura, tempo, limites |
| E | 7 (US3) | Três regras piloto e medição |

---

## Fase 1: Preparação (fatia A)

- [ ] T001 Criar repositórios de teste pequenos em `audit-api/src/test/resources/modelo/` (spring/, quarkus/, angular/, react/, vue/), cada um com gabarito `esperado.json` e, no de Java, um arquivo com erro de sintaxe, uma chamada por reflexão e uma a `jdbcTemplate`; no de frontend, uma chamada com caminho montado por partes
- [ ] T002 [P] Criar o pacote `audit-api/src/main/java/com/sentinel/audit/modelo/` com `package-info.java` descrevendo que o modelo só lê texto (princípio VII)

## Fase 2: Fundação (bloqueia as histórias; fatia A)

- [ ] T003 [P] Testes de `Unidade`, `Relacao` (estados RESOLVIDA, EXTERNA, NAO_RESOLVIDA, PARCIAL; `motivo` obrigatório em NAO_RESOLVIDA), `PontoDeEntrada` e `AcessoADados` conforme `data-model.md` em `.../modelo/ModeloTipoTest.java`
- [ ] T004 [P] Teste de serialização determinística (chaves ordenadas, ids `arquivo#símbolo`, bytes idênticos em duas execuções; SC-002) em `.../modelo/ModeloJsonTest.java`
- [ ] T005 [P] Teste de varredura do pacote `modelo`: sem `ProcessBuilder` de código auditado, sem compilação, sem chamada de rede nem de IA (FR-011, princípio VII) em `.../modelo/SemExecucaoTest.java`
- [ ] T006 Implementar os tipos do modelo e `ModeloJson` em `.../modelo/`
- [ ] T007 Registrar o comando `modelo` (endpoints, chamadores, anotacao, dados, cobertura) na CLI conforme `contracts/cli.md`, devolvendo "não implementado" até as fatias B a D, em `audit-cli/`
- [ ] T036 [P] Documentar a interface pública do pacote `modelo` (consultas, ids estáveis, estados de relação) em `audit-api/src/main/java/com/sentinel/audit/modelo/package-info.java` e testar que dois consumidores independentes obtêm o mesmo grafo (FR-010; reaproveitável pelas specs 006 e 007) em `.../modelo/InterfacePublicaTest.java` (A2)
- [ ] T008 Teste de regressão: com o comando novo registrado, a saída dos demais comandos da CLI não muda (FR-007)

**Checkpoint**: testes da fatia A verdes; nenhuma API nem tela criada (esclarecimento 1).

---

## Fase 3: História 1 — Consultar o backend por estrutura (P1) 🎯 MVP (fatia B)

**Teste independente**: roteiros 1 a 3 do `quickstart.md`.

- [ ] T009 ⚠️ **Autorização**: adicionar a dependência Maven do leitor sintático de Java (JavaParser) em `audit-api/pom.xml`; sem o "sim" do João, a fatia B não começa
- [ ] T010 [P] [US1] Testes dos endpoints: Spring e Quarkus devolvem método HTTP, caminho, classe e método exatamente como `esperado.json`, sem item extra (SC-001) em `.../modelo/JavaEndpointsTest.java`
- [ ] T011 [P] [US1] Testes de chamadores: diretos, inclusive de outro arquivo e módulo; nome igual em pacotes diferentes distinguido pelo import em `.../modelo/JavaChamadoresTest.java`
- [ ] T012 [P] [US1] Testes de relações especiais: reflexão e injeção por nome saem NAO_RESOLVIDA com motivo; `jdbcTemplate.query` sai EXTERNA com nome qualificado (FR-005, FR-013); Lombok e proxies declarados como não resolvidos em `.../modelo/JavaRelacoesTest.java`
- [ ] T013 [P] [US1] Teste de anotação e de acesso a dados em `.../modelo/JavaAnotacoesDadosTest.java`
- [ ] T014 [US1] Implementar `JavaReader` (só leitura de texto; nada compilado) em `.../modelo/JavaReader.java`
- [ ] T015 [US1] Implementar a resolução por import, pacote e tipo declarado em `.../modelo/Resolucao.java`
- [ ] T016 [US1] Ligar `modelo endpoints`, `chamadores`, `anotacao` e `dados` ao modelo Java na CLI, com `--json`

## Fase 4: História 2 — O mesmo no frontend (P1) (fatia C)

- [ ] T017 [P] [US2] Testes: componentes, rotas e chamadas de API de Angular, React e Vue conferem com o gabarito; caminho `"/clientes/" + id` sai PARCIAL com a variável marcada (FR-014) em `.../modelo/TsModeloTest.java`
- [ ] T018 [P] [US2] Teste de segurança do leitor: o script só chama o parser, nunca `eslint`, nunca carrega configuração do repositório auditado (padrão do `EslintAnalyzer`) em `.../modelo/TsSemConfigTest.java`
- [ ] T019 [P] [US2] Teste: sem Node, o frontend sai `naoModelado` com o motivo e o backend continua (FR-005)
- [ ] T020 [US2] Criar `audit-api/src/main/resources/modelo/leitor-ts.mjs` (só `parse`, saída JSON ordenada)
- [ ] T021 [US2] Implementar `TsReader` que chama o script com limite de tempo e memória, em `.../modelo/TsReader.java`
- [ ] T022 [US2] Ligar a CLI aos pontos de entrada e chamadas de API do frontend e casar chamadas com endpoints do backend, inclusive os parciais

## Fase 5: História 4 — O Sentinel diz o que não entendeu (P2) (fatia D)

- [ ] T023 [P] [US4] Testes de cobertura: arquivo com erro de sintaxe e outro com reflexão aparecem como não modelados com motivo e o restante segue; percentuais por tipo, sem média única; gerado e embutido excluídos e contados (SC-003) em `.../modelo/CoberturaTest.java`
- [ ] T024 [US4] Implementar `Cobertura` reaproveitando `GeneratedFiles` e `VendoredCode`
- [ ] T025 [US4] Guardar só o resumo de cobertura e o tempo com o resultado da auditoria, sem tabela nova nem modelo completo (FR-015) e mostrar em `modelo cobertura`
- [ ] T026 [P] [US4] Teste de que arquivo não modelado continua coberto pela regra de padrão equivalente (FR-007)

## Fase 6: História 5 — Custo em tempo (P3) (fatia D)

- [ ] T027 [P] [US5] Testes de limite: estouro de tempo ou memória devolve modelo parcial com `limiteAtingido` e o que ficou de fora, sem derrubar a auditoria em `.../modelo/LimiteTest.java`
- [ ] T028 [US5] Medir e registrar o tempo de construção por repositório; comparar com o orçamento do João quando existir (FR-009)

## Fase 7: História 3 — Regras que usam o modelo acertam mais (P1) (fatia E)

- [ ] T029 [US3] Escolher as 3 regras do piloto em `docs/evolucao/precisao.md` e nos gabaritos de **treino** e registrar a escolha e o motivo em `specs/005-modelo-estrutural-codigo/piloto.md` antes de escrever qualquer versão (princípio III)
- [ ] T030 [P] [US3] Para cada regra: caso positivo e negativo (para regra baseada em modelo: repositório de teste pequeno com o caso e o contra-caso, em `audit-api/src/test/resources/modelo/piloto/`, rodado por JUnit), entrada em `remediation/remediation.yml`; `semgrep --test` e `scripts/paridade-motores.py` valem para a regra de padrão equivalente, que continua existindo (princípio II, A3)
- [ ] T035 [US3] Desenhar e implementar a interface regra→modelo (D8 de `research.md`): as regras piloto são analisadores Java no estilo de `ArchitectureAnalyzer`, que consomem o modelo e leem seus parâmetros do perfil YAML; registrar a exceção ao princípio V em "Complexity Tracking" do plano (**decisão do João**, ver D8); teste unitário da interface antes (A1)
- [ ] T031 [US3] Escrever as versões baseadas no modelo (depende de T035), convivendo com as de padrão e atrás de chave desligada (FR-007)
- [ ] T032 [US3] Medir antes × depois no treino; sem a régua da spec 003, registrar a medida manual e **não** substituir nenhuma regra (FR-008, SC-004)

## Fase 8: Fechamento

- [ ] T033 Rodar o roteiro completo do `quickstart.md`; conferir jobs de regras, Semgrep e benchmark (SC-006) e que o modelo roda dentro da imagem Docker, com e sem `WITH_ESLINT`, no job de build da imagem (FR-012, A4)
- [ ] T034 Atualizar `docs/referencia/` com o comando `modelo` e registrar tudo no `docs/evolucao/LOG.md`

## Dependências

Fase 2 antes de tudo. B depende de A e do "sim" em T009. C depende de A (independente de B, mas T022 usa os endpoints de B). D depende de B e C. E depende de B (e de C para regras de frontend); dentro dela, T029 vem primeiro e T035 antes de T031.
