# Amostra para revisão humana (item E)

Gerada na volta 46 (2026-11-18) a partir da auditoria do CI de `layro01/node-api-goat` (treino mais recente, branch `ciclo/estabilizacao-14`, regras da versão 12.116.0).
**Classificação feita na volta 47 por subagente independente do Claude** (contexto novo, recebeu só a lista e o código do alvo, sem ver diff, gabaritos nem LOG). Não é revisão externa: o João pode sobrescrever qualquer linha e a dele prevalece. Resultado: 12 VERDADEIRO, 3 VERDADEIRO DE BAIXO VALOR, 0 FALSO em 15 (estrito 12/15 = 80 %, Wilson 55 a 93 %; largo 15/15, Wilson 80 a 100 %). 8 dos 15 são duplicatas da mesma linha por regras diferentes (eval e exec), o que infla a precisão.

Meta era 20 achados de regras de segurança; o repositório só tem **15** (todos listados, sem sorteio, descartados achados de CVE em `package-lock.json`, estilo e qualidade). Na próxima amostra (volta 49), sortear 20 de um repositório com mais achados.

| # | Ferramenta | Regra | Arquivo | Linha | Classificação (V/F) |
|---|------------|-------|---------|-------|---------------------|
| 1 | Opengrep | sentinel-node-command-injection | app/server.js | 106 | VERDADEIRO: `cprocess.exec(req.query.foo)` executa string da query |
| 2 | Opengrep | sentinel-node-desserializacao | app/server.js | 97 | VERDADEIRO: `unserialize(req.query.foo)` com node-serialize permite RCE |
| 3 | Opengrep | sentinel-node-eval-requisicao | app/server.js | 32 | VERDADEIRO: `eval(req.query.red)` com entrada do usuário |
| 4 | Opengrep | sentinel-node-path-traversal | app/server.js | 54 | VERDADEIRO: `fse.readJsonSync(req.query.foo)` sem restrição de diretório (só JSON) |
| 5 | Opengrep | sentinel-node-xss-refletido | app/server.js | 62 | VERDADEIRO: `res.send("<p>..."+req.query.text)` sem escape, text/html |
| 6 | Opengrep | sentinel-node-xss-refletido | app/server.js | 80 | BAIXO VALOR: `res.send(req.query.text)` é XSS real, mas redundante com o #5 |
| 7 | Opengrep | sentinel-k8s-imagem-latest | azure-pipelines-1.yml | 17 | BAIXO VALOR: imagem `:latest` num agente IAST de pipeline de CI |
| 8 | Opengrep | sentinel-node-open-redirect | app/server.js | 89 | BAIXO VALOR: host fixo: não é open redirect, só injeção de parâmetro de query |
| 9 | ESLint | eslint-no-eval | app/server.js | 32 | VERDADEIRO: mesmo eval do #3 |
| 10 | ESLint | eslint-no-eval | app/server.js | 33 | VERDADEIRO: `eval(req.query.green, 10)` |
| 11 | ESLint | eslint-no-eval | app/server.js | 34 | VERDADEIRO: `eval(req.query.blue, 10)` |
| 12 | ESLint | eslint-security/detect-eval-with-expression | app/server.js | 32 | VERDADEIRO: duplicado do #3 por outra regra |
| 13 | ESLint | eslint-security/detect-eval-with-expression | app/server.js | 33 | VERDADEIRO: duplicado do #10 |
| 14 | ESLint | eslint-security/detect-eval-with-expression | app/server.js | 34 | VERDADEIRO: duplicado do #11 |
| 15 | ESLint | eslint-security/detect-child-process | app/server.js | 106 | VERDADEIRO: `cprocess.exec(req.query.foo)`, duplicado do #1 |
