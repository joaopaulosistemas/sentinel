# Instalação, execução e CLI

## Pré-requisitos

| Para | Precisa de |
|---|---|
| Subir tudo com Compose (recomendado) | Docker (ou Podman) com Compose, Git |
| Rodar a API em modo desenvolvimento | Java 21, Maven 3.9+ (o Quarkus 3.33 recusa Maven mais antigo; o do `apt` costuma ser antigo), PostgreSQL 16+ (o Compose usa 16) |
| Rodar o painel em modo desenvolvimento | Node.js 20+ |

## Opção 1 — Tudo via Compose (recomendado)

```bash
git clone https://github.com/joaopaulosistemas/sentinel.git && cd sentinel
cp .env.example .env     # defina POSTGRES_PASSWORD (obrigatória); API_KEY recomendada
docker compose up -d --build
```

- **Painel:** http://localhost:3000 · **API:** http://localhost:8080 (Swagger só em modo dev).
- O Compose expõe tudo apenas em `127.0.0.1`. O painel injeta a `API_KEY` no proxy (nginx), então a chave não vai para o navegador.
- **Ferramentas na imagem da API** (build args, todos no `.env.example` ou em `docker-compose.yml`): `WITH_OPENGREP`, `WITH_TRIVY`, `WITH_GITLEAKS`, `WITH_PMD`, `WITH_ESLINT` e `WITH_MARKET_TOOLS` vêm ligados; `WITH_SEMGREP` e `WITH_JOERN` (taint entre arquivos, cerca de 2 GB a mais) vêm desligados. Exemplo: `docker compose build --build-arg WITH_JOERN=true audit-api`.
- **Rede com inspeção SSL:** coloque o CA raiz da empresa (`.crt`, PEM) em `audit-api/certs/` antes do build. Proxy: `HTTP_PROXY`, `HTTPS_PROXY` e `NO_PROXY` no `.env`.
- **Só o banco** (para a API em modo dev): `docker compose up -d postgres` (porta local 5433).

## Opção 2 — Desenvolvimento local

1. **Banco:** `docker compose up -d postgres` e `export DB_URL=jdbc:postgresql://127.0.0.1:5433/sentinel_audit DB_PASSWORD=<POSTGRES_PASSWORD>`. Podman rootless no WSL2 isola a rede do host: nesse caso use PostgreSQL instalado no próprio WSL2 ou rode API e painel pelo Compose.
2. **API:**
   ```bash
   mvn quarkus:dev -s settings-local.xml -f audit-api/pom.xml
   ```
   Pronto quando aparecer `Listening on: http://localhost:8080`. O Swagger fica em http://localhost:8080/swagger-ui (só em modo dev).


Variáveis úteis (todas opcionais em dev):

| Variável | Para quê |
|----------|----------|
| `API_KEY` | Se definida, **toda** a API exige `X-API-Key: <chave>` (ou `Authorization: Bearer`). `/api/v1/health` é livre. Sem ela a API fica aberta e um aviso é logado. |
| `DB_URL` / `DB_USER` / `DB_PASSWORD` | Banco. Com o Postgres do Compose use `DB_URL=jdbc:postgresql://localhost:5433/sentinel_audit`. |
| `DB_SCHEMA_MODE` | Padrão `update`. **Nunca** use `drop-and-create` fora de testes: apaga os dados a cada start. |
| Token Git (cadastro do sistema) | Fica cifrado no banco e nunca volta pela API. A chave vem de `SECRETS_KEY` (ou `API_KEY`); se as duas estiverem vazias, a API cria uma chave aleatória no volume `audit_secrets` (`/var/lib/sentinel/secrets.key`) e a reutiliza, então o token pode ser digitado só na tela. Guarde esse volume junto com o banco: sem a chave os tokens salvos não abrem; só é enviado aos hosts listados no sistema (padrão `github.com`); só URLs `https://` são aceitas. |
| `CORS_ORIGINS` | Origens permitidas (padrão `http://localhost:3000`). |
| `SEMGREP_CONFIG` | Regras do Semgrep (padrão: regras dos pacotes em `audit-rules/packs`; sem pacotes, `p/default`). |

3. **Painel** (outro terminal):
   ```bash
   cd audit-dashboard && npm install && npm run dev
   ```
   Acesse http://localhost:3000. O Vite faz proxy de `/api` para `http://localhost:8080` (use `API_URL` e `API_KEY` no ambiente do `npm run dev` se mudar a porta ou ativar a chave).

## Primeira auditoria pela API


```bash
# Se API_KEY estiver ativa, acrescente: -H "X-API-Key: $API_KEY"

# Cadastrar sistema
curl -X POST http://localhost:8080/api/v1/systems \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Portal Cooperado",
    "description": "Front-office principal",
    "repoUrl": "https://github.com/seu-org/seu-repo.git",
    "stack": "JAVASCRIPT",
    "team": "Time Portal"
  }'

# Disparar auditoria
curl -X POST http://localhost:8080/api/v1/audits \
  -H "Content-Type: application/json" \
  -d '{"systemId": 1, "branch": "main", "triggeredBy": "voce@empresa.com.br"}'

# Acompanhar resultado
curl http://localhost:8080/api/v1/audits/1

# Ver findings críticos
curl "http://localhost:8080/api/v1/audits/1/findings?severity=CRITICAL"
```

O mesmo pode ser feito na tela **Sistemas** do painel.

---

### Rodar os Testes

```bash
# WSL2 / Linux
cd /caminho/para/sentinel

mvn test \
  -s settings-local.xml \
  -f pom.xml \
  -pl audit-api

# Resultado esperado: BUILD SUCCESS, 0 falhas
```

---

### CLI para CI/CD

```bash
# Build do CLI
mvn package -DskipTests \
  -s settings-local.xml \
  -f audit-cli/pom.xml

# Usar no pipeline — falha o build se score < 60
java -jar audit-cli/target/sentinel-audit-cli.jar \
  --api-url http://sentinel-audit.internal:8080 \
  --system-id 5 \
  --branch main \
  --fail-below 60 \
  --api-key "$AUDIT_API_KEY"      # ou apenas exporte AUDIT_API_KEY

# Códigos de saída: 0 = aprovado, 1 = reprovado pelo gate, 2 = erro (API fora, 401, auditoria FAILED), 3 = timeout
```

Opções extras do CLI:

| Opção | Efeito |
|-------|--------|
| `--fail-on HIGH` | Reprova se houver qualquer finding daquela severidade (ou acima) |
| `--json resultado.json` | Grava os findings em JSON |
| `--fail-on-new CRITICAL` | Reprova se surgir finding **novo** daquela severidade (ou acima) desde a auditoria anterior |
| `--fail-on-score-drop 0` | Catraca da nota: reprova se ela cair mais que esses pontos desde a auditoria anterior (sem anterior, não reprova) |
| `--sarif relatorio.sarif` | Grava SARIF 2.1.0 (GitHub Code Scanning, Azure DevOps, GitLab) |
| `--junit relatorio.xml` | Grava JUnit XML (aparece como teste no pipeline) |

---

## Estrutura do Projeto

```
sentinel/
├── pom.xml                         ← Parent Maven multi-módulo
├── settings-local.xml              ← Maven settings (fora da rede corporativa)
├── docker-compose.yml              ← Ambiente completo local
├── .env.example                    ← Template de variáveis de ambiente
├── docs/                           ← Toda a documentação (índice em docs/README.md); schema-referencia.sql é só referência
│
├── audit-api/                      ← Backend Quarkus 3.33 + Java 21
│   ├── Dockerfile
│   └── src/main/java/com/sentinel/audit/
│       ├── model/                  ← Entidades JPA + enums
│       ├── repository/             ← Repositórios Panache
│       ├── dto/                    ← DTOs de request/response
│       ├── service/                ← Lógica de negócio
│       ├── analyzer/               ← Motores de análise
│       ├── insights/               ← Áreas, mapa do projeto, dívida técnica (P0–P3), quick wins e roadmap
│       ├── tuning/                 ← Ajuste do motor por sistema (regras, limites, pacotes, analisadores)
│       ├── mcp/                    ← Servidor MCP: chave por sistema, ferramentas, nível de dado, limite de chamadas
│       ├── review/                 ← Revisão do Claude: sugestões, regras propostas, funil e métricas
│       ├── actions/                ← Pedidos do Claude que só acontecem com aprovação (iniciar auditoria, suprimir achado)
│       └── resource/               ← Endpoints REST
│
├── audit-rules/
│   ├── packs/                      ← Pacotes de regras (core-security, core-iac, core-spring, core-persistencia, core-quality, core-frontend, core-corporativo, market-tools, ts-clean-code, ts-clean-code-labs42, core-architecture, e os de adesão opcional core-quarkus, core-vue e core-boas-praticas) e profiles/
│   ├── optional/                   ← Regras que duplicam os analisadores Java (desativadas)
│   ├── lint/eslint/                ← Configuração do ESLint usada pelo analisador
│   └── bin/sentinel-pack           ← Ferramenta de linha de comando para autores de pacotes
│
├── deploy/helm/sentinel/           ← Chart Helm (API, dashboard, PostgreSQL opcional) — `helm lint` e `helm template` rodam no CI
│
├── audit-dashboard/                ← Frontend React 18 + IBM Carbon DS
│   ├── Dockerfile + nginx.conf.template
│   └── src/
│       ├── pages/                  ← Overview, Systems, SystemDetail, AuditRun, Ranking, Findings (Achados, Revisão do Claude, Reauditoria), Reports
│       ├── components/             ← Layout/AppShell, RiskBadge, ScoreGauge, FindingsTable, InsightsPanel, TrendChart, SuppressModal, EnginePanel, McpPanel, ClaudeReviewPanel, ReauditPanel, StartAuditModal
│       ├── hooks/                  ← TanStack Query (useSystems, useFindings, useAuditRuns, useLifecycle)
│       ├── store/                  ← Zustand (filtros, sistema selecionado)
│       ├── api/                    ← Client tipado para o backend
│       └── types/                  ← Interfaces TypeScript
│
├── audit-cli/                      ← CLI Java para integração CI/CD
│   └── src/main/java/.../AuditCli.java
│
├── .github/workflows/              ← ci.yml, evolucao.yml, descoberta.yml, release.yml
├── .github/agente/                 ← instruções dos agentes Sentinel Evolution e Sentinel Descoberta
├── scripts/                        ← e2e, calibração, descoberta de repositórios, relatórios, anotações de CI
└── evidencias/                     ← Uma subpasta por sistema com dumps e exports para a análise offline
```

---

## Migrando de uma instalação antiga (antes do nome Sentinel)

O projeto foi renomeado por completo para **Sentinel**: pacotes Java (`com.sentinel.audit`), ids de regra
(`sentinel-*`), propriedades (`sentinel.audit.*`), banco (`sentinel_audit`) e jars (`sentinel-audit-*`).
Quem já tinha a versão anterior rodando, com o nome antigo, precisa:

1. **Banco:** renomear (ou recriar) o banco e o usuário, ou manter os antigos apontando `DB_URL`, `DB_USER` e `DB_PASSWORD` para eles:
   ```sql
   ALTER DATABASE <nome_antigo> RENAME TO sentinel_audit;
   ALTER ROLE <nome_antigo> RENAME TO sentinel_audit;   -- renomear o usuário limpa a senha: defina-a de novo com ALTER ROLE ... PASSWORD
   ```
2. **Histórico de findings:** os ids de regra mudaram, então o *fingerprint* de cada finding também. Na primeira auditoria depois da migração,
   tudo aparece como "novo" e os findings antigos como "resolvidos"; supressões (falsos positivos) cadastradas antes **não valem mais** e precisam ser refeitas.
3. **Variáveis e arquivos próprios:** troque as propriedades `<nome_antigo>.audit.*` por `sentinel.audit.*` em configurações, e os prefixos `<nome_antigo>-*` por `sentinel-*`
   nas suas regras Semgrep, supressões e scripts de CI (o jar do CLI agora é `sentinel-audit-cli.jar`).
