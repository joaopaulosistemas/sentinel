# Integração com CI e pre-commit

- `github-actions.yml`: auditoria em pull request com gate de código novo, SARIF no Code Scanning e comentário no PR.
- `pre-commit-config.yaml`: verificação rápida de segurança antes do commit.
- GitLab: veja a seção de CI do README principal.

Os modelos não foram executados contra um GitHub real. O workflow `.github/workflows/ci.yml` do próprio projeto roda API, CLI, testes Semgrep dos pacotes e o dashboard.
