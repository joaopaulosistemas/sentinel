# Versões fixadas e decisões em aberto

As ferramentas externas rodam em versões **fixadas** (com SHA-256 quando são binários) em `audit-api/Dockerfile` e `audit-rules/lint/eslint/package.json`. Fixar dá resultado repetível e impede troca silenciosa de binário; o custo é que **alguém precisa atualizar**.

## Política de atualização

- **Quando revisar:** a cada trimestre, e na hora em que uma ferramenta sair de suporte ou publicar correção de segurança.
- **Como atualizar:** subir a versão e o SHA-256 no `Dockerfile` (ou no `package.json` e `package-lock.json`), rodar a calibração e o OWASP Benchmark, e registrar no `docs/evolucao/LOG.md` o que mudou nos achados.
- **Quem decide:** quem abre o PR de atualização; mudança de licença ou de mantenedor vira decisão em aberto abaixo, com dono.

## Estado em 2026-11-10 (depois da atualização)

| Ferramenta | Versão fixada | Situação | Ação |
|---|---|---|---|
| ESLint | 10.14.0 | Linha atual (a 9.x saiu de suporte em 2026-08-06). `eslint-plugin-react` e `eslint-plugin-jsx-a11y` não declaram suporte ao 10 e rodam com `fixupPluginRules` (`@eslint/compat`) | Acompanhar versões novas desses dois plugins e tirar o adaptador quando declararem suporte |
| Gitleaks | 8.30.1 | Última 8.x (2026-03-21); o autor lançou o Betterleaks como sucessor e a manutenção do Gitleaks tende a só correções | Decidir se o Betterleaks (MIT, substituto direto) entra como alternativa |
| Node (imagem) | 22 | LTS; o ESLint 10 pede 20.19+ | Sem pendência |
| Hadolint | 2.12.0 | Licença GPL-3.0 | Decisão em aberto (abaixo) |
| Opengrep | 1.30.0 | LGPL-2.1 | Sem pendência |
| Checkov | 3.4.3 | Roda só em Dockerfile, Kubernetes e GitHub Actions; Terraform fica fora (ver LOG) | Sem pendência |

## Decisões em aberto

1. **Hadolint (GPL-3.0) dentro da imagem.** O Sentinel só executa o binário como processo separado e não o incorpora ao código, mas a imagem Docker publicada **distribui** esse binário. Quem distribuir a imagem precisa cumprir as obrigações da GPL para ele (aviso de licença e oferta do código-fonte) ou trocar a ferramenta. O parecer jurídico é de quem responde pela distribuição; este documento não o substitui. Dono: a definir. Alternativa técnica: manter o Hadolint como opcional (`WITH_MARKET_TOOLS=false`) na imagem pública.
2. **Gitleaks: continuar, migrar ou oferecer os dois.** Depende da linha do Gitleaks na tabela acima. Dono: a definir.

## O que a narrativa pode e não pode afirmar

- Dependências: **Trivy ou OSV.dev** (não OWASP Dependency-Check, que o Sentinel não usa).
- IaC: o Checkov cobre Dockerfile, Kubernetes e GitHub Actions; **Terraform está fora do escopo**.
- Os números de regras, pacotes, perfis e ferramentas são conferidos no CI por `scripts/conferir-numeros.py`; quem muda um pacote atualiza o `README.md` e o `docs/IMPACTO.md` na mesma hora.
- Precisão e cobertura só valem para o corpus medido (veja `docs/IMPACTO.md`); fora dele, não há número.
