# Documentação do Sentinel

Índice de todos os documentos do produto. O resumo está no [README da raiz](../README.md).

## Para começar
| Documento | Para quê |
|---|---|
| [guia/instalacao.md](guia/instalacao.md) | Subir com Compose, rodar em modo desenvolvimento, primeira auditoria, CLI para CI/CD, estrutura do projeto, migração de instalação antiga |
| [guia/certificados.md](guia/certificados.md) | Rede com inspeção SSL: instalar o CA da empresa na imagem |
| [guia/cache-maven.md](guia/cache-maven.md) | Cache Maven do host para o Trivy resolver POMs sem ir ao Maven Central (`M2_HOST_DIR`, `TRIVY_OFFLINE`) |
| [pasta-local/README.md](pasta-local/README.md) | Auditar uma pasta local sem Git |
| [evidencias/README.md](evidencias/README.md) | Evidências offline (schema, planos, cobertura, heap) |

## Como funciona
| Documento | Para quê |
|---|---|
| [arquitetura/ARQUITETURA.md](arquitetura/ARQUITETURA.md) | Módulos, pacotes, classes, fluxos e 14 diagramas (há uma versão em [ARQUITETURA.html](arquitetura/ARQUITETURA.html), gerada do `.md`) |
| [referencia/pacotes-de-regras.md](referencia/pacotes-de-regras.md) | Camadas de qualidade, pacotes de regras, perfis, ferramentas externas, segurança dos pacotes |
| [referencia/versoes-fixadas.md](referencia/versoes-fixadas.md) | Versões fixadas das ferramentas, política de atualização e decisões em aberto (Gitleaks) e decididas (Hadolint) |
| [referencia/auditoria-e-api.md](referencia/auditoria-e-api.md) | Auditoria completa, webhooks, notificações, Configurações, OIDC e todos os endpoints |
| [padrao-typescript/README.md](padrao-typescript/README.md) · [clean-code-labs42.md](padrao-typescript/clean-code-labs42.md) | Padrão TypeScript da casa e regras do guia clean-code-typescript |
| [padrao-google/README.md](padrao-google/README.md) | Perfis Google Java/TypeScript, SEI CERT e Sonar |
| [referencia/severidade-das-regras.md](referencia/severidade-das-regras.md) | Critério único de severidade das regras (ERROR, WARNING, INFO) |
| [referencia/claude-md-spring-boot.md](referencia/claude-md-spring-boot.md) · [claude-md-typescript.md](referencia/claude-md-typescript.md) | Exemplos do CLAUDE.md que o Guia de código gera (cópias de 2026-11-06, ajustadas ao gerador 12.64.0) |

## Como desenvolver
| Documento | Para quê |
|---|---|
| [sdd.md](sdd.md) | Desenvolvimento orientado por especificação com o GitHub Spec Kit: constituição, fluxo de comandos, ligação com issues, PRs e CI. Specs em [`specs/`](../specs/) |

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
| [evolucao/triagem-auditoria-33.md](evolucao/triagem-auditoria-33.md) · `evolucao/relatorios/` | Triagem da auditoria #33 do próprio Sentinel (justificativas prontas para suprimir) e relatórios das rodadas de 2026-10-21 a 28 |
| [historico-de-versoes.md](historico-de-versoes.md) · [historia.md](historia.md) | Resumo antigo por versão e origem do projeto |
