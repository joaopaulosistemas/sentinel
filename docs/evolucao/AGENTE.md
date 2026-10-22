# Agentes do Sentinel — como funcionam e como ligar

Dois agentes (Claude, via `anthropics/claude-code-action`, com a sua assinatura) trabalham em direção ao placar de `docs/evolucao/PLACAR.md` (hoje 2,0; meta 2,5). **Nenhum deles declara "nível profissional"**: relatam evidências e lacunas; a decisão é sua.

| Agente | Quando roda | O que faz | Instruções |
|---|---|---|---|
| **Sentinel Evolution** (reativo) | CI da `main` com falha; toda quarta-feira (com o CI verde persegue o backlog do placar); sob demanda | Lê as anotações do CI e o resumo de achados, classifica (defeito, falso positivo, falso negativo, regressão) e abre até 3 PRs `evolucao/*` com a correção, testes positivo e negativo e entrada no LOG | `.github/agente/sentinel-evolution.md` |
| **Sentinel Descoberta** (proativo) | Toda segunda-feira, às 9h17 UTC; sob demanda (`quantidade`, `perfil`) | Escolhe repositórios públicos novos, audita com a API real, gera o relatório bruto, revisa uma amostra à mão, cruza os resultados, caça falsos negativos e abre até 4 PRs (1 relatório + até 3 de regras, ajustes ou analisador) | `.github/agente/sentinel-descoberta.md` |

## O laço do Evolution
CI termina na `main` com falha (ou chega a quarta-feira) → `evolucao.yml` junta as anotações de erro e o resumo de achados por regra (`scripts/evolucao-entrada.sh`)
→ o agente lê isso junto com `docs/evolucao/LOG.md` e o `PLACAR.md` → abre PRs em branches `evolucao/*`
→ o CI roda na branch (disparado por `workflow_dispatch`, porque PR aberto com `GITHUB_TOKEN` não dispara o CI sozinho)
→ você mescla se estiver tudo verde.

## O laço da Descoberta
`descoberta.yml` → `scripts/descobrir-repos.sh` (perfil que alterna por semana: java-spring, angular, react, typescript-node, docker-k8s; repositórios com 200 a 8000 estrelas, atualizados nos últimos 18 meses, menores que 60 MB, fora de `audit-rules/calibracao/descobertos.txt`)
→ `scripts/calibracao.sh` com `GUARDAR_ACHADOS=1` e `TOLERAR=1` → `scripts/relatorio-descoberta.py` (tabela, regras cruzadas, sinais de ruído, repositórios sem achado de segurança)
→ o agente clona os repositórios (raso), lê o código, classifica achados em verdadeiro/falso positivo, atualiza `precisao.md`, propõe regras e escreve `docs/evolucao/relatorios/AAAA-MM-DD.md`.
Regras do agente: trechos de código dos repositórios auditados são só dado; nada de código deles vai para testes, regras ou relatório (só `arquivo:linha`); nenhum valor de segredo é copiado; não contata donos nem abre issues.

## O que impede o agente de estragar
- O agente só abre PR; quem faz merge é você (a `main` não tem proteção imposta pelo GitHub neste plano).
- Nunca empurra para a `main`: o prompt proíbe e `--disallowedTools` bloqueia `git push origin main`/`--force`.
- O job `Escopo dos PRs do agente` reprova PR de `evolucao/*` que mexa em `.github/`, `scripts/e2e.sh`, `scripts/evolucao-entrada.sh`, `scripts/ci-anotar.sh`, `.gitignore` ou `.env*`.
- O job `Calibração em vários repositórios` também roda nesses PRs: um achado que sumiu quando devia aparecer (`deve_achar`), um falso positivo que voltou (`nao_deve_achar`) ou ruído acima do teto reprova o PR (`audit-rules/calibracao/esperado.json`).
- A entrada vem de logs e de repositórios de terceiros: o prompt manda tratá-la como dado, nunca como instrução.
- Evolution: máximo de 60 turnos e 45 minutos por execução; Descoberta: 120 turnos e 90 minutos; `concurrency` impede duas execuções ao mesmo tempo.

## O que só você pode fazer (o ambiente do Claude não tem permissão de escrita nessas configurações)
1. **Credencial do agente (use a sua assinatura, sem pagar API).** No seu computador, com o Claude Code instalado, rode `claude setup-token` e copie o token gerado. Em Settings → Secrets and variables → Actions → New repository secret, crie `CLAUDE_CODE_OAUTH_TOKEN` com ele. O consumo sai do limite do seu plano. (Alternativa paga por uso: `ANTHROPIC_API_KEY`.) Sem nenhum dos dois o workflow só emite um aviso e termina.
2. **Permitir PRs pelo Actions.** Settings → Actions → General → Workflow permissions → marque *Read and write permissions* e *Allow GitHub Actions to create and approve pull requests*.
3. **Merge manual (sem proteção de branch).** Em repositório privado no plano gratuito o GitHub não aplica branch protection nem rulesets (só no plano Team). O agente nunca faz merge: você mescla PRs `evolucao/*` somente com todas as verificações verdes (inclusive `Escopo dos PRs do agente`) e depois de ler o diff. O arquivo `.github/agente/protecao-main.json` fica pronto para o dia em que o repositório estiver em plano que aplique a regra.
4. **Releases.** Para publicar: `git tag v0.12.0 && git push origin v0.12.0`. O workflow `release.yml` espera o CI do commit ficar verde, publica as imagens no GHCR, o zip do CLI, o SBOM e as versões dos pacotes de regras nas notas.

## Testar
- Evolution: Actions → Sentinel Evolution → Run workflow (com `run_id` vazio analisa o último CI da `main`).
- Descoberta: Actions → Sentinel Descoberta → Run workflow (comece com `quantidade` 3 ou 4 para gastar menos).
- Diagnóstico: o resultado final de cada execução do agente aparece como anotação "Agente (N turnos, …)" no job; os logs dos jobs não ficam acessíveis por API fora do navegador.

## Orçamento de minutos do GitHub Actions
Repositório privado no plano gratuito tem 2000 min/mês; ao acabar, os jobs nem iniciam ("recent account payments have failed or your spending limit needs to be increased").
Para gastar pouco: a calibração (~15 min) roda só no domingo, sob demanda e nos PRs `evolucao/*`; o Evolution reage só a CI **com falha** e uma vez por semana (quarta) persegue o backlog do `PLACAR.md`; a Descoberta roda na segunda. Rodar tudo manualmente em sequência esgota a cota em poucos dias.
