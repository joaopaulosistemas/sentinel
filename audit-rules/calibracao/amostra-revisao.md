# Amostra para revisão humana (item E)

Gerada na volta 46 (2026-11-18) a partir da auditoria do CI de `layro01/node-api-goat` (treino mais recente, branch `ciclo/estabilizacao-14`, regras da versão 12.116.0).
**Sem classificação**: quem diz se cada linha é verdadeiro ou falso positivo é o João ou o revisor externo. O agente que escreve as regras não classifica esta amostra.

Meta era 20 achados de regras de segurança; o repositório só tem **15** (todos listados, sem sorteio, descartados achados de CVE em `package-lock.json`, estilo e qualidade). Na próxima amostra (volta 49), sortear 20 de um repositório com mais achados.

| # | Ferramenta | Regra | Arquivo | Linha | Classificação (V/F) |
|---|------------|-------|---------|-------|---------------------|
| 1 | Opengrep | sentinel-node-command-injection | app/server.js | 106 | |
| 2 | Opengrep | sentinel-node-desserializacao | app/server.js | 97 | |
| 3 | Opengrep | sentinel-node-eval-requisicao | app/server.js | 32 | |
| 4 | Opengrep | sentinel-node-path-traversal | app/server.js | 54 | |
| 5 | Opengrep | sentinel-node-xss-refletido | app/server.js | 62 | |
| 6 | Opengrep | sentinel-node-xss-refletido | app/server.js | 80 | |
| 7 | Opengrep | sentinel-k8s-imagem-latest | azure-pipelines-1.yml | 17 | |
| 8 | Opengrep | sentinel-node-open-redirect | app/server.js | 89 | |
| 9 | ESLint | eslint-no-eval | app/server.js | 32 | |
| 10 | ESLint | eslint-no-eval | app/server.js | 33 | |
| 11 | ESLint | eslint-no-eval | app/server.js | 34 | |
| 12 | ESLint | eslint-security/detect-eval-with-expression | app/server.js | 32 | |
| 13 | ESLint | eslint-security/detect-eval-with-expression | app/server.js | 33 | |
| 14 | ESLint | eslint-security/detect-eval-with-expression | app/server.js | 34 | |
| 15 | ESLint | eslint-security/detect-child-process | app/server.js | 106 | |
