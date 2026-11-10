# Sentinel Audit Tool

Plataforma de auditoria técnica contínua de código. Clona o repositório (ou lê uma pasta local), roda vários analisadores, calcula uma nota de risco por sistema e mostra tudo em um painel, em relatórios (HTML, PDF, CSV), em SARIF e em um CLI que serve de gate no pipeline.

**Documentação completa: [`docs/`](docs/README.md)** · [Instalação](docs/guia/instalacao.md) · [Arquitetura](docs/arquitetura/ARQUITETURA.md) · [Impacto e o que foi medido](docs/IMPACTO.md) · [Histórico](docs/evolucao/LOG.md)

---

## O que ele faz

```
Repositório Git / pasta local
        │
        ▼
 Ingestão → Analisadores → Nota de risco → Painel · Relatórios · SARIF · CLI (gate)
```

| Área | O que é analisado |
|---|---|
| **Segurança** | SAST com Opengrep (regras próprias em pacotes), segredos (regex, Gitleaks, histórico do Git), dependências (Trivy ou OSV), IaC, taint entre arquivos em Java com Joern (opcional) |
| **Qualidade** | Clean code (função longa, complexidade, aninhamento), PMD, ESLint, Stylelint, duplicação (jscpd) |
| **Arquitetura** | Camadas, ciclos de dependência, God Object, regras declarativas por projeto (`architecture.yml`) |
| **Dados e infra** | SQL (SQLFluff, Squawk), Dockerfile (Hadolint), Kubernetes (kube-linter, kube-score), GitHub Actions (Checkov); Terraform fica fora do escopo |
| **Higiene do projeto** | README, testes, CI, lockfile, `.env` versionado |

Escopo de tecnologias: Java, Spring Boot, Quarkus, Angular, React, Vue, TypeScript, JavaScript, HTML, CSS/SCSS, Docker, Kubernetes e SQL.

**Nota:** seis dimensões (segurança, qualidade, arquitetura, dependências, observabilidade, performance), com ajuste por tamanho do código. Cada achado tem identidade estável entre auditorias (novo, resolvido, persistente), orientação de correção (antes/depois, esforço, risco) e pode ser suprimido com motivo, justificativa e validade.

**Também faz:** auditoria completa (parecer, dívida técnica P0–P3, plano de ação), auditoria só do que mudou no PR, webhooks GitHub/GitLab, avisos em Teams/Slack, SBOM CycloneDX, perfis por sistema, servidor MCP para o Claude consultar o Sentinel (fora da nota e com aprovação humana para qualquer ação).

## Pacotes de regras

16 pacotes declarativos em `audit-rules/packs` (298 regras próprias), 12 ferramentas de mercado via SARIF (mais o Joern, opcional) e 9 perfis. Pacotes novos entram sem compilar nada. Veja [pacotes e perfis](docs/referencia/pacotes-de-regras.md). O pacote [`ts-clean-code-labs42`](docs/padrao-typescript/clean-code-labs42.md) traz 21 regras do guia clean-code-typescript (opt-in).

## Começando

```bash
git clone https://github.com/joaopaulosistemas/sentinel.git && cd sentinel
cp .env.example .env            # defina POSTGRES_PASSWORD; API_KEY recomendada
docker compose up -d --build
```

Painel em http://localhost:3000 e API em http://localhost:8080. Modo desenvolvimento, CLI para CI/CD, build args da imagem (Joern, Semgrep etc.) e migração: [guia de instalação](docs/guia/instalacao.md).

## Stack

Java 21 + Quarkus 3.33 · PostgreSQL 16 · React 18 + TypeScript + Vite + IBM Carbon · Opengrep 1.30.0 (Semgrep como alternativa) · Docker/Compose e chart Helm · CLI Java.

## Estado e limites

- **Medido:** OWASP Benchmark (Java), calibração em repositórios públicos e revisão por amostragem. Os números e o que eles **não** provam estão em [IMPACTO.md](docs/IMPACTO.md).
- **Não medido:** taxa de falso positivo em código real da sua organização; recomenda-se um piloto com 2 ou 3 sistemas.
- **Login OIDC** autentica, mas não separa sistemas por squad.
- **Placar de maturidade:** 2,0 em 3 ([PLACAR](docs/evolucao/PLACAR.md)); o Sentinel não se declara nível profissional.
- **Testes Java e imagem Docker** rodam no GitHub Actions ([ci.yml](.github/workflows/ci.yml)).

## Origem

A primeira versão nasceu no Bobathon de Modernização Java; a história está em [docs/historia.md](docs/historia.md).
