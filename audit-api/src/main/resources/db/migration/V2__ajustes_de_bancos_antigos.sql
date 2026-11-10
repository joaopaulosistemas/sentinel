-- Ajustes que o SchemaFixups fazia a cada subida, agora uma vez só.
-- Bancos criados antes dos status/categorias novos tinham CHECKs que rejeitavam os valores atuais.
ALTER TABLE IF EXISTS audit_run DROP CONSTRAINT IF EXISTS audit_run_status_check;
ALTER TABLE IF EXISTS finding DROP CONSTRAINT IF EXISTS finding_category_check;
-- O motor SAST foi trocado de Semgrep para Opengrep; achados antigos passam a usar o nome novo.
UPDATE finding SET tool_source = 'Opengrep' WHERE tool_source = 'Semgrep';
