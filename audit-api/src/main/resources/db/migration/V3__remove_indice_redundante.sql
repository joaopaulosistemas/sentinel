-- A restrição unique (system_id, fingerprint) já atende buscas por system_id: o índice só em system_id sobrava.
DROP INDEX IF EXISTS idx_suppression_system;
