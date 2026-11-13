---

description: "Tarefas da spec 006: Sentinel Premium 4 — Rastreio de dado inseguro entre arquivos"
---

# Tarefas: Sentinel Premium 4 — Rastreio de dado inseguro entre arquivos

**Entrada**: `specs/006-rastreio-entre-arquivos/` (plan, spec, research, data-model, contracts/, quickstart)
**Testes**: **obrigatórios** (princípio II): cada fatia traz os testes **antes** do código.
**Organização**: por história; as fatias do plano (A a G) são os PRs. **Nada roda sem o João mandar implementar.**

## Formato: `[ID] [P?] [História] Descrição com caminho`

- **[P]**: paralelizável (arquivos diferentes, sem dependência pendente)
- **[USn]**: US1 SQL entre camadas, US2 caminho no achado, US3 mais preciso que o padrão, US4 segunda classe, US5 convivência
- ⚠️ **Autorização**: o João precisa liberar antes (API pública ou `.github/`)
- Toda fatia termina com entrada em `docs/evolucao/LOG.md` (Verificado / NÃO verificado) e, se mudar comportamento, versão nova (`audit-dashboard/package.json` + `package-lock.json` linhas 3 e 9 + `python3 -I scripts/sincronizar-versao.py`). Chaves nascem **desligadas**, por sistema, em `.sentinel.yml` (nada em `application.properties`).
- Caminhos de teste: `audit-api/src/test/java/com/sentinel/audit/rastreio/`; código: `audit-api/src/main/java/com/sentinel/audit/rastreio/`.

## Mapa de fatias

| Fatia | Fase | PR |
|---|---|---|
| A | 1 e 2 | Definições em dados, esquema, configuração por sistema |
| B | 3 (US1) | Fluxo de valores, motor e SQL em 3 camadas ⚠️ depende da 005 |
| C | 4 (US2) | Sanitizadores, propagadores, rastro perdido, limites, cobertura |
| D | 5 (US5) | Fusão de fontes e campo no achado ⚠️ |
| E | 6 (US3) | Medição |
| F | 7 (US4) | Segunda classe |
| G | 8 | Fechamento |

---

## Fase 1: Preparação (fatia A)

- [ ] T001 Criar repositórios de teste em `audit-api/src/test/resources/rastreio/`: `sql-3-camadas-spring/`, `sql-3-camadas-quarkus/` (cada um com variante vulnerável e variante parametrizada), `sql-sanitizado/` (`parseInt` e lista de permitidos), `sql-propagadores/` (`StringBuilder`, `String.format`, método de biblioteca fora da lista), `sql-rastro-perdido/` (reflexão, assíncrono) e `esperado.json` por caso
- [ ] T002 [P] Registrar as chaves **por sistema** `taint.enabled` (padrão `false`), `taint.max-depth`, `taint.max-paths`, `taint.timeout-seconds` em `.sentinel.yml` lido por `ProjectConfig`, com teste de padrão em `audit-api/src/test/java/com/sentinel/audit/service/ProjectConfigTest.java`

## Fase 2: Fundação (bloqueia as histórias; fatia A)

- [ ] T003 [P] Testes do esquema de definição (`contracts/definicao-classe.md`): classe única, argumento inteiro ≥ 0, método qualificado, listas não vazias, sem duplicata em `audit-api/src/test/java/com/sentinel/audit/rastreio/DefinicaoDeClasseTest.java`
- [ ] T004 [P] Teste de varredura do pacote `rastreio`: sem execução, compilação, rede nem IA (princípio VII, FR-012) em `audit-api/src/test/java/com/sentinel/audit/rastreio/SemExecucaoTest.java`
- [ ] T005 Criar `audit-rules/taint/sqli.yml` com origens, destinos (JDBC, `JdbcTemplate`, consulta nativa, JPQL/HQL por `EntityManager` e Panache), sanitizadores e propagadores iniciais, revistos só contra treino (princípio III, esclarecimentos 2 a 5)
- [ ] T006 Implementar `DefinicaoDeClasse` e o carregador em `audit-api/src/main/java/com/sentinel/audit/rastreio/DefinicaoDeClasse.java`, falhando com mensagem útil em definição inválida

**Checkpoint**: testes da fatia A verdes; nenhuma API alterada.

---

## Fase 3: História 1 — Injeção de SQL que atravessa camadas (P1) 🎯 MVP (fatia B)

**Pré-requisito**: spec 005, fatias A e B (leitor Java). **Teste independente**: roteiros 1 e 2.

- [ ] T007 [P] [US1] Testes de fluxo de valores: argumento→parâmetro, atribuição, retorno, campo e construtor, entre arquivos, em `audit-api/src/test/java/com/sentinel/audit/rastreio/FluxoDeValoresTest.java`
- [ ] T008 [P] [US1] Testes do motor: 3 camadas Spring e Quarkus vulneráveis geram 1 achado com os passos; variante parametrizada não gera (SC-001) em `audit-api/src/test/java/com/sentinel/audit/rastreio/MotorSqlTest.java`
- [ ] T009 [P] [US1] Teste de que ORM seguro, `setParameter` e consulta derivada por nome de método não geram alarme (FR-005, casos de borda) em `audit-api/src/test/java/com/sentinel/audit/rastreio/MotorSeguroTest.java`
- [ ] T010 [US1] Implementar a camada de fluxo de valores sobre o leitor Java da 005, sem mudar o contrato público do modelo (D2) em `audit-api/src/main/java/com/sentinel/audit/rastreio/FluxoDeValores.java`
- [ ] T011 [US1] Implementar resumos por método e a propagação interprocedural por fila de trabalho (D3) em `audit-api/src/main/java/com/sentinel/audit/rastreio/Resumos.java` e `Motor.java`
- [ ] T012 [US1] Gerar o achado `sentinel-taint-sqli` com origem, passos e destino, atrás de `taint.enabled`

## Fase 4: História 2 — Cada achado conta o caminho (P1) (fatia C)

- [ ] T013 [P] [US2] Testes do caminho: cada passo com arquivo e linha; caminho acima do limite vem com `truncado` e `passosOmitidos`, nunca encurtado em silêncio (FR-004) em `audit-api/src/test/java/com/sentinel/audit/rastreio/CaminhoTest.java`
- [ ] T014 [P] [US2] Testes de sanitizador declarado (encerra o rastro, registra qual) e de função "bonita" fora da lista (não sanitiza) em `audit-api/src/test/java/com/sentinel/audit/rastreio/SanitizadorTest.java`
- [ ] T015 [P] [US2] Testes de propagadores: `StringBuilder.append`, `String.format` seguem; método de biblioteca fora da lista vira `PROPAGADOR_DESCONHECIDO` (FR-013) em `audit-api/src/test/java/com/sentinel/audit/rastreio/PropagadorTest.java`
- [ ] T016 [P] [US2] Testes de rastro perdido (reflexão, assíncrono, limite de profundidade, de caminhos e de tempo): vai ao relatório de cobertura com motivo, 0 achados, 0 "seguro" (FR-006, SC-005) em `audit-api/src/test/java/com/sentinel/audit/rastreio/RastroPerdidoTest.java`
- [ ] T017 [US2] Implementar `Caminho` e o texto de correção ("o que faltou") em `audit-api/src/main/java/com/sentinel/audit/rastreio/Caminho.java`
- [ ] T018 [US2] Implementar sanitizadores, propagadores e limites configuráveis (FR-010) no motor
- [ ] T019 [US2] Implementar `CoberturaDoRastreio` e guardar só o resumo com o resultado da auditoria; comando `sentinel rastreio cobertura` (texto e `--json`, determinístico) em `audit-cli/`

## Fase 5: História 5 — Convivência com o que existe (P2) (fatia D)

- [ ] T020 [P] [US5] Testes da fusão: o mesmo destino achado por padrão, Joern e rastreio vira 1 achado com 3 fontes; destinos diferentes não se fundem; reformatação não quebra a fusão (FR-007) em `audit-api/src/test/java/com/sentinel/audit/rastreio/FusaoDeFontesTest.java`
- [ ] T021 [P] [US5] Teste de regressão: com `taint.enabled` desligado, relatório e resposta da API idênticos aos de hoje (byte a byte)
- [ ] T022 [US5] Implementar a fusão de fontes (classe + destino) como passo antes da nota, sem alterar `dedup.yml` (D5) em `audit-api/src/main/java/com/sentinel/audit/rastreio/FusaoDeFontes.java`
- [ ] T023 [US5] ⚠️ **Autorização**: acrescentar `trace`, `sources` e `fix` como campos opcionais do achado na API (`contracts/achado-rastreio.md`); sem o "sim", o caminho aparece só no relatório e no comentário

## Fase 6: História 3 — Mais preciso que o padrão atual (P1) (fatia E)

- [ ] T024 [P] [US3] Teste (arquivo novo `scripts/test_benchmark_owasp.py`): `sentinel-taint-sqli` mapeia para a categoria `sqli` em `MAPA_REGRAS` de `scripts/benchmark-owasp.py`, e as outras categorias seguem sob a trava existente (FR-009, SC-003)
- [ ] T033 [US3] Comando da CLI `sentinel rastreio executar <pasta> --json`: roda o motor numa pasta de fontes e devolve lista de achados (`ruleId`, `filePath`) no formato que `benchmark-owasp.py` já aceita; teste antes em `audit-cli/src/test/java/com/sentinel/audit/cli/RastreioExecutarTest.java`
- [ ] T034 [US3] Acrescentar `sentinel-taint-sqli` a `MAPA_REGRAS` em `scripts/benchmark-owasp.py`
- [ ] T035 [US3] ⚠️ **Autorização** (mexe em `.github/`): no job `benchmark` de `.github/workflows/ci.yml`, rodar `rastreio executar` sobre o Benchmark e juntar os achados aos do Opengrep antes de `benchmark-owasp.py`, e acrescentar `scripts/test_benchmark_owasp.py` à linha de testes dos scripts; sem o "sim", a medição é local e manual
- [ ] T025 [US3] Rodar o Benchmark (`sqli`: baseline 0,938 de acerto e 0,69 de alarme falso) e os gabaritos de treino com padrão, Joern e rastreio; registrar acerto, alarme falso e intervalos no LOG (FR-008)
- [ ] T026 [US3] Veredito: sem a régua da 003, registrar a medida manual e **não** colocar o rastreio no padrão; com a régua, só "fica" libera (SC-002)
- [ ] T027 [US3] Medir e registrar o tempo adicional por repositório; comparar com o orçamento do João quando existir (FR-011, SC-007)

## Fase 7: História 4 — Segunda classe de falha (P2) (fatia F)

- [ ] T028 [US4] Escolher a segunda classe entre as de menor pontuação na linha de base da 003 (hoje no Benchmark: `xpathi`, `pathtraver`, `ldapi`) e registrar a escolha e o motivo em `specs/006-rastreio-entre-arquivos/segunda-classe.md` antes de escrever a definição (FR-012)
- [ ] T029 [P] [US4] Teste de que a classe nova entra só por arquivo de definição, sem tocar o motor (SC-006) em `audit-api/src/test/java/com/sentinel/audit/rastreio/ClasseNovaTest.java`
- [ ] T030 [US4] Criar `audit-rules/taint/<classe>.yml` e medir como na T025

## Fase 8: Fechamento (fatia G)

- [ ] T031 Rodar o roteiro completo do `quickstart.md`; conferir jobs de regras, Semgrep, Benchmark e Joern (SC-001 a SC-007)
- [ ] T032 Atualizar `docs/integracao/joern.md` (convivência) e `docs/referencia/` com o rastreio; registrar tudo no `docs/evolucao/LOG.md`

## Dependências

005 (A e B) e 003 antes da medição. T033 e T034 antes de T035 e de T025. Fase 2 antes de tudo; B depende de A e da 005; C depende de B; D depende de C; E depende de D; F depende de E; G por último.
