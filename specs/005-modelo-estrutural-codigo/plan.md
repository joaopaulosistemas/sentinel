# Plano de implementação: Sentinel Premium 3 — Modelo estrutural do código

**Ramo**: `spec/005-plano` | **Data**: 2026-11-13 | **Spec**: [spec.md](spec.md)

## Resumo

O Sentinel passa a construir, a cada auditoria, um modelo em memória do repositório (unidades, relações, pontos de entrada, acessos a dados) lido só como texto, e a oferecer consultas por um comando da CLI. Java é lido por um leitor sintático em Java puro; TypeScript, JavaScript, Angular, React e Vue pelo parser do `typescript-eslint` que já está na imagem. O modelo convive com as regras de padrão: nenhuma é trocada sem "fica". Ver [research.md](research.md).

## Contexto técnico

- **Linguagem/pilha**: Java 25 no `audit-api` (Quarkus), `audit-cli`, script Node 24 da imagem para o frontend. Os frameworks Spring e Quarkus acima são os dos repositórios **auditados**.
- **Armazenamento**: nenhum novo; o modelo é reconstruído a cada auditoria (esclarecimento 5). Só cobertura e tempo vão com o resultado.
- **Testes**: JUnit com repositórios de teste pequenos em `audit-api/src/test/resources/modelo/`; `semgrep --test` e `paridade-motores.py` para regras piloto.
- **Restrições**: sem executar nem compilar o auditado (VII); sem instalação extra no host (VIII); sem rota na API nem tela (esclarecimento 1).
- **Dependências abertas**: (1) **dependência Maven nova** para ler Java: decisão do João (D1); (2) régua da spec 003 foi revertida, então o veredito "fica" das regras piloto está bloqueado até ela existir.

## Verificação da constituição

| Princípio | Estado | Como |
|---|---|---|
| I (motor é a fonte) | ok | o modelo alimenta regras do motor; sem IA na auditoria (FR-011) |
| II (teste junto) | ok | tarefas com teste antes; regra piloto com caso positivo e negativo, `semgrep --test`, paridade |
| III (treino e validação) | ok | piloto escolhido só por treino e gabaritos; `verificar-separacao.py` |
| IV (honestidade) | ok | cobertura por tipo, sem média; LOG com Verificado / NÃO verificado; piloto sem régua não afirma ganho |
| V (plug-and-play) | ok | regras do piloto são YAML; sem recompilar para regra nova |
| VI (escopo) | ok | Java, Spring, Quarkus, Angular, React, Vue, TS, JS; nada de outra linguagem |
| VII (não executar) | ok | só leitura; parser sem carregar configuração do repositório; teste de varredura proíbe `exec` e compilação |
| VIII (roda onde roda) | ok | JRE e Node já na imagem; sem Node, frontend vira "não modelado" declarado |
| IX (pequeno, perguntar) | **atenção** | dependência nova: pedir ao João; sem mudar API pública; `ci.yml` só se um teste novo precisar |

Reavaliada após o desenho: sem violação; duas decisões do João pendentes.

## Qual job do CI prova cada critério

| Critério | Prova |
|---|---|
| SC-001 | JUnit sobre repositórios de teste Spring, Quarkus, Angular, React e Vue (job de testes da API) |
| SC-002 | JUnit que constrói duas vezes e compara os bytes |
| SC-003 | JUnit do relatório de cobertura; depois medição da 003 sobre o treino |
| SC-004 | comparação antes × depois; **bloqueado** sem a régua da 003 |
| SC-005 | tempo gravado por repositório; orçamento do João |
| SC-006 | jobs de regras, Semgrep e benchmark existentes |
| FR-007, FR-008 | teste de que a regra de padrão segue valendo em arquivo não modelado |
| FR-011 | teste de varredura: sem chamada de rede nem de IA no código do modelo |

## Estrutura

```text
specs/005-modelo-estrutural-codigo/   plan, research, data-model, quickstart, contracts/cli.md
audit-api/.../modelo/                 Modelo, Unidade, Relacao, JavaReader (novo), TsReader (novo), Cobertura
audit-api/src/main/resources/modelo/  leitor-ts.mjs (só parse)
audit-cli/                            comando `modelo`
audit-rules/packs/                    3 regras piloto (YAML), convivendo com as de padrão
```

## Fatias (um PR cada, só depois de `/speckit-tasks`)

- **A**: tipos do modelo, serialização determinística, cobertura e contrato da CLI (sem leitor).
- **B**: leitor Java (Spring e Quarkus) e consultas de backend. ⚠️ depende do "sim" do João para a dependência.
- **C**: leitor TypeScript/JavaScript e frontend (Angular, React, Vue).
- **D**: relatório de cobertura e tempo, limites de tempo e memória.
- **E**: três regras piloto convivendo com as de padrão; medição antes × depois.

Nada é implementado nesta etapa: é só plano.
