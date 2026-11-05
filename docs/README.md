# Documentação do Sentinel

Índice de todos os documentos do produto. O resumo está no [README da raiz](../README.md).

## Para começar
| Documento | Para quê |
|---|---|
| [guia/instalacao.md](guia/instalacao.md) | Subir com Compose, rodar em modo desenvolvimento, primeira auditoria, CLI para CI/CD, estrutura do projeto, migração de instalação antiga |
| [guia/certificados.md](guia/certificados.md) | Rede com inspeção SSL: instalar o CA da empresa na imagem |
| [pasta-local/README.md](pasta-local/README.md) | Auditar uma pasta local sem Git |
| [evidencias/README.md](evidencias/README.md) | Evidências offline (schema, planos, cobertura, heap) |

## Como funciona
| Documento | Para quê |
|---|---|
| [arquitetura/ARQUITETURA.md](arquitetura/ARQUITETURA.md) | Módulos, pacotes, classes, fluxos e 13 diagramas |
| [referencia/pacotes-de-regras.md](referencia/pacotes-de-regras.md) | Camadas de qualidade, pacotes de regras, perfis, ferramentas externas, segurança dos pacotes |
| [referencia/auditoria-e-api.md](referencia/auditoria-e-api.md) | Auditoria completa, webhooks, notificações, Configurações, OIDC e todos os endpoints |
| [padrao-typescript/README.md](padrao-typescript/README.md) · [clean-code-labs42.md](padrao-typescript/clean-code-labs42.md) | Padrão TypeScript da casa e regras do guia clean-code-typescript |
| [padrao-google/README.md](padrao-google/README.md) | Perfis Google Java/TypeScript, SEI CERT e Sonar |

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
| [evolucao/AGENTE.md](evolucao/AGENTE.md) | Agentes Sentinel Evolution e Descoberta |
| [historico-de-versoes.md](historico-de-versoes.md) · [historia.md](historia.md) | Resumo antigo por versão e origem do projeto |
