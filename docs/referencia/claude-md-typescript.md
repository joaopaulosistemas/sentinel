> Referência: exemplo do CLAUDE.md que o Sentinel gera para TypeScript / JavaScript (endpoint `/api/v1/guide/ts/claude-md`, texto em `GuideBook.claudeMd`). Copiado em 2026-11-06 e corrigido à mão para o que o gerador 12.64.0 produz: o título da regra `ignoreExpiration` não é mais cortado no meio do código. As regras de "Sem exceção" mudam com os pacotes: para o texto vigente, use o endpoint.

# Regras de código — TypeScript / JavaScript

Este sistema é auditado pelo Sentinel. Aqui fica só o que não dá para deduzir do código.

## Verifique antes de dizer que terminou

- `npm ci`
- `npm test`
- `npx tsc --noEmit`
- `npm run build`
- Só diga que terminou com tudo isso passando; se algo falhar, corrija ou diga o que falhou.

## Convenções

- strict ligado: "strict": true no tsconfig; tipos explícitos nas fronteiras.
- await sem laço: Promise.all para trabalho independente; limite a concorrência.
- Entrada externa validada: Valide JSON e parâmetros com um esquema (zod, valibot).
- Sem HTML montado na mão: Deixe o framework escapar; sanitize quando HTML for inevitável.
- Erros tratados: Toda promessa tem tratamento; mostre ao usuário o que ele pode fazer.
- Dependências sob controle: package-lock versionado, npm ci no CI, npm audit.

## Armadilhas

- any e asserções (as) para calar o compilador.
- await dentro de for: serializa o que poderia ser paralelo.
- Confiar no formato que a API devolve.
- innerHTML, eval e new Function com dado do usuário.
- Não copie padrões do código legado para código novo.

## Sem exceção (severidade alta no Sentinel)

- Segredo de assinatura JWT escrito no código → O segredo de assinatura não pode estar no código: leia de cofre/variável e permita rotação
- Script carregado por HTTP sem criptografia → Carregue por HTTPS
- Possível injeção de comando (CWE-78) → Evite shell. Use `execFile`/`spawn` com cada argumento separado e valide o valor contra uma lista de permissão
- Desserialização de dado da requisição (CWE-502) → Não desserialize dado externo com `node-serialize`. Use JSON.parse e valide o formato
- Execução de código vinda da requisição (CWE-95) → Não execute texto vindo do usuário. Use um parser com lista de operações permitidas
- `ignoreExpiration: true` (CWE-613) → Não ignore a expiração do token. Renove o token em vez disso
- `Math.random()` gerando valor de segurança (CWE-338) → Use um gerador criptográfico para tokens, senhas temporárias e segredos
- Possível injeção NoSQL (CWE-943) → Não use `$where` com dado externo. Use operadores de consulta e valide o tipo (número, texto) antes

## Limites

- Função: até 60 linhas, complexidade 12, aninhamento 4. Até 5 parâmetros.
- Classe: até 500 linhas. Sem números mágicos.

## Como trabalhar

- Faça a solução mais simples que resolve o problema de hoje.
- Todo comportamento novo vem com teste que falha se o código quebrar.
- Toda chamada remota tem timeout; repita só operação idempotente, com espera exponencial e jitter.
- Exceção a uma regra: registre no `.sentinel.yml` com motivo e prazo.

## Commits e revisão

- Commit: `tipo(escopo): descrição` no imperativo, até 72 caracteres (feat, fix, docs, refactor, test, chore). O corpo explica o porquê.
- Um assunto por mudança; refatoração separada de comportamento.
- Se algo ficou fora do escopo, abra uma issue em vez de deixar "depois eu arrumo".
- Ao revisar, comente o código (não a pessoa) e marque `Nit:`, `Opcional:` ou `FYI:`.
