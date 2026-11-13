# Plano de implementação: Sentinel Premium 5 — Arquitetura verificável

**Ramo**: `spec/007-plano` | **Data**: 2026-11-13 | **Spec**: [spec.md](spec.md)

## Resumo

Um verificador novo recupera o mapa real da arquitetura (módulos, camadas inferidas por anotação, dependências com evidência) a partir do modelo da spec 005 e o compara com a arquitetura declarada em `architecture.yml`, estendido de forma compatível com dependência indireta, ciclos proibidos e exceções com validade. Os desvios saem com o caminho completo e nascem em **modo sombra** ao lado das verificações atuais, de modo que o peso de 20 % não muda; o verificador é medido com violações plantadas e só substitui o atual com o "fica" da régua da 003. Em PR, só o que o PR introduziu (spec 004). Ver [research.md](research.md).

## Contexto técnico

- **Linguagem/pilha**: Java no `audit-api` (Quarkus; compila no JDK 26 e roda no JRE 25), `audit-cli`.
- **Armazenamento**: nenhum novo; desvios são achados (identidade regra + par origem→destino, sem a linha); só o resumo de métricas vai com o resultado.
- **Testes**: JUnit com repositórios de teste em `audit-api/src/test/resources/arquitetura/`; corpus de violações plantadas em `audit-rules/calibracao/arquitetura/`.
- **Restrições**: nada executado nem compilado do auditado (VII); sem instalação no host (VIII); chaves por sistema, desligadas (V); API pública só com autorização (IX).
- **Dependências abertas**: (1) spec 005 (modelo) e 003 (régua); (2) spec 004 para o corte em PR; (3) **autorização do João** se o mapa ou as métricas exigirem campo novo na API.

## Verificação da constituição

| Princípio | Estado | Como |
|---|---|---|
| I | ok | verificador faz parte do motor; sem IA na auditoria |
| II | ok | testes antes; corpus de violações plantadas é o teste do verificador |
| III | ok | corpus em treino ou criado; reservado não é lido; `verificar-separacao.py` |
| IV | ok | acerto e alarme falso com intervalo; sem régua não afirma "fica"; LOG Verificado / NÃO verificado |
| V | ok | `architecture.yml` estendido sem quebrar; chaves por sistema em `.sentinel.yml` |
| VI | ok | Java, Spring, Quarkus e TypeScript no escopo; nada fora |
| VII | ok | só leitura; patches das violações plantadas aplicados em cópia temporária |
| VIII | ok | roda na JVM da imagem |
| IX | **atenção** | campo novo na API (se houver) e `ci.yml` (se um job novo for preciso) pedem o João |

Reavaliada após o desenho: sem violação; uma autorização condicional.

## Qual job do CI prova cada critério

| Critério | Prova |
|---|---|
| SC-001, SC-007 | JUnit do mapa sobre repositórios de teste (job de testes da API) |
| SC-002, SC-003 | `arquitetura medir` sobre o corpus plantado; régua da 003 quando existir; **bloqueado** sem ela |
| SC-004 | jobs de pacotes de regras e testes existentes + teste de compatibilidade do `architecture.yml` atual |
| SC-005 | JUnit do corte em PR (depende da 004) |
| SC-006 | JUnit da tendência com duas versões de teste |
| FR-009 | JUnit das exceções vigentes e vencidas |
| FR-010 | JUnit do mapa sem declaração |
| FR-011 | JUnit: nota de arquitetura idêntica com o verificador em sombra |
| FR-012 | teste de varredura: sem execução nem compilação |

## Estrutura

```text
specs/007-arquitetura-verificavel/   plan, research, data-model, quickstart, contracts/
audit-api/.../arquitetura/           Mapa, Camadas, Dependencias, Desvios, Excecoes, Metricas, VerificadorAnalyzer
audit-api/.../pack/ArchitecturePolicy  extensão compatível (indirect, cycles, exceptions, metrics)
audit-rules/calibracao/arquitetura/  corpus limpo + plantadas.json + patches
audit-cli/                           comando `arquitetura`
```

## Fatias (um PR cada, só depois de `/speckit-tasks`)

- **A**: formato estendido de `architecture.yml` e carregador, com teste de compatibilidade e de validação.
- **B**: mapa recuperado (módulos, camadas por anotação, dependências com evidência, não resolvidas). ⚠️ depende da 005.
- **C**: verificador: desvios diretos e indiretos, exceções, em modo sombra.
- **D**: corpus de violações plantadas e `arquitetura medir`.
- **E**: corte em PR (depende da 004) e ciclos entre módulos.
- **F**: métricas, tendência de erosão e sinais clássicos.
- **G**: fechamento.

Nada é implementado nesta etapa: é só plano.
