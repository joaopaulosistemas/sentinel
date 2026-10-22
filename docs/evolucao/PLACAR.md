# Placar do Sentinel rumo ao nível profissional

Escala por eixo: 0 ausente · 1 frágil · 2 funciona e é testado · 3 validado com evidência externa (repositórios reais, CI real, medição).
**Meta: média ≥ 2,5 com nenhum eixo abaixo de 2.** Hoje: **2,0** (todos os eixos em 2; nenhum em 3 ainda).

## Regras do jogo (valem para os dois agentes)
1. O placar só sobe com **evidência no repositório** (teste, saída de CI, relatório com números). Nunca por opinião.
2. Quem sobe a nota propõe a alteração desta tabela **no mesmo PR** que traz a evidência, citando o arquivo ou a execução.
3. Uma nota nunca sobe por causa de outra mudança de peso ou de critério. Se o critério mudar, registre no LOG e reavalie tudo.
4. O agente **não declara** "nível profissional". Quando a meta for atingida, ele relata as evidências e a decisão é do humano.
5. Cada PR deve dizer qual eixo ataca e qual item do backlog fecha.

## Tabela (atualizada por PR com evidência)
| # | Eixo | Nota | Para chegar a 3 (critério verificável) |
|---|---|---:|---|
| 1 | Cobertura de regras | 2 | recall medido no OWASP Benchmark (CI, `scripts/benchmark-owasp.py`): pontuação média (TPR − FPR) ≥ 0,45 e nenhuma categoria coberta abaixo de 0,10, nos conjuntos de treino **e** de validação com diferença ≤ 0,10. Hoje: 0,44 (treino 0,48 · validação 0,42) |
| 2 | Precisão | 2 | FPR ≤ 25% em cada categoria do OWASP Benchmark **e** taxa de verdadeiro positivo ≥ 80% nas regras de severidade média ou alta em amostra revisada, com concordância ≥ 80% entre o agente e a revisão humana (`revisao-humana.md`) |
| 3 | Padrões de saída | 2 | SARIF aceito pelo GitHub Code Scanning em execução real; SBOM no release |
| 4 | Quality gate e tendência | 2 | gate bloqueando um PR real por regressão de nota, com histórico entre execuções |
| 5 | Integração | 2 | fluxo de PR do modelo `github-actions.yml` rodando num repositório real, com comentário/anotação |
| 6 | Remediação | 2 | ≥ 90% das regras com remediação revisada contra a documentação oficial (OWASP/Spring/Angular) |
| 7 | Dashboard | 2 | teste ponta a ponta no navegador (Playwright) cobrindo auditoria, relatório e filtros |
| 8 | Operação | 2 | login OIDC testado com Keycloak em CI, métricas Prometheus, teste de carga com limites, imagem reprodutível |
| 9 | Qualidade interna | 2 | N+1 corrigidos, cobertura medida (JaCoCo) ≥ 70% nos pacotes de regra/pontuação, auditoria do próprio Sentinel sem achado alto |
| 10 | Credibilidade | 2 | ≥ 100 achados revisados em ≥ 10 repositórios públicos, taxa por regra publicada (`precisao.md`), **e** ≥ 60 deles conferidos por uma pessoa (`revisao-humana.md`) com concordância ≥ 80%; ≥ 1/3 dos repositórios em Java/Spring ou Angular |

## Medição independente (desde 2026-10-22)
- **OWASP Benchmark** (2.740 casos Java com gabarito oficial) roda no CI (`benchmark`) e publica TPR, FPR e pontuação por categoria. A linha de base fica em
  `audit-rules/calibracao/benchmark/baseline.json`; o CI falha se a média cair mais de 0,02. Quem melhorar uma categoria atualiza a linha de base **para cima** no mesmo PR.
- **Treino × validação**: veja `audit-rules/calibracao/conjuntos.json`. Casos 1–1370 do Benchmark serviram para escrever as regras; 1371–2740 só medem.
- **Revisão humana**: `docs/evolucao/revisao-humana.md`.
- **Linha de partida honesta**: antes das regras novas a média era 0,00 (equivalente a chute); depois, 0,44. Categorias mais fracas: `cmdi` 0,06, `xpathi` 0,17, `pathtraver` 0,13, `ldapi` 0,29, `trustbound` 0,26.

## Backlog (o Evolution pega **um** item de produto por execução, do eixo de menor nota; empate: o de menor esforço. Marcar `[x]` exige diff de código ou de teste)
Marque `[x]` no PR que fecha o item, com a evidência.
- [x] 10/2 Criar `docs/evolucao/precisao.md` e alimentá-lo a cada rodada da Descoberta (regra, revisados, VP, FP) — arquivo existe com 8 regras e 10 achados em 4 repositórios (rodada de 2026-10-21); nota do eixo 10 segue 2 (faltam ≥ 100 achados em ≥ 10 repositórios)
- [ ] 1/2 OWASP Benchmark: subir `cmdi` (hoje 0,06) tratando `exec` com argumentos de ambiente e `ProcessBuilder.command(...)`; medir antes e depois no CI
- [ ] 1/2 OWASP Benchmark: reduzir o FPR de `pathtraver` e `trustbound` (hoje 54–65%) com sanitizadores genéricos (`normalize`, `getFileName`, validação por lista) sem olhar o código do Benchmark
- [ ] 1/2 Estender o benchmark a outra linguagem com gabarito (NodeGoat ou OWASP Juice Shop com lista de desafios) para medir as regras de JS/TS
- [ ] 8 Métricas Prometheus da API (`/q/metrics`: auditorias, duração, achados) com teste
- [ ] 9 Corrigir N+1 em `RuleQualityService` e `AuditScheduler` com teste que conta consultas
- [ ] 9 Cobertura JaCoCo no Maven e meta de 70% nos pacotes de regra e pontuação
- [ ] 8 Teste de login OIDC contra Keycloak em CI (job novo no `e2e`, sem tocar `.github/` — pedir ao humano o ajuste do workflow)
- [ ] 3 SARIF de uma auditoria subido ao Code Scanning (depende do plano do GitHub)
- [ ] 8 Teste de carga (k6) com limites de latência documentados
- [ ] 7 Teste ponta a ponta do dashboard com Playwright
- [ ] 1 Conjunto de referência para recall (casos `deve_achar` ampliados com os falsos negativos da descoberta)
- [ ] 6 Revisão das remediações das regras de segurança contra a documentação oficial

Itens que exigem mexer em `.github/` ou em segredos não podem ser feitos pelo agente: ele abre o PR com tudo que está
fora desses caminhos e escreve, no corpo, o pedido exato para o humano.
