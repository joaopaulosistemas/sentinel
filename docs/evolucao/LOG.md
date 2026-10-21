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

Atualização na mesma volta: calibração nos repositórios do João (Frequency e Sentinel) corrigiu mais 4 regras (ver `calibracao.md`). Credibilidade passa a 2 (6 repositórios, 2 deles do João), sem chegar a 3 porque não houve revisão manual completa dos achados.

Verificado: bateria Java 364 testes verdes; Semgrep dos 8 pacotes.
Não verificado: calibração só das regras (não do pipeline completo); sem revisão manual de todos os achados.

## Volta 3 — 2026-10-21

- Workflows validados com `actionlint`: o CI do projeto passou limpo; o modelo de PR tinha injeção de script (`github.head_ref` direto no `run`) e foi corrigido (valores externos por variável de ambiente).
- Desempenho medido: varredura Semgrep com as 158 regras em 1.212 arquivos (WebGoat, 42 MB) levou 28 s; o tempo de casamento das regras soma 4,9 s e nenhuma regra isolada passa de 1,6 s (`sentinel-api-path-traversal`). Sem gargalo de regra.
- Dashboard: 2 testes de acessibilidade com axe (vitest-axe) sobre a casca, o badge e a nota; sem violações (16 testes no total).
- Nenhum eixo mudou de nota nesta volta. Os bloqueios que restam são externos: build real (Maven/Quarkus/Docker) e revisão manual de achados.

## Volta 4 — 2026-10-21 (verificação em CI real)

O GitHub Actions do repositório passou a ser o ambiente de verificação: o Maven e o Docker não rodam no ambiente de desenvolvimento do Claude, mas rodam no runner. O log completo não é acessível ao Claude, então o CI publica a causa das falhas como anotações (`scripts/ci-anotar.sh` e função `anotar` no job das regras), lidas pela API de check-runs.

Verificado no CI real (4 jobs verdes no commit da volta):
- `mvn verify` completo: compilação do Quarkus e todos os testes Java.
- Imagem Docker da API e do dashboard construídas.
- `helm lint` e `helm template` do chart (com o segredo obrigatório informado).
- Testes Semgrep dos 8 pacotes com Semgrep 1.172.0 fixo, mais o verificador de SQL/XML.
- Dashboard: `tsc`, 16 testes e build.

Defeitos encontrados só por rodar de verdade, todos corrigidos:
- `PackToolTest` e `RulePlaygroundServiceTest` precisam do Semgrep instalado no job do Maven.
- O Semgrep ignora pastas `tests/` dentro de repositório git; o verificador de SQL passou a copiar os casos para outra pasta.
- Uma linha sobrando no próprio workflow derrubava o job das regras.

| Eixo | Antes | Depois | Evidência |
|---|---|---|---|
| 8 Operação | 1 | 2 | imagens e chart Helm construídos/validados em CI; OIDC e carga ainda não verificados |
| 9 Qualidade interna | 2 | 2 | suíte Java passa no Maven real, não só no harness |

Média do placar: 2,0 (meta 2,5). Faltam: revisão manual dos achados (credibilidade 3), subir o Quarkus de verdade com banco e rodar uma auditoria ponta a ponta no CI, teste do login OIDC, métricas da própria ferramenta e corrigir o N+1 do `RuleQualityService`/`AuditScheduler`.
