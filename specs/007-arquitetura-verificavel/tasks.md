---

description: "Tarefas da spec 007: Sentinel Premium 5 — Arquitetura verificável"
---

# Tarefas: Sentinel Premium 5 — Arquitetura verificável

**Entrada**: `specs/007-arquitetura-verificavel/` (plan, spec, research, data-model, contracts/, quickstart)
**Testes**: **obrigatórios** (princípio II): cada fatia traz os testes **antes** do código.
**Organização**: por história; as fatias do plano (A a G) são os PRs. **Nada roda sem o João mandar implementar.**

## Formato: `[ID] [P?] [História] Descrição com caminho`

- **[P]**: paralelizável (arquivos diferentes, sem dependência pendente)
- **[USn]**: US1 mapa recuperado, US2 desvios contra a meta, US3 medida com violações plantadas, US4 erosão, US5 sinais clássicos
- ⚠️ **Autorização**: o João precisa liberar antes (API pública ou `.github/`)
- Toda fatia termina com entrada em `docs/evolucao/LOG.md` (Verificado / NÃO verificado) e, se mudar comportamento, versão nova (`audit-dashboard/package.json` + `package-lock.json` linhas 3 e 9 + `python3 -I scripts/sincronizar-versao.py`). Chaves nascem **desligadas**, por sistema, em `.sentinel.yml`.
- Caminhos: código em `audit-api/src/main/java/com/sentinel/audit/arquitetura/`, testes em `audit-api/src/test/java/com/sentinel/audit/arquitetura/`.

## Mapa de fatias

| Fatia | Fase | PR |
|---|---|---|
| A | 1 e 2 | Formato estendido de `architecture.yml` e carregador |
| B | 3 (US1) | Mapa recuperado ⚠️ depende da 005 |
| C | 4 (US2) | Verificador em modo sombra |
| D | 5 (US3) | Violações plantadas e `arquitetura medir` |
| E | 6 | Corte em PR e ciclos entre módulos (depende da 004) |
| F | 7 (US4) e 8 (US5) | Métricas, tendência e sinais clássicos |
| G | 9 | Fechamento |

---

## Fase 1: Preparação (fatia A)

- [ ] T001 Criar repositórios de teste em `audit-api/src/test/resources/arquitetura/`: `multimodulo/` (3 módulos, camadas por anotação), `direto-e-indireto/`, `com-excecao/`, `sem-declaracao/`, `nao-resolvido/` (injeção por nome), `monorepo/` (2 sistemas), cada um com `esperado.json`
- [ ] T002 [P] Registrar as chaves **por sistema** `architecture.verify.enabled` (padrão `false`) e `architecture.max-path-length` em `.sentinel.yml` lido por `ProjectConfig`, com teste de padrão em `audit-api/src/test/java/com/sentinel/audit/service/ProjectConfigTest.java`

## Fase 2: Fundação (bloqueia as histórias; fatia A)

- [ ] T003 [P] Testes de compatibilidade: o `architecture.yml` de `core-architecture` sem alteração carrega e dá os mesmos resultados de hoje (História 2 cenário 3, SC-004) em `audit-api/src/test/java/com/sentinel/audit/arquitetura/PoliticaCompativelTest.java`
- [ ] T004 [P] Testes de validação do formato estendido (`contracts/architecture-yml-estendido.md`): exceção sem `expires` ou `reason` é erro; `registered` > `expires` é erro; camada inexistente é erro; chave desconhecida é erro com mensagem útil em `audit-api/src/test/java/com/sentinel/audit/arquitetura/PoliticaEstendidaTest.java`
- [ ] T005 [P] Teste de varredura do pacote `arquitetura`: sem execução, compilação nem rede (FR-012, princípio VII) em `audit-api/src/test/java/com/sentinel/audit/arquitetura/SemExecucaoTest.java`
- [ ] T006 Estender `ArchitecturePolicy` com `indirect`, `cycles`, `exceptions` e `metrics`, sem mudar o que existe, em `audit-api/src/main/java/com/sentinel/audit/pack/ArchitecturePolicy.java` e no leitor correspondente (FR-002)
- [ ] T007 Implementar `Excecao` com estado VIGENTE ou VENCIDA calculado pela **data da auditoria** (D6) em `audit-api/src/main/java/com/sentinel/audit/arquitetura/Excecao.java`

**Checkpoint**: testes da fatia A verdes; `ArchitecturePolicyAnalyzer` intacto.

---

## Fase 3: História 1 — Ver a arquitetura que o código tem (P1) 🎯 MVP (fatia B)

**Pré-requisito**: spec 005 (modelo e leitor Java). **Teste independente**: roteiros 1, 2 e 9.

- [ ] T008 [P] [US1] Testes do mapa: módulos de build (`pom.xml`, Gradle, workspaces) e, sem eles, pacotes de primeiro nível; dependências com ao menos uma evidência (arquivo e linha); 100 % do gabarito (SC-001) em `audit-api/src/test/java/com/sentinel/audit/arquitetura/MapaTest.java`
- [ ] T009 [P] [US1] Testes de camada inferida: por anotação e estereótipo; classe sem sinal em "sem camada"; nunca por nome de pacote (esclarecimento 1) em `audit-api/src/test/java/com/sentinel/audit/arquitetura/CamadaInferidaTest.java`
- [ ] T010 [P] [US1] Testes de dependência "não resolvida" com motivo, nunca ausente (FR-004, SC-007); teste e código gerado fora do mapa, contados; monorepo separado por sistema em `audit-api/src/test/java/com/sentinel/audit/arquitetura/MapaBordaTest.java`
- [ ] T011 [P] [US1] Teste do mapa sem `architecture.yml`: entrega o mapa sem desvios e o relatório sugere por onde começar (FR-010) em `audit-api/src/test/java/com/sentinel/audit/arquitetura/MapaSemDeclaracaoTest.java`
- [ ] T012 [US1] Implementar `Mapa`, `Modulo`, `CamadaInferida` e `DependenciaDoMapa` sobre o modelo da 005 em `audit-api/src/main/java/com/sentinel/audit/arquitetura/` (FR-001)
- [ ] T013 [US1] Comando `sentinel arquitetura mapa` (texto e `--json` determinístico) no ponto de entrada local (`audit-api/.../local/LocalMain.java`)

## Fase 4: História 2 — Declarar a meta e ver os desvios (P1) (fatia C)

- [ ] T014 [P] [US2] Testes de desvio direto e indireto: caminho completo com arquivo e linha por aresta (FR-003) em `audit-api/src/test/java/com/sentinel/audit/arquitetura/DesvioTest.java`
- [ ] T015 [P] [US2] Testes de exceção: vigente não conta e aparece com motivo e datas; vencida volta como desvio e sai como vencida (FR-009) em `audit-api/src/test/java/com/sentinel/audit/arquitetura/ExcecaoRelatorioTest.java`
- [ ] T016 [P] [US2] Teste de "declarada e sem evidência" (camada declarada que o código nunca usou) em `audit-api/src/test/java/com/sentinel/audit/arquitetura/DeclaradaSemEvidenciaTest.java`
- [ ] T017 [P] [US2] Teste: com o verificador em sombra, a nota de arquitetura é idêntica à de hoje (FR-011) e as verificações atuais seguem iguais (SC-004) em `audit-api/src/test/java/com/sentinel/audit/arquitetura/PesoNaoMudaTest.java`
- [ ] T018 [US2] Implementar a busca de caminho mais curto com limite `architecture.max-path-length` (D4) em `audit-api/src/main/java/com/sentinel/audit/arquitetura/Desvios.java`
- [ ] T019 [US2] Implementar `ArquiteturaVerificavelAnalyzer`: achados na categoria ARCHITECTURE, em modo sombra, identidade regra + par origem→destino sem a linha, atrás de `architecture.verify.enabled`
- [ ] T020 [US2] Comando `sentinel arquitetura desvios` (texto e `--json`)

## Fase 5: História 3 — Medir o verificador (P1) (fatia D)

- [ ] T021 [P] [US3] Corpus em `audit-rules/calibracao/arquitetura/`: 3 repositórios limpos (treino ou criados; nunca do reservado) e `plantadas.json` com patches (diretas, indiretas, ciclo) e gabarito; teste de que repositório limpo acusa 0 fora do esperado
- [ ] T022 [P] [US3] Testes de `arquitetura medir`: aplica o patch em **cópia temporária** (o original fica intacto), calcula acerto e alarme falso com Wilson 95 %, mesma estatística da 003 (FR-005, SC-002, SC-003) em `audit-api/src/test/java/com/sentinel/audit/arquitetura/MedirTest.java`
- [ ] T023 [US3] Implementar `arquitetura medir` no ponto de entrada local, com saída determinística
- [ ] T024 [US3] Rodar a medição, registrar acerto e alarme falso com intervalo no LOG; sem a régua da 003 **não** declarar "fica" nem tirar o verificador do sombra (FR-006)

## Fase 6: Corte em PR e ciclos (fatia E)

- [ ] T025 [P] Testes do corte em PR: só violações e ciclos introduzidos ou piorados aparecem; 0 das antigas (FR-008, SC-005); sem a spec 004 implementada, mantém o comportamento por arquivo em `audit-api/src/test/java/com/sentinel/audit/arquitetura/PrTest.java`
- [ ] T026 [P] Testes do ciclo entre módulos: reporta os dois lados (História 5) em `audit-api/src/test/java/com/sentinel/audit/arquitetura/CicloModulosTest.java`
- [ ] T027 Ligar os achados ao classificador de PR da 004 e implementar o ciclo entre módulos reaproveitando `DependencyCycles`

## Fase 7: História 4 — Erosão ao longo do tempo (P2) (fatia F)

- [ ] T028 [P] [US4] Testes de métricas: instabilidade de Martin (saem ÷ saem + entram; indefinida e marcada quando ambos são 0), contagem bruta, fan-out e ciclos por módulo (esclarecimento 3) em `audit-api/src/test/java/com/sentinel/audit/arquitetura/MetricasTest.java`
- [ ] T029 [P] [US4] Testes da tendência com duas versões, uma mais acoplada: aponta a piora em 100 % dos pares (SC-006); mudança de linha não vira "resolvida" e "nova" em `audit-api/src/test/java/com/sentinel/audit/arquitetura/TendenciaTest.java`
- [ ] T030 [US4] Implementar `MetricasDoModulo` e guardar só o resumo com o resultado da auditoria
- [ ] T031 [US4] Implementar a tendência com a rota de diferença existente (`FindingLifecycleService`) e `sentinel arquitetura tendencia` (FR-007)
- [ ] T032 [US4] ⚠️ **Autorização**: só se o resumo de métricas precisar de campo novo na resposta da API pública; sem o "sim", ele aparece no relatório e na CLI ✅ **Autorizado pelo João em 2026-11-13** (decisão recomendada; vale quando a implementação for liberada).

## Fase 8: História 5 — Sinais clássicos (P3) (fatia F)

- [ ] T033 [P] [US5] Testes: classe com fan-out acima de `metrics.max-fan-out` e módulo com instabilidade acima de `metrics.max-instability` são reportados com limites declaráveis em `audit-api/src/test/java/com/sentinel/audit/arquitetura/SinaisTest.java`
- [ ] T034 [US5] Implementar os sinais como achados em sombra, com limites do `architecture.yml`

## Fase 9: Fechamento (fatia G)

- [ ] T035 Rodar o roteiro completo do `quickstart.md`; conferir jobs de pacotes de regras e de testes (SC-004)
- [ ] T036 Atualizar `docs/referencia/pacotes-de-regras.md` e `docs/arquitetura/ARQUITETURA.md` com o formato estendido e o comando `arquitetura`; registrar tudo no `docs/evolucao/LOG.md`

## Dependências

005 (modelo) e 003 (régua) antes da medição; 004 antes de T025 e T027. Fase 2 antes de tudo; B depende de A e da 005; C depende de B; D depende de C; E depende de C e da 004; F depende de C; G por último.
