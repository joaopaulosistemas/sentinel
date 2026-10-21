# Sentinel Audit Tool

> Plataforma de auditoria técnica contínua e automatizada para os sistemas do Sicoob —
> construída em **menos de 1 dia** com IBM Bob como engenheiro de software.

---

## Objetivo

Realizar uma **auditoria técnica completa dos projetos Sicoob**, identificando:

- **Problemas reais** — vulnerabilidades, falhas de segurança, código defeituoso
- **Riscos técnicos** — débito acumulado, dependências obsoletas, arquitetura frágil
- **Oportunidades de melhoria** — padrões modernos que ainda não foram adotados
- **Boas práticas ausentes** — testes, logs, contratos de API, separação de responsabilidades

O objetivo final não é apenas gerar um relatório de erros — é **elevar cada sistema ao padrão de um software profissional, sustentável e preparado para evolução**.

A ferramenta faz isso de forma contínua, automatizada e rastreável, cobrindo os dois alvos principais:

| Alvo | Escopo |
|------|--------|
| **🖥️ Front-office** | Componentes visuais — portais, SPAs, interfaces do cooperado |
| **⚙️ Back-office** | Componentes de API — serviços, regras de negócio, integrações |

---

## O Problema

O Sicoob opera dezenas de sistemas distribuídos entre squads diferentes — APIs de back-office, portais de front-office, integrações financeiras. Com times independentes, stacks variadas (Java, JavaScript, .NET, COBOL) e pressão constante de entrega, a qualidade técnica é monitorada de forma fragmentada ou reativa.

**O que acontece sem auditoria contínua:**

- Vulnerabilidades de segurança entram em produção sem serem detectadas
- Débito técnico acumula silenciosamente por meses ou anos
- Secrets hardcoded (senhas, tokens de API) ficam expostos no código
- Dependências com CVEs críticas continuam em uso após patches disponíveis
- Não há visibilidade consolidada de qual sistema está em pior estado
- Times descobrem problemas apenas quando explorados ou quando o sistema para

A pergunta que motivou esta ferramenta é simples:

> *"Se eu precisasse auditar 50 sistemas amanhã, como faria de forma consistente, rastreável e sem depender de memória individual?"*

---

## A Solução

A **Sentinel Audit Tool** é uma plataforma que automatiza a auditoria técnica completa — ela clona repositórios Git diretamente, executa múltiplos analisadores em paralelo, calcula um Risk Score por sistema e entrega os resultados em dashboard visual e relatórios exportáveis.

```
Repositório Git
      │
      ▼
┌─────────────────────────────────────────────────────────────┐
│                     SENTINEL AUDIT TOOL                     │
│                                                             │
│  ┌──────────────┐  ┌───────────────┐  ┌─────────────────┐  │
│  │  Ingestion   │→ │   Analyzers   │→ │  Risk Scoring   │  │
│  │  Git Clone   │  │  Secrets      │  │  Segurança 35%  │  │
│  │  (https)     │  │  OWASP Deps   │  │  Qualidade 25%  │  │
│  │              │  │  Arquitetura  │  │  Arquit.   20%  │  │
│  └──────────────┘  │  Semgrep SAST │  │  Deps      10%  │  │
│                    └───────────────┘  │  Observab. 10%  │  │
│                                       └─────────────────┘  │
│                              │                              │
│              ┌───────────────┼───────────────┐             │
│              ▼               ▼               ▼             │
│         Dashboard        Relatório        CLI              │
│         React +          HTML             CI/CD Gate       │
│         Carbon DS        Exportável       (exit code)      │
└─────────────────────────────────────────────────────────────┘
```

### Estado atual e limitações (leia antes de confiar nos números)

| Item | Situação |
|------|----------|
| **Trivy e Gitleaks (opcionais)** | Se `trivy` e `gitleaks` estiverem no PATH (a imagem Docker já os instala; `WITH_TRIVY`/`WITH_GITLEAKS=false` desligam), eles **substituem** o SCA e os analisadores de segredos internos, com muito mais cobertura (inclusive IaC/Dockerfile/Kubernetes no Trivy e histórico completo no Gitleaks). Sem eles, os analisadores internos descritos abaixo continuam como fallback. `TRIVY_SCANNERS=vuln` desliga a checagem de IaC do Trivy (que se sobrepõe às regras Semgrep de Dockerfile/Terraform e pode contar o mesmo problema duas vezes). O Trivy baixa sua base de vulnerabilidades na primeira execução; em rede fechada use `TRIVY_SKIP_DB_UPDATE=true` com a base no cache. O valor de um segredo nunca é gravado: só um hash curto. **Duplicatas entre ferramentas:** o mesmo problema reportado pelo Semgrep e pelo Trivy é juntado em um só (fica o do Semgrep, com a maior severidade e uma nota citando o outro). Hoje isso cobre só dois casos conhecidos (Dockerfile com `:latest` e sem `USER`); regras sem equivalência declarada nunca são tocadas, então ainda pode haver duplicatas em IaC/Terraform. Para ampliar, acrescente um tópico em `FindingDeduplicator`. |
| **SCA interno (fallback)** | Lê `pom.xml`, `package-lock.json`/`package.json`, Gradle, `requirements.txt` e `.csproj` e consulta o **OSV.dev** (só nome/versão saem da rede). Sem rede, desliga com `OSV_ENABLED=false` ou usa um espelho em `OSV_URL`; nesse caso cai para uma lista interna pequena. Pacotes sem versão fixa (ranges) são avaliados pela versão mínima declarada. |
| **Secrets** | Regex nos arquivos do commit **e no histórico do Git** (últimos `GIT_HISTORY_DEPTH` commits, padrão 100; `0`/`1` desliga). Segredo removido do código mas presente no histórico continua sendo reportado — e precisa ser **revogado**, não só apagado. |
| **Semgrep (SAST)** | A imagem Docker da API instala o Semgrep (build arg `WITH_SEMGREP`, padrão ligado). Fora do Docker, só roda se o `semgrep` estiver no PATH; sem ele o SAST é ignorado com um WARNING. Regras em `audit-rules/` (injeção, XSS, OWASP A01, OWASP API Top 10, criptografia, Dockerfile, Terraform). |
| **Acompanhamento** | Cada finding tem uma identidade estável entre auditorias: o painel mostra **novos, resolvidos e persistentes**, e a **tendência** do score. |
| **Falso positivo / risco aceito** | Suprime-se um finding com motivo, justificativa e (opcional) validade. Suprimidos saem do score e da listagem padrão, mas ficam registrados e aparecem no relatório. |
| **Qualidade das regras** | O painel consolidado mostra, por regra, quantos findings foram marcados como falso positivo. Pede revisão com 5+ ocorrências e 40%+ de falsos positivos (limites fixos no código, sem calibração ainda). Mede a decisão do time, não a verdade: se ninguém suprime, a taxa fica em zero. Findings de dependência não entram. |
| **Orientação de correção** | Cada finding traz o que fazer, exemplo antes/depois e referência (catálogo estático por regra — não é IA). |
| **Automação** | Auditoria periódica por sistema, auditoria a cada push (webhook GitHub/GitLab) e aviso em Teams/Slack/webhook genérico. Veja a seção abaixo. |
| **Relatório e SBOM** | Relatório HTML pronto para imprimir/salvar como PDF no navegador. SBOM **CycloneDX 1.5** (JSON) por auditoria. |
| **Login corporativo** | Opcional via OIDC/Keycloak (perfil Maven `oidc`): autentica o usuário. **Não** há restrição por squad: quem autentica vê todos os sistemas. |
| **Narrativa por IA** | **Não implementada.** Só faz sentido depois de a base de findings estar calibrada com o time de segurança; o ponto de extensão é o campo `aiRecommendation` do finding. |
| **Testes** | Cobrem analisadores, scoring, ingestão, orquestração, ciclo de vida dos findings, webhooks, agendador, notificações, SBOM e relatório. O teste com PostgreSQL real (Testcontainers) existe mas só roda com Docker: `mvn verify -Pintegration`. |

### O que a ferramenta audita

A ferramenta cobre os dois alvos principais dos sistemas Sicoob:

#### 🖥️ Front-office — Componentes Visuais

Portais, SPAs e interfaces do cooperado (Angular, React, Vue.js).

| O que audita | Exemplos práticos |
|-------------|------------------|
| **Segurança client-side** | XSS em inputs sem sanitização, dados sensíveis expostos no JavaScript |
| **Dependências npm vulneráveis** | CVEs em libs JavaScript — `lodash`, `axios`, versões antigas do Angular |
| **Secrets no frontend** | API keys hardcoded no `.js`, tokens no `localStorage` |
| **Qualidade de código JS/TS** | `console.log` em produção, `innerHTML` com dados do usuário |
| **Cobertura de testes** | Ausência de testes unitários e E2E em fluxos críticos |

#### ⚙️ Back-office — Componentes de API

Serviços REST/SOAP, regras de negócio e integrações (Java, .NET, Python, COBOL).

| O que audita | Exemplos práticos |
|-------------|------------------|
| **Secrets hardcoded** | `password=abc123`, connection strings com credenciais no código Java |
| **OWASP API Security Top 10** | SQL Injection, Broken Access Control, endpoints sem autenticação |
| **CVEs em dependências Java** | log4shell, Spring4Shell, `jackson-databind` antigas |
| **Arquitetura** | God Objects (>500 linhas), controller acessando repositório direto |
| **Qualidade de código** | `System.out.println` em produção, complexidade ciclomática alta |
| **Observabilidade** | Ausência de logs em operações críticas, dados sensíveis em logs |

---

## Como o IBM Bob Construiu Esta Solução

Este projeto foi desenvolvido inteiramente com **IBM Bob como engenheiro de software** — não como assistente, mas como executor direto das tarefas.

### O fluxo de trabalho

```
Humano (Product Owner)          IBM Bob (Engenheiro)
─────────────────────           ────────────────────
Descreve o problema         →   Faz perguntas de refinamento
Responde às perguntas       →   Gera SENTINEL-AUDIT-TOOL.md (especificação)
Aprova a especificação      →   Implementa backend completo (43 arquivos Java)
                            →   Implementa frontend React + Carbon DS
                            →   Cria regras Semgrep YAML
                            →   Cria CLI Java para CI/CD
                            →   Cria Docker Compose
                            →   Valida build (mvn compile + 15 testes)
                            →   Corrige erros de compilação
                            →   Gera README e documentação
```

### Capacidades do Bob usadas

| Capacidade | Como foi usada |
|-----------|---------------|
| **Leitura de codebase** | Bob analisou o projeto legado Weather Demo para entender o contexto Sicoob |
| **Geração de especificação** | Bob entrevistou o usuário e criou o documento técnico completo |
| **Implementação multi-arquivo** | Bob criou 50+ arquivos simultaneamente via subtasks paralelas |
| **Execução de comandos** | Bob rodou `mvn compile`, `mvn test`, corrigiu erros em tempo real |
| **Diagnóstico de ambiente** | Bob identificou conflito Java 8 vs Java 21, Maven 3.0.5 vs 3.9.x e resolveu |
| **Raciocínio técnico** | Bob escolheu Quarkus, Carbon DS, Panache baseado no contexto Sicoob |

### O que o humano fez

1. Descreveu o problema em linguagem natural
2. Respondeu 4 perguntas de refinamento
3. Digitou `implementar`

**Tempo total:** ~45 minutos de conversa  
**Linhas de código geradas:** ~3.000 linhas Java + ~1.500 linhas TypeScript/React + ~300 linhas YAML

---

## A Equipe

Este projeto foi construído durante o **Bobathon de Modernização Java** — um workshop da área de tecnologia do Sicoob.

| Papel | Nome | Contribuição |
|-------|------|-------------|
| **Product Owner / Idealizador** | Participante Bobathon | Definição do problema, requisitos, validação |
| **Engenheiro de Software** | IBM Bob | Especificação, implementação, testes, documentação |
| **Revisão Técnica** | Time Sicoob | Validação dos padrões e contexto do negócio |

> 💡 **Este é exatamente o propósito do Bobathon:** demonstrar que com IBM Bob, um profissional de negócio com conhecimento técnico básico pode construir uma solução de software completa e funcional em horas, não semanas.

---

## Stack Tecnológico

| Camada | Tecnologia | Versão |
|--------|-----------|--------|
| **Backend** | Java + Quarkus | 21 + 3.15 |
| **Persistência** | PostgreSQL + Hibernate Panache | 16 (compose) / 18 (nativo) |
| **API** | Jakarta EE / JAX-RS + OpenAPI | 10 / 3.1 |
| **Frontend** | React + TypeScript | 18 + 5.5 |
| **Design System** | IBM Carbon Design System | 1.71 |
| **SAST** | Semgrep (opcional) | 1.x |
| **Segurança de Deps** | Lista interna de CVEs (SCA simplificado) | — |
| **Containers** | Docker / Podman + Compose | — |
| **CI/CD** | CLI Java (fat-jar) | — |

---

## Como Executar a Solução

### Pré-requisitos

| Ferramenta | Windows (PowerShell) | WSL2 / Linux |
|-----------|---------------------|-------------|
| **Java 21** | `C:\java\jdk-21_windows-x64_bin\jdk-21.0.12.1` | `sudo apt install openjdk-21-jdk` |
| **Maven 3.9+** | `C:\maven396\apache-maven-3.9.6` | `sudo apt install maven` |
| **Node.js 20+** | `winget install OpenJS.NodeJS` | `sudo apt install nodejs npm` |
| **PostgreSQL 18** | Docker Desktop | `sudo apt install postgresql-18` ⚠️ ver nota abaixo |
| **Podman** | Podman Desktop | `sudo apt install podman podman-compose` |
| **Git** | `winget install Git.Git` | pré-instalado |

> ⚠️ **WSL2 + Podman:** o Podman rootless no WSL2 isola a rede dos containers do host — o Quarkus rodando no WSL2 não consegue acessar containers via IP. **Use PostgreSQL instalado nativamente no WSL2** para desenvolvimento local. O Podman/Docker serve para subir o ambiente completo via Compose.

---

### Opção A — WSL2 com PostgreSQL nativo (validado ✅)

> Esta é a forma testada e validada durante o Bobathon.
>
> **Por que não usar Podman para o banco?** O Podman rootless no WSL2 isola completamente a rede dos containers — o Quarkus rodando no host WSL2 não consegue alcançar containers pelo IP interno nem por port binding. A solução é instalar o PostgreSQL diretamente no WSL2.

#### 1. Instale Java 21 e PostgreSQL 18 no WSL2

```bash
sudo apt update
sudo apt install -y openjdk-21-jdk postgresql-18
```

> ⚠️ **Não instale o Maven pelo `apt`** — a versão disponível é antiga demais para o Quarkus 3.15. Use o Maven 3.9.6 conforme o passo abaixo.

Configure o `JAVA_HOME` (adicione ao `~/.bashrc` para persistir):

```bash
echo 'export JAVA_HOME="/usr/lib/jvm/java-21-openjdk-amd64"' >> ~/.bashrc
echo 'export PATH="$JAVA_HOME/bin:$PATH"' >> ~/.bashrc
source ~/.bashrc

java -version    # deve mostrar 21.x
```

#### 2. Instale o Maven 3.9.6

O Maven do `apt` é muito antigo para o Quarkus 3.15 (`MavenVersionEnforcer` error). Instale manualmente:

```bash
cd ~
curl -O https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.tar.gz
tar -xzf apache-maven-3.9.6-bin.tar.gz

# Adiciona ao PATH permanentemente
echo 'export PATH="$HOME/apache-maven-3.9.6/bin:$PATH"' >> ~/.bashrc
source ~/.bashrc

mvn -version    # deve mostrar: Apache Maven 3.9.6
```

#### 3. Configure o PostgreSQL

```bash
# Inicia o serviço
sudo service postgresql start

# Cria usuário e banco
sudo -u postgres psql -c "CREATE USER sentinel_audit WITH PASSWORD 'sentinel_audit';"
sudo -u postgres psql -c "CREATE DATABASE sentinel_audit OWNER sentinel_audit;"

# Habilita autenticação por trust para conexões locais
sudo sed -i 's/127.0.0.1\/32            scram-sha-256/127.0.0.1\/32            trust/g' \
  /etc/postgresql/18/main/pg_hba.conf

sudo service postgresql restart

# Valida — deve retornar 1 sem pedir senha
psql -h 127.0.0.1 -U sentinel_audit -d sentinel_audit -c "SELECT 1"
```

> 💡 Para iniciar o PostgreSQL automaticamente ao abrir o WSL2:
> ```bash
> echo 'sudo service postgresql start > /dev/null 2>&1' >> ~/.bashrc
> ```

#### 4. Inicie o backend

```bash
cd /mnt/c/bob/bobathon2026-modernization-sicoob/sicoob-audit-tool

mvn quarkus:dev \
  -s settings-local.xml \
  -f audit-api/pom.xml
```

Sinal de sucesso:
```
audit-api 1.0.0-SNAPSHOT started in Xs. Listening on: http://localhost:8080
All 15 tests are passing ✅
```

Aguarde a mensagem `started` e acesse:
- **API:** http://localhost:8080
- **Swagger UI (somente em modo dev):** http://localhost:8080/swagger-ui

Variáveis úteis (todas opcionais em dev):

| Variável | Para quê |
|----------|----------|
| `API_KEY` | Se definida, **toda** a API exige `X-API-Key: <chave>` (ou `Authorization: Bearer`). `/api/v1/health` é livre. Sem ela a API fica aberta e um aviso é logado. |
| `DB_URL` / `DB_USER` / `DB_PASSWORD` | Banco. Com o Postgres do Compose use `DB_URL=jdbc:postgresql://localhost:5433/sentinel_audit`. |
| `DB_SCHEMA_MODE` | Padrão `update`. **Nunca** use `drop-and-create` fora de testes: apaga os dados a cada start. |
| `GIT_TOKEN`, `GIT_TOKEN_HOSTS`, `GIT_ALLOWED_HOSTS` | Token só é enviado aos hosts listados; só URLs `https://` são aceitas. |
| `CORS_ORIGINS` | Origens permitidas (padrão `http://localhost:3000`). |
| `SEMGREP_CONFIG` | Regras do Semgrep (padrão: regras dos pacotes em `audit-rules/packs`; sem pacotes, `p/default`). |

#### 4. Inicie o dashboard (novo terminal)

```bash
cd /mnt/c/bob/bobathon2026-modernization-sicoob/sicoob-audit-tool/audit-dashboard
npm install
npm run dev
```

Acesse no browser do Windows: **http://localhost:3000**. O Vite faz proxy de `/api` para `http://localhost:8080`
(use `API_URL` e `API_KEY` no ambiente do `npm run dev` se mudar a porta ou ativar a chave).

#### 5. Cadastre um sistema e dispare a primeira auditoria

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
  -d '{"systemId": 1, "branch": "main", "triggeredBy": "voce@sicoob.com.br"}'

# Acompanhar resultado
curl http://localhost:8080/api/v1/audits/1

# Ver findings críticos
curl "http://localhost:8080/api/v1/audits/1/findings?severity=CRITICAL"
```

---

### Opção B — Windows PowerShell com Docker Desktop

#### 1. Suba a infraestrutura

```powershell
cd sentinel-audit-tool
docker compose up -d postgres
docker compose ps
```

#### 2. Inicie o backend

```powershell
$env:JAVA_HOME = "C:\java\jdk-21_windows-x64_bin\jdk-21.0.12.1"
$env:PATH = "$env:JAVA_HOME\bin;C:\maven396\apache-maven-3.9.6\bin;" + $env:PATH

mvn quarkus:dev `
  -s settings-local.xml `
  -f audit-api/pom.xml
```

#### 3. Inicie o dashboard

```powershell
cd audit-dashboard
npm install
npm run dev
```

---

### Opção C — Tudo via Compose (produção local)

```bash
# WSL2 / Linux
cd sentinel-audit-tool
cp .env.example .env        # defina POSTGRES_PASSWORD (obrigatória), API_KEY e, se precisar, GIT_TOKEN

podman-compose up -d        # ou: docker compose up -d
```

Serviços disponíveis:
- **Dashboard:** http://localhost:3000
- **API:** http://localhost:8080
- **Swagger:** desativado no perfil de produção

O Compose expõe tudo apenas em `127.0.0.1`. O dashboard injeta a `API_KEY` no proxy (nginx), então a chave não vai para o navegador.

---

### Rodar os Testes

```bash
# WSL2 / Linux
cd /mnt/c/bob/bobathon2026-modernization-sicoob/sicoob-audit-tool

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
| `--sarif relatorio.sarif` | Grava SARIF 2.1.0 (GitHub Code Scanning, Azure DevOps, GitLab) |
| `--junit relatorio.xml` | Grava JUnit XML (aparece como teste no pipeline) |

---

## Estrutura do Projeto

```
sentinel-audit-tool/
├── pom.xml                         ← Parent Maven multi-módulo
├── settings-local.xml              ← Maven settings (fora da rede Sicoob)
├── docker-compose.yml              ← Ambiente completo local
├── .env.example                    ← Template de variáveis de ambiente
├── docs/schema-referencia.sql      ← Só referência (o schema é gerado pelo Hibernate)
│
├── audit-api/                      ← Backend Quarkus 3.15 + Java 21
│   ├── Dockerfile
│   └── src/main/java/com/sentinel/audit/
│       ├── model/                  ← Entidades JPA + enums
│       ├── repository/             ← Repositórios Panache
│       ├── dto/                    ← DTOs de request/response
│       ├── service/                ← Lógica de negócio
│       ├── analyzer/               ← Motores de análise
│       ├── insights/               ← Áreas, mapa do projeto, dívida técnica (P0–P3), quick wins e roadmap
│       └── resource/               ← Endpoints REST
│
├── audit-rules/
│   ├── packs/                      ← Pacotes de regras (core-security, core-iac, core-spring, core-quality, core-frontend, market-tools, ts-clean-code, core-architecture) e profiles/
│   ├── optional/                   ← Regras que duplicam os analisadores Java (desativadas)
│   ├── lint/eslint/                ← Configuração do ESLint usada pelo analisador
│   └── bin/sentinel-pack           ← Ferramenta de linha de comando para autores de pacotes
│
├── deploy/helm/sentinel/           ← Chart Helm (API, dashboard, PostgreSQL opcional) — não validado com `helm lint`
│
├── audit-dashboard/                ← Frontend React 18 + IBM Carbon DS
│   ├── Dockerfile + nginx.conf.template
│   └── src/
│       ├── pages/                  ← Overview, SystemDetail, Findings, Reports
│       ├── components/             ← AppShell, RiskBadge, ScoreGauge, FindingsTable
│       ├── hooks/                  ← TanStack Query (useSystems, useFindings...)
│       ├── store/                  ← Zustand (filtros, sistema selecionado)
│       ├── api/                    ← Client tipado para o backend
│       └── types/                  ← Interfaces TypeScript
│
└── audit-cli/                      ← CLI Java para integração CI/CD
    └── src/main/java/.../AuditCli.java
```

---

## Migrando de uma instalação antiga (quando o nome era "Sicoob Audit Tool")

O projeto foi renomeado por completo para **Sentinel**: pacotes Java (`com.sentinel.audit`), ids de regra
(`sentinel-*`), propriedades (`sentinel.audit.*`), banco (`sentinel_audit`) e jars (`sentinel-audit-*`).
Quem já tinha a versão antiga rodando precisa:

1. **Banco:** renomear (ou recriar) o banco e o usuário, ou manter os antigos apontando `DB_URL`, `DB_USER` e `DB_PASSWORD` para eles:
   ```sql
   ALTER DATABASE sicoob_audit RENAME TO sentinel_audit;
   ALTER ROLE sicoob_audit RENAME TO sentinel_audit;   -- renomear o usuário limpa a senha: defina-a de novo com ALTER ROLE ... PASSWORD
   ```
2. **Histórico de findings:** os ids de regra mudaram, então o *fingerprint* de cada finding também. Na primeira auditoria depois da migração,
   tudo aparece como "novo" e os findings antigos como "resolvidos"; supressões (falsos positivos) cadastradas antes **não valem mais** e precisam ser refeitas.
3. **Variáveis e arquivos próprios:** troque qualquer `sicoob.audit.*` por `sentinel.audit.*` em configurações, e `sicoob-*` por `sentinel-*`
   nas suas regras Semgrep, supressões e scripts de CI (o jar do CLI agora é `sentinel-audit-cli.jar`).

## Qualidade de código, padrões e lint (Java e TypeScript)

Além de segurança e dependências, a auditoria avalia **clean code, boas práticas, resiliência de integrações e lint**,
sem exigir nada instalado (as três primeiras camadas) e usando PMD/ESLint quando existirem.

| Camada | O que verifica | Precisa instalar? |
|---|---|---|
| **CleanCode** (interno) | função longa, muitos parâmetros, complexidade ciclomática, aninhamento profundo | não |
| **Regras Semgrep** `audit-rules/packs/core-quality/rules/` | 14 regras Java (catch vazio, `return null` em coleção, injeção por campo, estado estático mutável, `==` em String, `@Transactional` privado, log concatenado, thread manual, service locator, singleton manual...), 11 regras TypeScript/React (`any`, `@ts-ignore`, `var`, `==`, `useEffect` async, `key` por índice, token em `localStorage`...) e 9 de **integração** (HTTP/JDBC/axios/fetch sem timeout, pool ilimitado, HTTP sem TLS) | Semgrep |
| **PMD** (lint Java) | boas práticas, propensão a erro, design e segurança do PMD 7 (ruleset embutido; troque com `PMD_RULESET`) | `pmd` no PATH |
| **ESLint** (lint TypeScript) | `typescript-eslint` recommended + `eqeqeq`, `no-var`, `no-console`, `prefer-const`, `no-eval` | `eslint` no PATH **e** `/opt/eslint/eslint.config.mjs` |

Como funciona:

- **Limites do CleanCode** (`CLEANCODE_*`): 50 linhas por função (100 para componente JSX), 5 parâmetros, complexidade 10, aninhamento 4. As métricas são **textuais** (um scanner de chaves que ignora comentários e strings), não uma árvore sintática: é rápido e não depende de nada, mas pode errar em código muito fora do comum. Testes, `target`, `node_modules`, `dist` e arquivos gerados são ignorados.
- **Sem contagem em dobro**: quando Semgrep e PMD/ESLint apontam o mesmo ponto (catch vazio, `var`, `==`, `any`, `console.log`), fica um finding só, com a maior severidade e a nota "Também detectado por...". Só se juntam equivalências conhecidas (`FindingDeduplicator`); regra desconhecida nunca é escondida.
- **O ESLint nunca carrega a configuração do repositório auditado** (`--no-config-lookup`): um `eslint.config.js` é código e seria executado no servidor. Vale a configuração de `audit-rules/lint/eslint/`.
- **Imagem Docker**: PMD vem ligado (`WITH_PMD=true`); ESLint vem desligado porque exige Node 18+ (`--build-arg WITH_ESLINT=true`).
- Cada regra nova tem orientação de correção (antes/depois) no catálogo, exibida no dashboard; um teste impede regra sem orientação.
- A taxa de falso positivo por regra (`GET /api/v1/rules/quality`) mostra quais regras estão incomodando e devem ser ajustadas.

```bash
# testar as regras Semgrep de qualidade
semgrep --test --config audit-rules/packs/core-quality/rules audit-rules/packs/core-quality/tests
```

## Pacotes de regras, perfis e arquitetura (plug and play)

As regras não ficam mais no código: são **pacotes** (pastas com um `pack.yml`) que você adiciona, atualiza ou remove sem recompilar.
Vêm prontos em `audit-rules/packs/`: `core-security` (30 regras Semgrep), `core-spring` (14, Spring Boot e JPA), `core-iac` (Dockerfile, Compose e Kubernetes),
`core-quality` (35, clean code/integrações), `core-frontend` (24: Angular, React, HTML, CSS/SCSS, acessibilidade e performance), `market-tools` (njsscan e Stylelint, veja abaixo), `ts-clean-code` (padrão TypeScript, opt-in) e `core-architecture` (exemplo de política de camadas e componentes, **desligado por padrão**).

### Estrutura de um pacote

```
meu-pacote/
├── pack.yml              # id, name, version, description, author, languages, enabled-by-default, min-sentinel
├── rules/*.yml           # regras Semgrep (metadata.status: draft|experimental|stable|deprecated)
├── tests/                # casos "// ruleid:" / "// ok:" — mesmo nome do arquivo de regra
├── remediation/*.yml     # orientação de correção por id de regra (key, summary, before, after, refs)
├── dedup.yml             # regras que descrevem o mesmo problema (agrupa em um achado)
├── tools/*.yml           # fontes SARIF de outras ferramentas (veja abaixo)
└── architecture.yml      # camadas, convenções, arquivos obrigatórios, política de bibliotecas
```

Tudo é validado ao carregar (chaves desconhecidas, severidade inválida, regex quebrada, camada inexistente, ids repetidos...).
Pacote inválido é **rejeitado com o motivo** e os demais continuam; veja em `GET /api/v1/packs`.

### De onde vêm os pacotes

- `audit-rules/packs` (embutido), mais `PACKS_DIRS` (pastas extras, separadas por vírgula);
- um **repositório Git central**: `PACKS_GIT_URL`, `PACKS_GIT_REF` (padrão `main`), `PACKS_GIT_PATH`. É atualizado a cada `refresh-interval` e em `POST /api/v1/packs/reload`; se a rede falhar, segue com o cache.
- Mesmo `id` em dois lugares: vale a **maior versão**. `PACKS_DISABLED=id1,id2` desliga pacotes.

### Ferramenta para autores (`sentinel-pack`)

```bash
sentinel-pack new meu-time --dir ./meus-pacotes      # cria pacote de exemplo (já com teste)
sentinel-pack validate ./meus-pacotes                # mesmas checagens do servidor (--strict: regra sem teste é erro)
sentinel-pack test ./meus-pacotes/meu-time           # semgrep --test
sentinel-pack keygen minha-chave                     # Ed25519: minha-chave.key (segredo) e .pub
sentinel-pack pack ./meus-pacotes/meu-time --key minha-chave.key
sentinel-pack verify ./meus-pacotes/meu-time --public-key minha-chave.pub
```

Na imagem Docker o comando já existe. Fora dela: `SENTINEL_HOME=<pasta quarkus-app> audit-rules/bin/sentinel-pack ...`.

**Laboratório de regras:** `POST /api/v1/rules/playground` com `{"rule": "<yaml>", "code": "<trecho>", "extension": "java"}` valida a regra e roda o Semgrep
de verdade sobre o trecho (máx. 20 mil/100 mil caracteres, 30 s, 2 execuções simultâneas). Desligue com `PLAYGROUND_ENABLED=false`.

### Perfis (por sistema)

Arquivos em `profiles/*.yml` na raiz de pacotes (ex.: `audit-rules/packs/profiles/`). Cada sistema escolhe um perfil na tela do sistema (ou `PUT /api/v1/systems/{id}/profile`).

```yaml
id: legado
extends: base-comum            # opcional; sem 'packs', herda do pai (ou, sem pai, todos os habilitados por padrão)
packs: [core-security, core-quality]
disable-packs: [core-spring]
rules:
  "sentinel-java-*": {shadow: true}      # aparece no relatório, mas não pesa no score nem derruba o pipeline
  sentinel-god-object: {severity: low}
  sentinel-sql-injection: {severity: critical}
  sentinel-crypto-hash-fraco: {disabled: true}
thresholds:                  # clean code: max-function-lines, max-component-lines, max-parameters, max-complexity, max-nesting, max-findings
  max-function-lines: 120
```

**Modo sombra:** regra com `metadata.status: draft|experimental` já nasce em sombra; `shadow: false` no perfil a promove. Serve para
introduzir uma regra nova sem derrubar a nota de ninguém: observe o volume, ajuste, promova. Exemplos prontos: `profiles/legado.yml`, `profiles/arquitetura-em-camadas.yml`.

### Arquitetura e componentes declarativos (`architecture.yml`)

```yaml
layers:   [{name: resource, paths: ["**/resource/**"]}, {name: repository, paths: ["**/repository/**"], packages: ["*.repository.*"]}]
forbid:   [{from: resource, to: repository, severity: high, message: "Passe pelo serviço"}]   # allow + strict: true = só o que está em allow
conventions:
  - {id: endpoint-sem-auth, files: ["**/resource/**/*.java"], exclude: ["**/*Test.java"], require: "@RolesAllowed", severity: medium}
required-files: [{path: README.md, severity: low}]
components:
  forbidden:   [{dep: "log4j:log4j", reason: "descontinuada"}]
  min-version: [{dep: "org.apache.logging.log4j:log4j-core", version: ">=2.17.1", severity: critical}]
  required:    [{dep: "org.slf4j:*"}]
  approved-only: [{dep: "com.acme.*"}]       # dependências diretas fora da lista viram achado
```

Imports Java e TypeScript (relativos e por pacote) são resolvidos para camadas. Ids gerados: `sentinel-arch-layer-<de>-<para>`, `sentinel-arch-conv-<id>`,
`sentinel-arch-required-file`, `sentinel-comp-forbidden|min-version|required|not-approved`. Versão não resolvida (`${prop}`, intervalo) **não** reprova.

### Outras ferramentas via SARIF (`tools/*.yml`)

```yaml
- id: ci-reports                      # lê SARIF que o CI do time já gera (sempre permitido)
  input: "reports/*.sarif"
- id: spotbugs                        # executa uma ferramenta que grava o SARIF em {output}
  command: [spotbugs-sarif, --project, "{project}", --output, "{output}"]
  severity-map: {error: high, warning: medium, note: low}
- id: hadolint                        # ferramenta que imprime o SARIF na saída padrão
  stdout: true
  applies-to: ["**/Dockerfile", "**/*.dockerfile"]   # só roda se o projeto tiver ao menos um arquivo assim
  command: [hadolint, -f, sarif, "{files}"]          # {files} = os arquivos que casaram (relativos, até 500)
```

Comandos nunca passam por shell, rodam com o projeto como diretório de trabalho e só executam se o pacote for liberado
(`PACKS_ALLOW_TOOLS=true`, para todos) ou **confiável** (`PACKS_TRUSTED_TOOL_PACKS`, abaixo). Ferramenta ausente é ignorada sem erro.

### Escopo de tecnologias e analisadores

O Sentinel analisa **Angular, TypeScript, JavaScript, React, HTML, CSS, SCSS, Java, Spring Boot, Docker e Kubernetes**. Analisadores de Python, Go e Terraform foram removidos.

| Tecnologia | Analisadores |
|---|---|
| Java | PMD, **Checkstyle** (bugs prováveis, tamanho e complexidade); regras Semgrep de segurança (injeção, criptografia, OWASP API, XSS, **XXE, SpEL, redirect aberto, desserialização do Jackson, Zip Slip, dado sensível no log, senha fixa, cookie sem Secure**), clean code e integrações |
| Spring Boot / JPA | pacote `core-spring` (14 regras): Actuator exposto, console H2, senha em properties, `ddl-auto`, `open-in-view`, `@RequestMapping` sem método, `@RequestBody` sem `@Valid`, `permitAll()` geral, `frameOptions` desligado, `FetchType.EAGER`, `@Data` em entidade; `@CrossOrigin` aberto |
| TypeScript / JavaScript | ESLint com `typescript-eslint`, `security`, `no-unsanitized`, `sonarjs`, **`promise` e `regexp` (ReDoS)**; regras Semgrep `sentinel-ts-*`; njsscan |
| Angular | `angular-eslint` no código e nos **templates** (acessibilidade incluída); regras `sentinel-ng-*` (bypassSecurityTrust, nativeElement, `::ng-deep`, `*ngFor` sem `trackBy`, método no template) |
| React | `react`, `react-hooks` e `jsx-a11y`; regras `sentinel-react-*` |
| HTML | **`@html-eslint`** (lang, title, alt, label, ids duplicados, viewport, ARIA, tabindex...) e regras `sentinel-html-*` / `sentinel-fe-*` |
| CSS / SCSS | Stylelint (`standard-scss`, **acessibilidade com `stylelint-a11y`**, `!important`, `transition: all`, aninhamento) e regras `sentinel-css-*` / `sentinel-scss-*` |
| Docker / Compose | `core-iac` (Dockerfile: `latest`, root, `ADD` remoto, `chmod 777`, segredo em `ARG`/`ENV`, SSH exposto, apt; **Compose: `privileged`, socket do Docker, `network_mode: host`, segredo no ambiente**), Hadolint, Checkov, Trivy (misconfig) |
| Kubernetes | regras `sentinel-k8s-*` (`hostNetwork`, `hostPath`, root, escalada de privilégio, capabilities, `:latest`), kube-linter, **kube-score**, Checkov, Trivy |
| Qualquer linguagem | Gitleaks e histórico (segredos), OSV/Trivy (dependências vulneráveis), **jscpd (código duplicado)** |

### Ferramentas externas (`market-tools`)

Pacote embutido que liga por SARIF: **njsscan** (segurança de JS/Node), **Stylelint** (CSS/SCSS), **Checkstyle** (Java), **jscpd** (código duplicado em Java, TS/JS, CSS/SCSS e HTML; limites em `JSCPD_MIN_LINES`/`JSCPD_MIN_TOKENS`), **Hadolint** (Dockerfile), **kube-linter** e **kube-score** (manifestos Kubernetes em `k8s/`, `kubernetes/`, `manifests/`) e **Checkov** (Dockerfile, Kubernetes e GitHub Actions). O ESLint tem analisador próprio. Cada ferramenta só roda se estiver instalada **e** se o projeto tiver arquivos do tipo que ela entende.

- Na imagem Docker: `WITH_ESLINT=true` (padrão) instala Node 20, ESLint e Stylelint; `WITH_MARKET_TOOLS=true` (padrão) instala njsscan, Checkov, Hadolint, kube-linter, kube-score e o Checkstyle (precisa da JVM que a imagem já tem); o jscpd vem junto com o ESLint. Fora do Docker, basta ter os executáveis no `PATH` e, para o Stylelint, a pasta `audit-rules/lint/stylelint` instalada em `/opt/stylelint`.
- Os findings entram com o nome da ferramenta e o id `<ferramenta>-<regra>` (ex.: `stylelint-declaration-no-important`, `eslint-react-hooks/rules-of-hooks`). Achados repetidos entre ferramentas (ESLint e Semgrep, Hadolint/Checkov e as regras de Dockerfile) são juntados (`dedup.yml`).
- O Stylelint inclui regras de estilo: para não pesar no score, use um perfil `rules: { "stylelint-*": { shadow: true } }`.
- **Confiança:** `PACKS_TRUSTED_TOOL_PACKS=market-tools` (padrão) libera as ferramentas desse pacote sem ligar `PACKS_ALLOW_TOOLS`.
  Vale **só** para o pacote que vem na pasta do produto; um pacote externo com o mesmo id, mesmo mais novo, não herda a confiança.
  Para desligar tudo: `PACKS_TRUSTED_TOOL_PACKS=` (vazio) ou `PACKS_DISABLED=market-tools`.
- Não incluídos de propósito: Grype e OSV-Scanner (repetem o OSV interno e o Trivy), Lighthouse (exige navegador e site publicado) e SpotBugs/FindSecBugs (precisam do código Java já compilado).

### Padrão de código TypeScript / Angular (`ts-clean-code`)

Pacote opt-in (desligado por padrão) que transforma o padrão de clean code da casa em verificações automáticas. Veja o padrão completo, o que cada regra checa e os conflitos encontrados no documento original em [`docs/padrao-typescript/README.md`](docs/padrao-typescript/README.md).

- **7 regras Semgrep** (`sentinel-tsp-*`): membro privado com `_`, público sem `_`, modificador de acesso explícito, enum terminado em `Enum`, interface com `I`/`Interface`, hooks de ciclo de vida só com chamadas a métodos privados e sem abreviações (`num`, `cod`, `desc`...).
- **Perfil `padrao-typescript-angular`**: liga o pacote e fixa os limites do padrão (método 50 linhas, complexidade 10, aninhamento 3, arquivo 500 linhas).
- **Lint de estrutura e nomes (ESLint)**: use `ESLINT_CONFIG=/opt/eslint/eslint.padrao-ts.config.mjs`. Cobre tipo de retorno, ordem de membros, nomes, linhas em branco, `any`, `console` e `==`.
- **Nos seus projetos Angular**: copie `docs/padrao-typescript/eslintrc.json` para `.eslintrc.json` (mesmas regras, no formato do ESLint 8 + Angular + Prettier).

### Auditoria de PR (só o que mudou)

`POST /api/v1/audits` com `"baseRef": "main"` (ou `--base-ref main` no CLI) mantém só achados em arquivos alterados em relação a esse branch.
O "novo vs. anterior" compara com a última auditoria **completa** do branch base; auditorias de PR nunca viram base nem entram na tendência.
Se não for possível calcular o diff, nada é escondido (todos os achados são mantidos). O CLI grava um resumo para comentar no PR: `--pr-comment pr.md` (ele **não** posta; o pipeline posta).

```bash
java -jar sentinel-audit-cli.jar --system-id 1 --branch feature/x --base-ref main --fail-on HIGH --pr-comment pr.md
```

### Segurança dos pacotes

- Um pacote é **código de confiança**: só instale de fontes suas. O YAML é lido sem instanciar classes, com limites de tamanho/aliases; links simbólicos são recusados.
- `command` em `tools/` é **desligado** por padrão (`PACKS_ALLOW_TOOLS=false`); quando ligado, roda sem shell, com tempo limite.
- **Integridade:** `pack.lock` (SHA-256 de cada arquivo) detecta alteração; qualquer divergência rejeita o pacote. Com `PACKS_PUBLIC_KEY` + `pack.sig` (Ed25519) a origem é conferida;
  `PACKS_REQUIRE_SIGNATURE=true` só carrega pacotes assinados com essa chave — **inclusive os `core-*`**, que você deve assinar com a sua chave (`sentinel-pack pack --key`).
- URLs de Git: só https/ssh; nomes que começam com `-` são recusados.

---

## Auditoria completa (áreas, dívida técnica e plano de ação)

Além da lista de findings, cada auditoria concluída gera uma visão no formato de uma auditoria profissional
(`GET /api/v1/audits/{id}/insights`, painel "Auditoria completa" na tela do sistema e seção no relatório HTML):

| Parte | O que traz |
|---|---|
| **Parecer e resumo executivo** | REPROVADO (há P0), ATENÇÃO (P1), APROVADO COM RESSALVAS (P2) ou APROVADO, com as áreas mais afetadas |
| **Por área** | 11 áreas (arquitetura, qualidade, segurança, testes, performance, frontend/acessibilidade, CI/CD e infra, documentação, dependências, observabilidade, manutenibilidade): estado, problemas, riscos, recomendações |
| **Dívida técnica** | Uma linha por problema: impacto, esforço (S/M/L), risco de regressão, ocorrências e prioridade **P0 Bloqueador** (crítico), **P1 Alta**, **P2 Importante**, **P3 Futura** |
| **Quick wins, Top 10 problemas, Top 10 melhorias** | Ordenados por gravidade × ocorrências ÷ esforço |
| **Plano de ação** | 8 fases: correções críticas, qualidade estrutural, performance, segurança, testes, UX/UI, DevOps, melhorias futuras |
| **Não foi possível verificar** | Tudo que a análise estática não alcança (cobertura real, Core Web Vitals, contraste, produção) e analisadores que não executaram |
| **Mapa do projeto** | Linguagens, frameworks, build, CI, Dockerfiles, testes, documentação — só o que foi observado nos arquivos |

Regras que sustentam isso:

- **Só o que foi comprovado.** O analisador de higiene (`sentinel-proj-*`) olha evidências de arquivos: sem README, sem testes, sem CI,
  `package.json` sem lockfile, `.env` ou chave privada versionados, `node_modules`/`target` commitados, Dockerfile sem `.dockerignore`.
  Ajuste ou desligue por perfil (`rules:`) e use os limites `min-source-files-for-tests` e `readme-min-lines`.
- **Área sem analisador não vira "ok".** Se nenhum analisador que cobre a área executou (ex.: Trivy e OWASP ausentes → dependências),
  a área aparece como *Não verificado* e o motivo entra na lista.
- **Esforço e risco vêm da remediação.** Em `remediation/*.yml` use `effort: S|M|L` e `regression-risk: LOW|MEDIUM|HIGH`.
  Sem eles, o esforço é M e aparece marcado como *estimado*.
- **Pacote novo, regra com teste.** `sentinel-pack validate --strict <pacote>` trata regra sem caso `// ruleid:` como erro (os pacotes embutidos passam).
- O mapa do projeto e a situação dos analisadores ficam na coluna `audit_run.context_json`; auditorias antigas, sem ela, geram a visão só com os findings.

## Auditoria automática, webhooks e notificações

**Periódica:** na tela do sistema, em *Auditoria automática*, defina o intervalo em horas e a branch. O agendador (`SCHEDULER_ENABLED=true`) dispara quando vence e nunca empilha uma auditoria sobre outra em andamento.

**A cada push:** marque *Auditar a cada push* no sistema e cadastre o webhook no repositório:

| Plataforma | URL | Segredo |
|-----------|-----|---------|
| GitHub | `https://<sua-api>/api/v1/webhooks/github` (JSON, evento *push*) | campo *Secret* = `WEBHOOK_SECRET` (assinatura HMAC-SHA256) |
| GitLab | `https://<sua-api>/api/v1/webhooks/gitlab` | *Secret token* = `WEBHOOK_SECRET` |

Sem `WEBHOOK_SECRET`, os webhooks ficam desativados. Só a branch configurada no sistema dispara; tags e branches apagadas são ignoradas.

**Notificações:** defina `NOTIFY_WEBHOOK_URL` (Incoming Webhook do Teams ou Slack) e `NOTIFY_FORMAT` (`teams`, `slack` ou `generic`). O aviso só sai quando há motivo: auditoria falhou, score caiu `NOTIFY_MIN_SCORE_DROP` pontos ou mais, ou surgiu finding novo de severidade `NOTIFY_MIN_NEW_SEVERITY`+. Falha no envio nunca altera o resultado da auditoria.

## Login corporativo (OIDC/Keycloak) — opcional

```bash
mvn package -Poidc -f audit-api/pom.xml          # inclui quarkus-oidc
export QUARKUS_PROFILE=oidc OIDC_AUTH_SERVER_URL=https://keycloak.exemplo.com/realms/sentinel OIDC_CLIENT_ID=sentinel-audit
# deixe API_KEY vazia: a autenticação passa a ser o token Bearer do provedor
```

`/api/v1/health` e os webhooks seguem públicos. **Limitação:** isto autentica; não separa dados por squad. Isso exigiria filtrar sistemas pela claim do token e ainda não foi feito.

---

## Endpoints da API

| Método | Endpoint | Descrição |
|--------|---------|-----------|
| `GET` | `/api/v1/systems` | Lista todos os sistemas |
| `POST` | `/api/v1/systems` | Cadastra novo sistema |
| `GET` | `/api/v1/systems/{id}` | Detalhe do sistema |
| `DELETE` | `/api/v1/systems/{id}` | Remove o sistema (e suas supressões) |
| `PUT` | `/api/v1/systems/{id}/schedule` | Auditoria periódica / por push |
| `GET` | `/api/v1/systems/{id}/audits` | Histórico de auditorias do sistema |
| `GET` | `/api/v1/systems/{id}/trend` | Evolução do score |
| `GET` | `/api/v1/systems/{id}/suppressions` | Findings suprimidos (`DELETE …/{suppressionId}` reabre) |
| `POST` | `/api/v1/audits` | Dispara nova auditoria (assíncrono; PENDING → RUNNING → COMPLETED/FAILED, com `errorMessage` em caso de falha) |
| `GET` | `/api/v1/audits/{id}` | Status e resultado da auditoria |
| `GET` | `/api/v1/audits/{id}/findings` | Findings (filtros: `severity`, `category`, `status`, `onlyNew`, `includeSuppressed`) |
| `GET` | `/api/v1/audits/{id}/diff` | Novos / resolvidos / persistentes vs. auditoria anterior |
| `GET` | `/api/v1/audits/{id}/report` | Relatório HTML (imprimível como PDF) |
| `GET` | `/api/v1/audits/{id}/sbom` | SBOM CycloneDX 1.5 (JSON) |
| `GET` | `/api/v1/audits/{id}/insights` | Auditoria completa: parecer, áreas, dívida técnica P0–P3, quick wins, roadmap em 8 fases e o que não foi possível verificar |
| `GET` | `/api/v1/packs` · `POST /reload` | Pacotes e perfis carregados (rejeitados com motivo, avisos, integridade) · recarrega |
| `PUT` | `/api/v1/systems/{id}/profile` | Perfil de regras do sistema |
| `POST` | `/api/v1/rules/playground` | Valida e executa uma regra Semgrep sobre um trecho de código |
| `GET` | `/api/v1/rules/quality` | Taxa de falso positivo por regra (a partir das supressões); marca as que merecem revisão |
| `POST` | `/api/v1/findings/{id}/suppress` | Suprime com motivo, justificativa e validade |
| `POST` | `/api/v1/webhooks/github` · `/gitlab` | Disparo por push (autenticado por segredo próprio) |
| `GET` | `/api/v1/health` | Health check |

Documentação interativa (somente modo dev): **http://localhost:8080/swagger-ui**

---

**Desenvolvido com ❤️ usando IBM Bob**  
**Bobathon de Modernização Java — Sicoob 2026**
