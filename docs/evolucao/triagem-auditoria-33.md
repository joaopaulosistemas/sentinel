# Triagem da auditoria #33 do Sentinel (auditoria do próprio Sentinel)

Resultado: nota 25,4; 0 CRITICAL, 53 HIGH, 358 MEDIUM, 396 LOW, 7 INFO (814 achados). PMD e CleanCode somam 79%.
Cada linha abaixo foi conferida no código. Os textos da coluna "Justificativa" servem para colar na supressão do achado (tela Achados → Suprimir, como falso positivo ou risco aceito).

| Achado | Quantos | Veredito | Justificativa |
|---|---|---|---|
| Gitleaks em fixtures de teste (`ExternalScannersTest`, `ProjectProfilerTest`, `AnalyzerRulesTest`, `SecretsAnalyzerTest`, `core-security/tests/sentinel-java-extras.java`) | 6 HIGH | Falso positivo | Segredos de mentira, escritos de propósito para testar os detectores. |
| Gitleaks em `docs/evolucao/relatorios/2026-10-22.md` | 1 HIGH | Falso positivo | Relatório que cita um token de exemplo (`token=abc123def456`) de outro projeto. |
| Gitleaks em `core-iac/remediation/remediation.yml:122,136` | 2 HIGH | Falso positivo | O detector confundiu o campo YAML `key:` com chave de API; não há segredo. |
| Gitleaks em `.env.example:15` | 1 HIGH | **Verificar** | Na `main` essa linha é um comentário e não há token. Se o seu arquivo local tiver um token real, revogue. |
| Semgrep em `sentinel-compose-k8s.yml:35,77` (`network_mode: host`, `allowPrivilegeEscalation`) | 1 HIGH, 1 MEDIUM | Falso positivo | É a própria regra do pacote, que traz o padrão como texto. |
| "Command injection" em `IngestionService`, `ExternalTools`, `SemgrepAnalyzer`, `ChangedFiles`, `RulePlaygroundService` | 1 HIGH, 7 MEDIUM | Falso positivo (conferido no `IngestionService`; os outros seguem o mesmo padrão e não foram abertos um a um) | `ProcessBuilder` com lista de argumentos, `--` antes da URL e diretório temporário criado pela API; nada passa por shell. |
| PMD `HardCodedCryptoKey` em `SecretBox.java` | 1 MEDIUM | Falso positivo | A chave é o SHA-256 de `SECRETS_KEY` ou `API_KEY`, lida do ambiente. |
| PMD `SystemPrintln` e `DoNotTerminateVM` em `AuditCli` | cerca de 35 MEDIUM | Falso positivo | É uma CLI: imprimir na saída e sair com código é o comportamento esperado. |
| Semgrep "recurso JAX-RS sem `@RolesAllowed`" | cerca de 14 MEDIUM | Decisão, não defeito | A autenticação é global, pelo `ApiKeyFilter`; o MCP tem chave própria por sistema. |
| Arquitetura "Controller acessa repositório" | 9 HIGH | Decisão | Convenção do projeto (recursos usam repositórios Panache diretamente). Mudar é refatoração, não correção. |
| PMD/Semgrep `ReturnEmptyCollectionRatherThanNull` em `Versions` e `Yamls` | 7 HIGH, 3 MEDIUM | Falso positivo | `null` significa "não consegui interpretar" e é diferente de lista vazia; os chamadores testam `null`. Trocar mudaria o comportamento. |

## Real e ainda aberto

- N+1 e gravação em loop: `FindingResource`, `ReportResource` (3), `SystemResource:388`, `AuditScheduler:108`, `FindingLifecycleService:252`, `RuleQualityService` (2), `AuditOrchestrationService:629`. O volume é proporcional ao número de sistemas, então o ganho é pequeno hoje; corrigir pede consultas novas nos repositórios e teste com banco.
- Chart Helm com `:latest` (`deploy/helm/sentinel/values.yaml`): fixar a versão depende de como as imagens são publicadas.
- Dockerfile do dashboard roda o nginx oficial como root: trocar por imagem sem privilégios muda a porta e o `nginx.conf`.
- Complexidade e funções longas (193 CleanCode): `FindingsPage`, `EnginePanel`, `AuditRunPage`, `SystemsPage`, `PackLoader`, `PackRegistry` e outros.

## Corrigido a partir desta triagem

- `RuleCatalog`: os dois `catch` vazios agora registram em nível debug.
- Dashboard: comparações `!= null` trocadas por comparação estrita (`ExecutionPanels`, `SystemDetailPage`), aspas sem escape no `EnginePanel` e ramo duplicado no `ReauditPanel`.

## Ideia para o próprio Sentinel

Não existe exclusão de caminhos por sistema (o Semgrep tem uma lista fixa de pastas de terceiros e o detector de segredos ignora algumas pastas). Uma lista de caminhos ignorados por sistema, para fixtures de teste, `docs/` e arquivos de regras, tiraria da nota cerca de 10 achados HIGH desta auditoria. É mudança de contrato da API e fica para decisão sua.
