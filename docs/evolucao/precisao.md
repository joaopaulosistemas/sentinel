# Precisão medida por revisão manual

Revisão por amostragem feita pelo agente Sentinel Descoberta (ler o código ao redor de cada achado). Não é auditoria exaustiva: as amostras são pequenas e as taxas são indicativas.
VP = verdadeiro positivo (inclui os de pouco valor, como estilo), FP = falso positivo. Atualizar a tabela a cada rodada, somando aos números anteriores.
Para o eixo 10 do placar chegar a 3 são necessários ≥ 100 achados revisados em ≥ 10 repositórios; hoje há 10 achados em 4 repositórios.

| Regra | Revisados | VP | FP | Duvidosos | Última rodada |
|---|---:|---:|---:|---:|---|
| `eslint-@angular-eslint/prefer-inject` | 1 | 0 | 1 | 0 | 2026-10-21 (corrigida: só vale em projeto com `@angular/*`) |
| `sentinel-ts-any-explicito` | 2 | 2 | 0 | 0 | 2026-10-21 |
| `sqlfluff-RF06` | 1 | 0 | 1 | 0 | 2026-10-21 (migração gerada pelo Prisma) |
| `squawk-require-concurrent-index-creation` | 1 | 0 | 1 | 0 | 2026-10-21 (tabela nova na mesma migração) |
| `eslint-prefer-const` | 1 | 1 | 0 | 0 | 2026-10-21 |
| `sentinel-ts-supressao-de-tipos` | 1 | 0 | 1 | 0 | 2026-10-21 (corrigida: ignora `**/generated/**`) |
| `sentinel-int-http-sem-tls` | 2 | 2 | 0 | 0 | 2026-10-21 |
| `gitleaks-generic-api-key` | 1 | 0 | 0 | 1 | 2026-10-21 |
