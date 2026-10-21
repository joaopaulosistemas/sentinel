-- Gera as estatísticas do Postgres que o Sentinel lê, SEM mexer em nada no banco (só consultas de leitura).
-- Uso (na pasta onde os arquivos devem ser criados):
--   psql "postgresql://usuario@servidor:5432/banco" -f coletar-evidencias.sql
-- Os CSVs saem na pasta atual; copie-os para ./evidencias/<nome-do-sistema>/ (veja README.md).
--
-- Requisitos: o usuário precisa poder ler pg_stat_statements (papel pg_read_all_stats) e a extensão
-- pg_stat_statements precisa estar ativa (shared_preload_libraries + CREATE EXTENSION).

-- Consultas mais caras (Postgres 13 ou mais novo). No 12 ou anterior, troque total_exec_time por total_time
-- e mean_exec_time por mean_time.
\copy (select query, calls, total_exec_time, mean_exec_time, rows, shared_blks_hit, shared_blks_read, temp_blks_written from pg_stat_statements order by total_exec_time desc limit 500) to 'pg_stat_statements.csv' csv header

-- Uso das tabelas: varreduras sequenciais e linhas mortas.
\copy (select schemaname, relname, seq_scan, seq_tup_read, coalesce(idx_scan, 0) as idx_scan, n_live_tup, n_dead_tup from pg_stat_user_tables) to 'pg_stat_user_tables.csv' csv header

-- Uso dos índices: índices que nunca foram lidos desde o último reset das estatísticas.
\copy (select s.schemaname, s.relname, s.indexrelname, s.idx_scan, pg_relation_size(s.indexrelid) as size_bytes, i.indexdef from pg_stat_user_indexes s join pg_indexes i on i.schemaname = s.schemaname and i.indexname = s.indexrelname) to 'pg_stat_user_indexes.csv' csv header
