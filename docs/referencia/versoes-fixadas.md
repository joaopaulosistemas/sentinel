# Versões fixadas e decisões em aberto

As ferramentas externas rodam em versões **fixadas** (com SHA-256 quando são binários) em `audit-api/Dockerfile` e `audit-rules/lint/eslint/package.json`. Fixar dá resultado repetível e impede troca silenciosa de binário; o custo é que **alguém precisa atualizar**.

## Política de atualização

- **Quando revisar:** a cada trimestre, e na hora em que uma ferramenta sair de suporte ou publicar correção de segurança.
- **Como atualizar:** subir a versão e o SHA-256 no `Dockerfile` (ou no `package.json` e `package-lock.json`), rodar a calibração e o OWASP Benchmark, e registrar no `docs/evolucao/LOG.md` o que mudou nos achados.
- **Quem decide:** quem abre o PR de atualização; mudança de licença ou de mantenedor vira decisão em aberto abaixo, com dono.

## Estado em 2026-11-10

| Ferramenta | Versão fixada | Situação | Ação |
|---|---|---|---|
| ESLint | 9.39.5 | A linha 9.x chegou ao fim de vida em 2026-08-06 (a 10.x é a atual, desde 2026-02-06; fonte: eslint.org/version-support) | **Prioridade.** Migrar para a 10.x: exige Node 20.19+ (a imagem usa Node 20), os plugins (`angular-eslint`, `typescript-eslint`, `sonarjs`, `react-hooks` etc.) precisam declarar suporte, e a busca de `eslint.config.*` passa a partir de cada arquivo |
| Gitleaks | 8.21.2 | A última 8.x é a 8.30.1 (2026-03-21); o autor lançou o Betterleaks como sucessor e a manutenção do Gitleaks tende a só correções | **Prioridade.** Subir para 8.30.1 e decidir se o Betterleaks (MIT, substituto direto) entra como alternativa |
| Hadolint | 2.12.0 | Licença GPL-3.0 | Decisão em aberto (abaixo) |
| Opengrep | 1.30.0 | LGPL-2.1 | Sem pendência |
| Checkov | 3.4.3 | Roda só em Dockerfile, Kubernetes e GitHub Actions; Terraform fica fora (ver LOG) | Sem pendência |

## Decisões em aberto

1. **Hadolint (GPL-3.0) dentro da imagem.** O Sentinel só executa o binário como processo separado e não o incorpora ao código, mas a imagem Docker publicada **distribui** esse binário. Quem distribuir a imagem precisa cumprir as obrigações da GPL para ele (aviso de licença e oferta do código-fonte) ou trocar a ferramenta. O parecer jurídico é de quem responde pela distribuição; este documento não o substitui. Dono: a definir. Alternativa técnica: manter o Hadolint como opcional (`WITH_MARKET_TOOLS=false`) na imagem pública.
2. **Gitleaks: continuar, migrar ou oferecer os dois.** Depende do ponto 2 da tabela acima. Dono: a definir.

## O que a narrativa pode e não pode afirmar

- Dependências: **Trivy ou OSV.dev** (não OWASP Dependency-Check, que o Sentinel não usa).
- IaC: o Checkov cobre Dockerfile, Kubernetes e GitHub Actions; **Terraform está fora do escopo**.
- Os números de regras, pacotes, perfis e ferramentas são conferidos no CI por `scripts/conferir-numeros.py`; quem muda um pacote atualiza o `README.md` e o `docs/IMPACTO.md` na mesma hora.
- Precisão e cobertura só valem para o corpus medido (veja `docs/IMPACTO.md`); fora dele, não há número.
