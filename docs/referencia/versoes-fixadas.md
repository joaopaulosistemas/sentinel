# Versões fixadas e decisões em aberto

As ferramentas externas rodam em versões **fixadas** (com SHA-256 quando são binários) em `audit-api/Dockerfile` e `audit-rules/lint/eslint/package.json`. Fixar dá resultado repetível e impede troca silenciosa de binário; o custo é que **alguém precisa atualizar**.

## Política de atualização

- **Quando revisar:** a cada trimestre, e na hora em que uma ferramenta sair de suporte ou publicar correção de segurança.
- **Como atualizar:** subir a versão e o SHA-256 no `Dockerfile` (ou no `package.json` e `package-lock.json`), rodar a calibração e o OWASP Benchmark, e registrar no `docs/evolucao/LOG.md` o que mudou nos achados.
- **Quem decide:** quem abre o PR de atualização; mudança de licença ou de mantenedor vira decisão em aberto abaixo, com dono.

## Estado em 2026-11-10 (depois da atualização)

| Ferramenta | Versão fixada | Situação | Ação |
|---|---|---|---|
| ESLint | 10.14.0 | Linha atual (a 9.x saiu de suporte em 2026-08-06). `eslint-plugin-react` e `eslint-plugin-jsx-a11y` não declaram suporte ao 10 e rodam com `fixupPluginRules` (`@eslint/compat`) | Acompanhar versões novas desses dois plugins e tirar o adaptador quando declararem suporte |
| Gitleaks | 8.30.1 | Última 8.x (2026-03-21); o autor lançou o Betterleaks como sucessor e a manutenção do Gitleaks tende a só correções | Segue como padrão; só correções de segurança upstream |
| Betterleaks | 1.10.0 | Sucessor do Gitleaks (MIT), pelo mesmo autor. A série 1.x mantém a linha de comando e o relatório do Gitleaks; a **2.x ainda só tem versões candidatas** (rc.7) e muda comandos, flags e o JSON | Opt-in (`BETTERLEAKS_ENABLED=true`). Migrar para a 2.x quando sair a versão final; medir contra o Gitleaks antes de torná-lo padrão |
| Node (imagem da API e CI) | 24 | LTS ativo (fim de vida em 2028-04-30); o 22 sai de suporte em 2027-04-30 e o ESLint 10 pede 20.19+ | Avaliar o 26 quando virar LTS ativo e o ecossistema acompanhar |
| Hadolint | 2.12.0 | Licença GPL-3.0; **fora da imagem publicada**, opt-in com `WITH_HADOLINT=true` | Decidido (abaixo) |
| Stylelint | 17.16.0 | Com `stylelint-config-standard-scss` 17 e `-standard` 40, que exigem Stylelint 17; o 18 só entra quando o `-standard-scss` aceitar | Subir junto com as configs |
| Opengrep | 1.30.0 | LGPL-2.1 | Sem pendência |
| Checkov | 3.4.3 | Roda só em Dockerfile, Kubernetes e GitHub Actions; Terraform fica fora (ver LOG) | Sem pendência |

### Outras versões fixadas (lidas do `Dockerfile` em 2026-11-12)

Sem decisão de revisão pendente; entram aqui para a tabela cobrir tudo o que o build fixa.

| Item | Versão | Observação |
|---|---|---|
| PMD | 7.27.0 (SHA-256) | Subiu do 7.7.0 porque o ASM 9.7 não lia classes do Java 25 (ver LOG) |
| Checkstyle | 10.21.4 (SHA-256) | Pacote `market-tools`; também usado pelo perfil Google Java |
| kube-linter / kube-score | 0.7.2 / 1.19.0 (SHA-256) | `WITH_MARKET_TOOLS=true` |
| njsscan / SQLFluff | 1.0.2 / 4.5.0 | Instalados com pip em ambiente virtual próprio, sem hash |
| Semgrep | 1.172.0 | Motor alternativo ao Opengrep, só com `WITH_SEMGREP=true` |
| Joern | v4.0.583 (SHA-256) | Opt-in com `WITH_JOERN=true` (cerca de 2 GB) |
| JDK de build / JRE da imagem | 26 / 25 | O bytecode é Java 21 (`pom.xml`); o CI compila com 21 e roda os testes também na JVM da imagem (25) |
| Node no build do dashboard | 26 | A imagem da API e o CI usam 24; o estágio de build do dashboard (`audit-dashboard/Dockerfile`) usa `node:26-alpine` e o servidor é `nginx-unprivileged` 1.31 |

## Decisões em aberto

1. **Hadolint (GPL-3.0): decidido.** A imagem publicada não distribui mais o binário (`WITH_HADOLINT=false` por padrão). O analisador continua no pacote `market-tools` e roda quando o `hadolint` está no `PATH` (instalação própria ou build com `--build-arg WITH_HADOLINT=true`; nesse caso as obrigações da GPL passam a ser de quem distribui essa imagem). As regras de Dockerfile do `core-iac` e o Checkov cobrem o essencial sem ele. Este documento não substitui parecer jurídico.
2. **Gitleaks ou Betterleaks como padrão: decidido, Gitleaks segue padrão.** Medição de 2026-11-10 (histórico `--depth 200`, achados únicos por regra+arquivo+segredo, como o `SecretReportParser` conta): NodeGoat 4 contra 18, WebGoat 23 contra 38, angular-realworld 2 contra 5, petclinic 0 contra 1, juice-shop 68 contra 729. Jwt e chave privada saem iguais nos dois. O Betterleaks acrescenta `generic-password` (687 dos 729 do juice-shop, quase todos em testes e i18n) e `mongodb-connection-string` (4 achados reais no NodeGoat); o Gitleaks tem mais `generic-api-key` (juice-shop 46 contra 20). Ou seja, mais cobertura de senha e string de conexão, ao custo de muito ruído em fixtures. 

   **Segunda medição (2026-11-10, com `PathRole.LOCALIZATION` do #189, só achados em caminho de produção):** Gitleaks 30 contra Betterleaks 71 (NodeGoat 4/17, WebGoat 14/18, petclinic 0/1, juice-shop 12/35, angular-realworld 0/0). O excedente do Betterleaks são 17 `generic-password` e 2 `mongodb-connection-string`; conferi à mão os 18 `generic-password` fora de teste e tradução: 14 são senhas fixas de verdade em código, seed de banco ou `docker-compose.yml` (de apps propositalmente vulneráveis ou de demonstração) e 4 são falso positivo (`password:hover` em CSS e texto de tutorial em HTML). O Gitleaks pega 4 arquivos de produção que o Betterleaks não pega (`generic-api-key`). Conclusão: o Betterleaks encontra mais senha fixa de verdade, mas o Gitleaks segue como padrão porque a 2.x do Betterleaks vai mudar a linha de comando e o JSON (migrar o padrão duas vezes é custo à toa) e ainda há falso positivo em CSS/HTML. Quem audita código com credencial fixa pode ligar `BETTERLEAKS_ENABLED=true`. Próximo passo possível: tratar `generic-password` em `.css`/`.html`.

## O que a narrativa pode e não pode afirmar

- Dependências: **Trivy ou OSV.dev** (não OWASP Dependency-Check, que o Sentinel não usa).
- IaC: o Checkov cobre Dockerfile, Kubernetes e GitHub Actions; **Terraform está fora do escopo**.
- Os números de regras, pacotes, perfis e ferramentas são conferidos no CI por `scripts/conferir-numeros.py`; quem muda um pacote atualiza o `README.md` e o `docs/IMPACTO.md` na mesma hora.
- Precisão e cobertura só valem para o corpus medido (veja `docs/IMPACTO.md`); fora dele, não há número.
