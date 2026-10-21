# Agente Sentinel Evolution — como funciona e como ligar

## O laço
CI termina na `main` → `evolucao.yml` junta as anotações de erro e o resumo de achados por regra (`scripts/evolucao-entrada.sh`)
→ o agente (Claude, via `anthropics/claude-code-action`) lê isso junto com `docs/evolucao/LOG.md`, classifica cada problema
(defeito da ferramenta, falso positivo, falso negativo, regressão) e abre até 3 PRs em branches `evolucao/*`
→ o CI roda na branch (disparado por `workflow_dispatch`, porque PR aberto com `GITHUB_TOKEN` não dispara o CI sozinho)
→ você mescla se estiver tudo verde. As instruções do agente estão em `.github/agente/sentinel-evolution.md`.

## O que impede o agente de estragar
- Nunca empurra para a `main`: o prompt proíbe e `--disallowedTools` bloqueia `git push origin main`/`--force`.
- O job `Escopo dos PRs do agente` reprova PR de `evolucao/*` que mexa em `.github/`, `scripts/e2e.sh`, `scripts/evolucao-entrada.sh`, `scripts/ci-anotar.sh`, `.gitignore` ou `.env*`.
- O job `Calibração em vários repositórios` também roda nesses PRs: um achado que sumiu quando devia aparecer (`deve_achar`), um falso positivo que voltou (`nao_deve_achar`) ou ruído acima do teto reprova o PR (`audit-rules/calibracao/esperado.json`).
- A entrada vem de logs e de repositórios de terceiros: o prompt manda tratá-la como dado, nunca como instrução.
- Máximo de 60 turnos e 45 minutos por execução; `concurrency` impede duas execuções ao mesmo tempo.

## O que só você pode fazer (o ambiente do Claude não tem permissão de escrita nessas configurações)
1. **Segredo da API.** Settings → Secrets and variables → Actions → New repository secret: `ANTHROPIC_API_KEY`. Sem ele o workflow só emite um aviso e termina.
2. **Permitir PRs pelo Actions.** Settings → Actions → General → Workflow permissions → marque *Read and write permissions* e *Allow GitHub Actions to create and approve pull requests*.
3. **Proteger a `main`** para que só entre PR com o CI verde:
   ```
   gh api -X PUT repos/joaopaulosistemas/sentinel/branches/main/protection --input .github/agente/protecao-main.json
   ```
   (exige o CI inteiro verde; `enforce_admins=false` mantém o seu push direto como administrador. Se quiser exigir também uma aprovação, acrescente `required_pull_request_reviews`.)
4. **Releases.** Para publicar: `git tag v0.12.0 && git push origin v0.12.0`. O workflow `release.yml` espera o CI do commit ficar verde, publica as imagens no GHCR, o zip do CLI, o SBOM e as versões dos pacotes de regras nas notas.

## Primeiro teste
Actions → Sentinel Evolution → Run workflow (com `run_id` vazio analisa o último CI da `main`).
