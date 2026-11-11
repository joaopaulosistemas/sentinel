# Impacto do Sentinel

Este documento diz o que o Sentinel faz hoje, o que já foi **medido** e o que ainda **não** foi. Não traz estimativas de ganho em porcentagem ou em horas: nenhuma foi medida com esta ferramenta numa organização real. Onde um número aparece, a fonte está ao lado.

Data da revisão: 2026-11-05 (versão 12.39.0 do painel).

---

## 1. O problema que o Sentinel ataca

Uma organização com muitos sistemas e vários times costuma ter a qualidade técnica vista de forma fragmentada: cada time olha o seu projeto, quando olha. Faltam três coisas:

- uma **visão única** de qual sistema está pior e por quê;
- um **critério igual** para todos (as mesmas regras, a mesma nota);
- um **registro** do que foi achado, do que foi aceito como risco e do que foi corrigido.

O Sentinel entrega isso: clona o repositório (ou lê uma pasta local), roda os analisadores, calcula uma nota de risco por sistema e mostra tudo no painel, no relatório (HTML, PDF, CSV), no SARIF e no CLI.

---

## 2. O que o Sentinel faz hoje

| Capacidade | Como é hoje |
|---|---|
| **Análise** | Segurança (SAST com Opengrep, segredos, dependências com Trivy/OSV, IaC), qualidade (clean code, PMD, ESLint, Stylelint), arquitetura (camadas, ciclos, God Object), SQL (SQLFluff, Squawk), Docker e Kubernetes (Hadolint, Checkov, kube-linter), duplicação (jscpd) e higiene do projeto (README, testes, CI, lockfile) |
| **Regras** | 16 pacotes declarativos em `audit-rules/packs` (299 regras próprias), 12 ferramentas de mercado via SARIF (mais o Joern, opcional) e 9 perfis (padrão, Quarkus + React, padrão Google, padrão TypeScript, legado etc.) |
| **Fluxo de dados** | Taint no mesmo arquivo pelo Opengrep; entre arquivos (Java) pelo Joern, opcional, sem compilar o projeto |
| **Nota** | Seis dimensões (segurança, qualidade, arquitetura, dependências, observabilidade, performance), com ajuste por tamanho do código e limite de penalidade por regra |
| **Acompanhamento** | Achados com identidade estável entre auditorias: novos, resolvidos e persistentes; tendência da nota; supressão com motivo, justificativa e validade |
| **Auditoria completa** | Parecer, áreas, dívida técnica P0–P3 com esforço e risco de regressão, quick wins e plano de ação em 8 fases, com a lista do que a análise estática não alcança |
| **Orientação** | Cada regra traz o que fazer, exemplo antes/depois, esforço e risco de regressão |
| **Integração** | CLI com gate de nota e de código novo, SARIF e JUnit, auditoria só do que mudou no PR, webhooks GitHub/GitLab, avisos em Teams/Slack, SBOM CycloneDX |
| **IA (opcional)** | Servidor MCP: o Claude consulta o Sentinel (só leitura, fora da nota); propostas de regra e pedidos de ação só valem com aprovação de uma pessoa |
| **Operação** | Docker Compose, chart Helm, login OIDC opcional, tela de Configurações |

Detalhes e limites de cada item: [README](../README.md) e [arquitetura](arquitetura/ARQUITETURA.md).

---

## 3. O que já foi medido

| Medição | Resultado | Fonte | O que ela **não** prova |
|---|---|---|---|
| **OWASP Benchmark v1.2** (2.740 casos Java com gabarito oficial; média de acerto − falso alarme) | Opengrep **0,513**; Semgrep 0,461. Antes das regras novas a média era 0,00 | [LOG 12.35.0](evolucao/LOG.md), [PLACAR](evolucao/PLACAR.md) | A metade usada para escrever as regras subiu (0,507 → 0,615); a metade só de validação ficou em **0,42** nos dois motores. Mede só Java e só vulnerabilidades do Benchmark |
| **Categorias mais fracas** | Falso alarme de 46% a 73% em caminho de arquivo, comando, SQL e limite de confiança | `audit-rules/calibracao/benchmark/baseline.json` | Os números são do Benchmark, que mistura casos propositalmente difíceis |
| **Revisão por amostragem** | 450 achados revisados em 22 repositórios públicos, com taxa de acerto por regra | [precisao.md](evolucao/precisao.md) | A revisão foi do antigo agente Sentinel Descoberta (removido); a conferência humana ainda é parcial ([revisao-humana.md](evolucao/revisao-humana.md)). As amostras por regra são pequenas |
| **Ruído em projeto limpo** | Auditoria completa do `spring-petclinic`: 1.498 → 290 achados após corrigir as fontes de ruído; a nota ficou em 42,6 | [calibracao.md](evolucao/calibracao.md) | É um projeto de exemplo; mostra que o ruído de estilo era grande, não que 42,6 seja a nota certa |
| **Falsos positivos corrigidos** | Corrigidos em regras reais após calibração em petclinic, WebGoat, Frequency e o próprio Sentinel | [calibracao.md](evolucao/calibracao.md) | Falsos negativos só foram contados nos casos conhecidos |
| **Paridade entre motores** | Opengrep e Semgrep dão os mesmos achados sobre o corpus de testes de todos os pacotes (429 achados) | `scripts/paridade-motores.py`, CI | Vale só para os casos de teste das regras |
| **Taint entre arquivos (Joern)** | 6 casos de teste (3 com falha, 3 seguros) e o PetClinic, sem falso alarme nos seguros | `audit-api/joern/` | O ganho sobre o Opengrep **não** foi medido num conjunto de fluxos entre arquivos; falsos negativos não foram contados |
| **Placar** | Média 2,0 em 3; **nenhum eixo em 3** | [PLACAR.md](evolucao/PLACAR.md) | O Sentinel não declara a si mesmo nível profissional |

---

## 4. O que muda, por quem usa

Aqui estão os efeitos que decorrem diretamente do que o código faz. Nenhum tem número de ganho: isso depende de cada organização (seção 6).

**Quem desenvolve**
- Vê o problema com a linha, a explicação e o antes/depois, no painel ou no comentário do PR.
- A auditoria de PR mostra só o que mudou, então a conversa fica em cima do código novo.
- O CLI falha o pipeline pelo critério que o time escolheu (nota mínima, severidade, achado novo, queda de nota).

**Quem lidera o time**
- Tem a nota, a tendência e a dívida técnica do sistema em ordem de prioridade (P0 a P3), com esforço e risco de regressão.
- Pode marcar risco aceito com justificativa e validade; o que foi suprimido continua registrado e aparece no relatório.

**Segurança e arquitetura**
- Uma régua só para todos os sistemas, definida em pacotes de regras versionados e testados, que o próprio time pode estender.
- O que a ferramenta **não** consegue verificar (cobertura real, produção, contraste) aparece como "não verificado" em vez de virar "ok".

**Gestão**
- Relatório executivo de uma página, comparativo entre auditorias e relatório de portfólio (todos os sistemas).

---

## 5. O que ainda não está provado ou não existe

- **Taxa de falso positivo em repositório real da organização:** nunca foi medida. As calibrações usaram repositórios públicos.
- **Ganho em produtividade, prazo de correção, incidentes evitados:** não medidos. Qualquer número desse tipo seria chute.
- **Falsos negativos:** só se conhece o que o Benchmark e os casos de calibração mostram. A validação do Benchmark está em 0,42.
- **Linguagens além de Java no Benchmark:** o recall de JavaScript e TypeScript não tem gabarito externo (backlog do PLACAR).
- **Restrição por squad:** o login OIDC autentica, mas quem entra vê todos os sistemas.
- **Operação:** sem teste de carga, sem métricas Prometheus, sem teste ponta a ponta do painel no navegador.
- **Narrativa por IA** dentro da nota: não existe de propósito. O Claude via MCP é consulta e proposta, nunca muda a nota.
- **Maven, imagem Docker e CI** rodam só no GitHub; o que depende deles está marcado como não verificado no [LOG](evolucao/LOG.md) até o CI confirmar.

---

## 6. Como medir o impacto na sua organização

Para trocar estimativa por número, meça antes e depois em 2 ou 3 sistemas piloto. Sugestão:

| Indicador | Como coletar | Quando |
|---|---|---|
| Nota e achados críticos/altos por sistema | Painel, `GET /api/v1/systems/{id}/trend` | A cada auditoria |
| Tempo até corrigir um achado crítico ou alto | Data do primeiro aparecimento (novo) até "resolvido", no histórico de auditorias | Mensal |
| Taxa de falso positivo por regra | Tela de qualidade das regras (`GET /api/v1/rules/quality`) e revisão por amostragem de 20 achados por rodada | Mensal |
| PRs barrados pelo gate e achados novos por PR | CLI com `--fail-on-new` e resumo do PR (`--pr-comment`) | Por PR |
| Riscos aceitos vencidos | Supressões com validade (`GET /api/v1/systems/{id}/suppressions`) | Mensal |
| Achados que o Sentinel não achou e o pentest/incidente achou | Registro manual | A cada pentest ou incidente |

Regra de bolso: só publique um ganho depois de dois ciclos de medição e com a taxa de falso positivo ao lado.

---

## 7. O que falta para ir a produção com confiança

Os critérios de cada eixo estão no [PLACAR](evolucao/PLACAR.md). Os itens mais relevantes:

1. **Calibração em repositórios da própria organização**, com revisão humana de uma amostra (hoje só em repositórios públicos).
2. **Restringir por squad** a visão dos sistemas (claim do token OIDC).
3. **Subir a validação do Benchmark** (0,42) e reduzir o falso alarme das categorias mais fracas.
4. **Teste de carga e métricas** da própria API (Prometheus), e login OIDC testado com Keycloak em CI.
5. **Teste ponta a ponta do painel** no navegador.
6. **Corrigir os N+1** conhecidos em `RuleQualityService` e `AuditScheduler`.

---

## 8. Conclusão

O Sentinel já funciona de ponta a ponta e tem medições honestas, mas pequenas: o ponto forte hoje é a **régua única, versionada e testada**, com histórico e orientação de correção. O ponto fraco é a **precisão em código real da organização**, que ainda não foi medida. O primeiro passo útil é um piloto com 2 ou 3 sistemas, com os indicadores da seção 6.
