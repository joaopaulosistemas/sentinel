# Integração

- [`mcp.md`](mcp.md): o Claude (Claude Code ou outro cliente MCP) consulta o Sentinel, sugere regras e pede reauditoria, com aprovação de uma pessoa. A nota continua sendo só do motor de regras.
- [`github-actions.yml`](github-actions.yml): auditoria em pull request com gate de código novo, SARIF no Code Scanning e comentário no PR.
- [`pre-commit-config.yaml`](pre-commit-config.yaml): verificação rápida de segurança antes do commit.
- GitLab: veja a seção de CI do README principal.

Os dois modelos passam no `actionlint`, mas não foram executados num GitHub real nem em repositório de terceiros. O workflow `.github/workflows/ci.yml` do próprio projeto (API, CLI, testes Semgrep dos pacotes, dashboard, imagens, Helm, ponta a ponta e calibração) roda no GitHub Actions; veja `docs/evolucao/AGENTE.md` para o orçamento de minutos.
