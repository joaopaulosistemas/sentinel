# Variáveis de ambiente

Tabela única das variáveis do Compose. No dia a dia só se mexe em poucas; o `.env.example` traz as que valem a pena decidir, e o `docker-compose.yml` é a lista completa. Ferramentas, pacotes e Git também mudam na tela **Configurações**, e o valor salvo lá vale mais que o `.env`.

## Obrigatórias e de segurança

| Variável | Padrão | Para quê |
|---|---|---|
| `POSTGRES_PASSWORD` | (obrigatória) | Senha do PostgreSQL |
| `API_KEY` | vazia | Chave exigida em toda a API (`X-API-Key` ou `Authorization: Bearer`). Sem ela e sem OIDC a API recusa subir |
| `ALLOW_OPEN_API` | `false` | `true` libera a API sem chave. Só em desenvolvimento local |
| `SECRETS_KEY` | vazia | Cifra tokens Git, segredos de webhook e URLs de notificação. Vazia usa a `API_KEY`; sem as duas, a API cria uma chave no volume `sentinel-audit-secrets` |
| `ALLOWED_INTERNAL_HOSTS` | vazia | Hosts Git de rede interna que a API pode auditar (lista separada por vírgula) |
| `CORS_ORIGINS` | `http://localhost:3000` | Só se o painel for servido de outra origem |
| `PLAYGROUND_ENABLED` | `true` | Laboratório de regras (roda o Semgrep sobre texto enviado). Desligue em instalação compartilhada |

## Relatórios e interface

| Variável | Padrão | Para quê |
|---|---|---|
| `REPORT_TIMEZONE` | `America/Sao_Paulo` | Fuso da hora nos relatórios HTML/PDF. O painel usa o do navegador |
| `SWAGGER_UI` | `true` | Swagger UI em `/swagger-ui`. É build arg: mudar exige `--build` |

## Capacidade e retenção

| Variável | Padrão | Para quê |
|---|---|---|
| `AUDIT_MAX_CONCURRENT` | `2` | Auditorias simultâneas; as outras esperam vaga |
| `AUDIT_RETENTION_DAYS` | `0` | Apaga auditorias antigas por dia (`0` desliga) |
| `AUDIT_RETENTION_KEEP` | `20` | Quantas auditorias recentes de cada sistema nunca são apagadas |

## Ferramentas (build args: valem no próximo `--build`)

`WITH_OPENGREP`, `WITH_TRIVY`, `WITH_GITLEAKS`, `WITH_BETTERLEAKS`, `WITH_PMD`, `WITH_ESLINT` e `WITH_MARKET_TOOLS` vêm ligadas. `WITH_SEMGREP`, `WITH_JOERN` (+2 GB) e `WITH_HADOLINT` (GPL-3.0) vêm desligadas. Em execução: `TRIVY_ENABLED`, `TRIVY_SCANNERS` (`vuln,misconfig`), `GITLEAKS_ENABLED`, `BETTERLEAKS_ENABLED` (`false`), `PMD_ENABLED`, `ESLINT_ENABLED`, `ESLINT_CONFIG` e `CLEANCODE_*` (limites de função, parâmetros, complexidade e aninhamento).

## Rede

| Variável | Padrão | Para quê |
|---|---|---|
| `HTTP_PROXY`, `HTTPS_PROXY`, `NO_PROXY` | vazias | Proxy usado no build |
| `INSECURE_SSL` | `false` | `true` desliga a verificação TLS **só durante o build**, em rede que reassina o TLS e sem o CA em `audit-api/certs/` ([certificados](../guia/certificados.md)) |
| `OSV_ENABLED`, `OSV_URL` | `true`, `https://api.osv.dev` | Consulta de vulnerabilidades. Rede fechada: `false` ou um espelho interno |
| `TRIVY_SKIP_DB_UPDATE`, `TRIVY_OFFLINE` | `false` | Rede fechada e [cache Maven](../guia/cache-maven.md) |

## Pastas no host

`EVIDENCE_HOST_DIR` (`./evidencias`), `M2_HOST_DIR` (`./.m2-cache`), `PROJECTS_HOST_DIR` (`./projetos`) e `SENTINEL_LOCAL_ROOT` (vazia: pasta local desligada; para ligar, `/projects`). Veja [evidências](../evidencias/README.md) e [pasta local](../pasta-local/README.md).

## Pacotes de regras

`PACKS_DIRS`, `PACKS_GIT_URL`, `PACKS_GIT_REF` (`main`), `PACKS_GIT_PATH`, `PACKS_DISABLED`, `PACKS_ALLOW_TOOLS` (`false`), `PACKS_TRUSTED_TOOL_PACKS`, `PACKS_PUBLIC_KEY` e `PACKS_REQUIRE_SIGNATURE` (`false`). Detalhes e riscos em [pacotes-de-regras.md](pacotes-de-regras.md).

## Login (OIDC)

`OIDC_AUTH_SERVER_URL`, `OIDC_CLIENT_ID` e `OIDC_AUDIENCE`, com `QUARKUS_PROFILE=oidc` e a imagem construída com `WITH_OIDC=true`. Veja [login-keycloak.md](../guia/login-keycloak.md).

## Só no desenvolvimento

`DB_URL`, `DB_USER`, `DB_PASSWORD` (a API fora do Compose), `DB_SCHEMA_MODE` (`none`; nunca `drop-and-create` fora de testes) e `SCHEDULER_ENABLED` (`true`; réplicas além da primeira devem rodar com `false`).
