# Registro de evolução do Sentinel

Placar de 0 a 3 por eixo (0 ausente, 1 frágil, 2 funciona e é testado, 3 validado em repositório real).

## Volta 1 — 2026-10-21

Placar antes → depois (avaliado com evidência no repositório):

| Eixo | Antes | Depois | Evidência |
|---|---|---|---|
| 1 Cobertura de regras | 2 | 2 | 171 regras; segurança com CWE/OWASP; +4 CWE (System.exit, SSH, network host, printStackTrace) |
| 2 Precisão | 1 | 1 | modo sombra e supressão existem; falso positivo nunca medido em repositório real |
| 3 Padrões de saída | 2 | 2 | SARIF/JUnit no CLI, SBOM CycloneDX |
| 4 Quality gate e tendência | 2 | 2 | `--fail-below`, `--fail-on-new`, diff entre execuções |
| 5 Integração | 1 | 2 | `.github/workflows/ci.yml` do projeto, modelo de GitHub Actions para PR, modelo de pre-commit |
| 6 Remediação | 2 | 2 | uma entrada por regra exigida pelo teste estrito |
| 7 Dashboard | 1 | 2 | 14 testes (vitest + Testing Library), `tsc` e build verdes |
| 8 Operação | 1 | 1 | OIDC opcional; sem teste de carga nem imagem verificada |
| 9 Qualidade interna | 2 | 2 | 363 testes Java; verificador próprio para regras de SQL/XML |
| 10 Credibilidade | 0 | 0 | sem calibração em repositório real |

Lacunas atacadas: integração/CI (eixo 5), testes do dashboard (eixo 7), CWE nas regras sem mapeamento.

Verificado: bateria Java (363), testes Semgrep dos 8 pacotes (SQL via `audit-rules/scripts/testar-regex-sql.py`), `vitest` (14), `tsc`, `vite build`.
Não verificado: os workflows do GitHub não rodaram num GitHub real; Maven, imagem Docker e Quarkus real não foram executados aqui.

## Volta 2 — 2026-10-21

| Eixo | Antes | Depois | Evidência |
|---|---|---|---|
| 1 Cobertura de regras | 2 | 2 | recall verificado no WebGoat; +1 regra (`command-injection-shell`), 1 regra de desserialização corrigida |
| 2 Precisão | 1 | 2 | falsos positivos medidos em 4 repositórios reais e corrigidos (ver `calibracao.md`); 134→80 e 5619→1609 achados |
| 10 Credibilidade | 0 | 1 | calibração em 4 repositórios públicos; faltam projetos do João (precisa de 3 reais para chegar a 3) |

Verificado: bateria Java 364 testes verdes; Semgrep dos 8 pacotes.
Não verificado: calibração só das regras (não do pipeline completo); sem revisão manual de todos os achados.
