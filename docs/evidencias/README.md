# Evidências offline

O Sentinel analisa código, mas **consulta lenta, índice sobrando e vazamento de memória só aparecem com dados de
execução**. Para isso ele lê arquivos que você gera uma vez, no seu ambiente, e entrega numa pasta. Ele **nunca se
conecta ao seu banco nem à sua aplicação**.

## Como usar

1. Crie `./evidencias/<nome-do-sistema>/` ao lado do `docker-compose.yml`. O nome é o do sistema cadastrado no Sentinel,
   em minúsculo, sem acento e com hífens (`Sistema de Câmbio` → `sistema-de-cambio`). O nome do repositório também vale.
2. Coloque ali os arquivos da tabela abaixo. Não precisa de todos.
3. Rode a auditoria normalmente. As descobertas aparecem com a origem **Evidências** e a área **Desempenho**.

A pasta é montada somente para leitura (`EVIDENCE_HOST_DIR` muda o local). Para um repositório que já versiona estes
arquivos, vale também a pasta `.sentinel/evidence/` dentro do próprio projeto.

## O que cada arquivo revela

| Arquivo | Como gerar | O que o Sentinel aponta |
|---|---|---|
| `schema.sql` | `pg_dump --schema-only` | tabela sem chave primária, **chave estrangeira sem índice**, índice duplicado/redundante, índice em coluna booleana, `timestamp` sem fuso, `money`, `json` em vez de `jsonb`, chave `int` que vai esgotar, tabela larga |
| `pg_stat_statements.csv` | `coletar-evidencias.sql` | **consulta que consome mais tempo do banco**, consulta lenta, uso de disco temporário, leitura excessiva de disco, N+1 (milhões de chamadas) |
| `pg_stat_user_tables.csv` | `coletar-evidencias.sql` | varredura sequencial em tabela grande, tabela inchada (linhas mortas) |
| `pg_stat_user_indexes.csv` | `coletar-evidencias.sql` | **índice nunca usado** (custa escrita e espaço) |
| `explain-*.json` | `EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) <consulta>` | varredura que descarta milhões de linhas, ordenação em disco, estimativa errada, tempo total |
| `jacoco.xml` ou `jacoco.csv` | relatório do JaCoCo do seu CI | cobertura baixa, classes grandes sem teste |
| `lcov.info` | relatório de cobertura do Jest/Karma/Vitest | idem, para TypeScript |
| `class_histogram.txt` | `jcmd <pid> GC.class_histogram` | maiores consumidores do heap, classes suas com milhões de instâncias, mapas gigantes |

## Coletando do banco

Linux, macOS ou Git Bash: `./coletar-evidencias.sh "postgresql://usuario@servidor:5432/banco"`.
Windows (PowerShell): `.\coletar-evidencias.ps1 -Url "postgresql://usuario@servidor:5432/banco"`.
Ambos usam só leituras e `pg_dump --schema-only`.

## Cuidados

- **Nenhum dado entra.** `schema.sql` traz só definições. As estatísticas trazem texto de consulta normalizado
  (`WHERE id = $1`), mas confira se alguma consulta tem valores literais sensíveis antes de entregar o arquivo.
- **Estatísticas representam um período.** Um índice "nunca usado" pode servir um fechamento mensal. Colete depois
  de um ciclo completo de uso e lembre que `pg_stat_reset()` zera tudo.
- **`EXPLAIN ANALYZE` executa a consulta.** Use só com `SELECT`, de preferência em homologação.
- **Memória:** capture o histograma duas vezes com alguns minutos de carga entre elas. O que cresce nas duas capturas
  é o suspeito. O Sentinel lê uma captura por vez.
- Tamanho máximo por arquivo: 200 MB; até 50 arquivos por sistema.
