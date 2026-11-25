# Documentação do Sentinel

Índice de todos os documentos do produto. O resumo está no [README da raiz](../README.md). Para contribuir: [CONTRIBUTING](../CONTRIBUTING.md); para reportar falha de segurança: [SECURITY](../SECURITY.md).

## Para começar
| Documento | Para quê |
|---|---|
| [guia/instalacao.md](guia/instalacao.md) | Subir com Compose, rodar em modo desenvolvimento, primeira auditoria, CLI para CI/CD, estrutura do projeto, migração de instalação antiga |
| [guia/certificados.md](guia/certificados.md) | Rede com inspeção SSL: instalar o CA da empresa na imagem |
| [guia/observabilidade.md](guia/observabilidade.md) | Painel no Grafana, métricas, logs, traces e alertas (Slack/Teams), com a pilha de `deploy/observabilidade/` |
| [guia/login-keycloak.md](guia/login-keycloak.md) | Login com Keycloak: usuários de teste, papéis admin e executor, como criar usuários, esqueci a senha por e-mail (Mailpit) e validação do token |
| [guia/compose-combinacoes.md](guia/compose-combinacoes.md) | Subir o Compose com Keycloak e/ou observabilidade (overlays), `COMPOSE_FILE` e o 502 depois de recriar a API |
| [guia/solucao-de-problemas.md](guia/solucao-de-problemas.md) | Erros comuns (502, 401, TLS no build, API que não sobe, hora do relatório) com causa e correção |
| [guia/operacao.md](guia/operacao.md) | Backup e restauração do PostgreSQL, atualização de versão, migrações e retenção |
| [guia/kubernetes-helm.md](guia/kubernetes-helm.md) | Instalar com o chart Helm: imagens, Secret, banco, réplicas, rede e limites |
| [guia/cache-maven.md](guia/cache-maven.md) | Cache Maven do host para o Trivy resolver POMs sem ir ao Maven Central (`M2_HOST_DIR`, `TRIVY_OFFLINE`) |
| [pasta-local/README.md](pasta-local/README.md) | Auditar uma pasta local sem Git |
| [evidencias/README.md](evidencias/README.md) | Evidências offline (schema, planos, cobertura, heap) |

## Como funciona
| Documento | Para quê |
|---|---|
| [arquitetura/ARQUITETURA.md](arquitetura/ARQUITETURA.md) | Módulos, pacotes, classes, fluxos e 14 diagramas (há uma versão em [ARQUITETURA.html](arquitetura/ARQUITETURA.html), gerada do `.md`) |
| [referencia/pacotes-de-regras.md](referencia/pacotes-de-regras.md) | Camadas de qualidade, pacotes de regras, perfis, ferramentas externas, segurança dos pacotes |
| [referencia/versoes-fixadas.md](referencia/versoes-fixadas.md) | Versões fixadas das ferramentas, política de atualização e decisões em aberto (Gitleaks) e decididas (Hadolint) |
| [referencia/variaveis-de-ambiente.md](referencia/variaveis-de-ambiente.md) | Todas as variáveis do Compose numa tabela, com padrão e para quê |
| [referencia/banco-de-dados.md](referencia/banco-de-dados.md) · [banco-de-dados.html](referencia/banco-de-dados.html) | Tabelas, relações, migrações Flyway e o diagrama do banco |
| [referencia/auditoria-e-api.md](referencia/auditoria-e-api.md) | Auditoria completa, webhooks, notificações, Configurações, OIDC e todos os endpoints |
| [referencia/iso-25010.md](referencia/iso-25010.md) | Qualidade por característica da ISO/IEC 25010: mapa, perfis de peso, metas, API, CLI e relatórios |
| [padrao-typescript/README.md](padrao-typescript/README.md) · [clean-code-labs42.md](padrao-typescript/clean-code-labs42.md) | Padrão TypeScript da casa e regras do guia clean-code-typescript |
| [padrao-google/README.md](padrao-google/README.md) | Perfis Google Java/TypeScript, SEI CERT e Sonar |
| [referencia/severidade-das-regras.md](referencia/severidade-das-regras.md) | Critério único de severidade das regras (ERROR, WARNING, INFO) |

## Como desenvolver
| Documento | Para quê |
|---|---|
| [sdd.md](sdd.md) | Desenvolvimento orientado por especificação com o GitHub Spec Kit: constituição, fluxo de comandos, ligação com issues, PRs e CI, e o estado de cada spec em [`specs/`](../specs/) |
| [figma/README.md](figma/README.md) | Telas do dashboard em PNG e HTML, nos temas claro e escuro, para levar ao Figma, e o script que as refaz |

## Integração
| Documento | Para quê |
|---|---|
| [integracao/README.md](integracao/README.md) | GitHub Actions, pre-commit, GitLab |
| [integracao/mcp.md](integracao/mcp.md) | Claude consultando o Sentinel por MCP |
| [integracao/joern.md](integracao/joern.md) | Taint entre arquivos com o Joern |
| [integracao/sentinel-yml.md](integracao/sentinel-yml.md) | Arquivo `.sentinel.yml` no projeto auditado |

## Resultados e evolução
| Documento | Para quê |
|---|---|
| [IMPACTO.md](IMPACTO.md) | O que o Sentinel faz, o que foi medido e o que falta provar |
| [evolucao/PLACAR.md](evolucao/PLACAR.md) | Placar de maturidade e backlog |
| [evolucao/LOG.md](evolucao/LOG.md) | Histórico por versão, com o que foi e não foi verificado |
| [evolucao/precisao.md](evolucao/precisao.md) · [calibracao.md](evolucao/calibracao.md) · [revisao-humana.md](evolucao/revisao-humana.md) | Precisão medida e calibração |
| [evolucao/AGENTE.md](evolucao/AGENTE.md) | Agente Sentinel Ciclo (auditoria completa em repositórios vulneráveis), com o estado entre execuções em `evolucao/estado.json` |
| [historia.md](historia.md) | Origem do projeto |
