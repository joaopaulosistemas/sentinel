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
| 1 | Cobertura de regras | 2 | recall medido: ≥ 85% dos casos conhecidos de um conjunto de referência (OWASP Benchmark / WebGoat / casos reais da descoberta) |
| 2 | Precisão | 2 | taxa de verdadeiro positivo ≥ 80% nas regras de severidade média ou alta, medida em amostra revisada (`precisao.md`) |
| 3 | Padrões de saída | 2 | SARIF aceito pelo GitHub Code Scanning em execução real; SBOM no release |
| 4 | Quality gate e tendência | 2 | gate bloqueando um PR real por regressão de nota, com histórico entre execuções |
| 5 | Integração | 2 | fluxo de PR do modelo `github-actions.yml` rodando num repositório real, com comentário/anotação |
| 6 | Remediação | 2 | ≥ 90% das regras com remediação revisada contra a documentação oficial (OWASP/Spring/Angular) |
| 7 | Dashboard | 2 | teste ponta a ponta no navegador (Playwright) cobrindo auditoria, relatório e filtros |
| 8 | Operação | 2 | login OIDC testado com Keycloak em CI, métricas Prometheus, teste de carga com limites, imagem reprodutível |
| 9 | Qualidade interna | 2 | N+1 corrigidos, cobertura medida (JaCoCo) ≥ 70% nos pacotes de regra/pontuação, auditoria do próprio Sentinel sem achado alto |
| 10 | Credibilidade | 2 | ≥ 100 achados revisados à mão em ≥ 10 repositórios públicos, taxa por regra publicada (`precisao.md`) |

## Backlog (o agente pega o primeiro item aberto do eixo de menor nota; empate: o de menor esforço)
Marque `[x]` no PR que fecha o item, com a evidência.
- [ ] 10/2 Criar `docs/evolucao/precisao.md` e alimentá-lo a cada rodada da Descoberta (regra, revisados, VP, FP)
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
