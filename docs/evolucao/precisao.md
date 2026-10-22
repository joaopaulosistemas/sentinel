# Precisão medida por revisão manual

Revisão por amostragem feita pelo agente Sentinel Descoberta (ler o código ao redor de cada achado). Não é auditoria exaustiva: as amostras são pequenas e as taxas são indicativas.
VP = verdadeiro positivo (inclui os de pouco valor, como estilo), FP = falso positivo. Atualizar a tabela a cada rodada, somando aos números anteriores.
Para o eixo 10 do placar chegar a 3 são necessários ≥ 100 achados revisados em ≥ 10 repositórios; hoje há 91 achados em 9 repositórios (10 em 4 na rodada de 2026-10-21, 81 em 5 na de 2026-10-22).

| Regra | Revisados | VP | FP | Duvidosos | Última rodada |
|---|---:|---:|---:|---:|---|
| `eslint-@angular-eslint/prefer-inject` | 1 | 0 | 1 | 0 | 2026-10-21 (corrigida: só vale em projeto com `@angular/*`) |
| `sentinel-ts-any-explicito` | 10 | 8 | 0 | 2 | 2026-10-22 (D: `any[]` em restrição genérica de decorator) |
| `sqlfluff-RF06` | 1 | 0 | 1 | 0 | 2026-10-21 (migração gerada pelo Prisma) |
| `squawk-require-concurrent-index-creation` | 1 | 0 | 1 | 0 | 2026-10-21 (tabela nova na mesma migração) |
| `eslint-prefer-const` | 1 | 1 | 0 | 0 | 2026-10-21 |
| `sentinel-ts-supressao-de-tipos` | 1 | 0 | 1 | 0 | 2026-10-21 (corrigida: ignora `**/generated/**`) |
| `sentinel-int-http-sem-tls` | 2 | 2 | 0 | 0 | 2026-10-21 |
| `gitleaks-generic-api-key` | 3 | 0 | 2 | 1 | 2026-10-22 (FP: selo de README com `token=` de placeholder) |
| `eslint-regexp/no-super-linear-move` | 6 | 0 | 6 | 0 | 2026-10-22 (scripts de instalação sem entrada externa; pendente) |
| `eslint-@typescript-eslint/no-explicit-any` | 6 | 4 | 0 | 2 | 2026-10-22 (D: `T = any` padrão de genérico) |
| `eslint-@typescript-eslint/no-unused-vars` | 8 | 5 | 2 | 1 | 2026-10-22 (FP: omissão por rest, parâmetro de decorator) |
| `sentinel-cc-funcao-longa` | 8 | 6 | 1 | 1 | 2026-10-22 (FP: classe `extends Base(...)` lida como função) |
| `sentinel-cc-complexidade` | 8 | 7 | 1 | 0 | 2026-10-22 (mesmo FP de classe lida como função) |
| `sentinel-ts-console-log` | 6 | 4 | 2 | 0 | 2026-10-22 (FP: saída de CLI) |
| `sentinel-cc-aninhamento` | 5 | 3 | 1 | 1 | 2026-10-22 (FP: objeto literal conta como nível; pendente) |
| `eslint-sonarjs/cognitive-complexity` | 6 | 6 | 0 | 0 | 2026-10-22 (só a métrica foi conferida) |
| `eslint-security/detect-non-literal-fs-filename` | 6 | 0 | 6 | 0 | 2026-10-22 (CLI/scripts, sem entrada externa; pendente) |
| `gitleaks-jwt` | 5 | 0 | 0 | 5 | 2026-10-22 (dump SQL de demonstração, tokens vencidos) |
| `sentinel-proj-committed-env` | 3 | 3 | 0 | 0 | 2026-10-22 |
| `sentinel-proj-no-tests` | 2 | 2 | 0 | 0 | 2026-10-22 |
| `owasp-dependency-CVE-2022-0235-node-fetch` | 1 | 0 | 1 | 0 | 2026-10-22 (corrigida: maior correção do mesmo major) |
| `hadolint-DL3044` | 1 | 1 | 0 | 0 | 2026-10-22 |

Taxa de VP nas regras de severidade média ou alta com ao menos 5 revisados (critério do eixo 2): `no-explicit-any` 4/6, `no-unused-vars` 5/8, `funcao-longa` 6/8 (MEDIUM/LOW), `complexidade` 7/8, `any-explicito` 8/10, `gitleaks-jwt` 0/5 (duvidosos), `no-super-linear-move` e `detect-non-literal-fs-filename` 0/6 (LOW). Várias regras ficam abaixo de 80%; o eixo 2 continua em 2.
