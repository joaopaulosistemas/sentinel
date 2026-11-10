-- Datas com fuso: as 20 colunas de data/hora passam de "timestamp" para "timestamptz".
--
-- Até aqui o Hibernate gravava o horário de parede em UTC (quarkus.hibernate-orm.jdbc.timezone=UTC) numa coluna sem
-- fuso; quem lia o banco por fora (psql, BI, relatório) não sabia que era UTC. O valor continua o mesmo instante:
-- "AT TIME ZONE 'UTC'" diz ao Postgres que o que está gravado é UTC. O Java segue com LocalDateTime e a API continua
-- devolvendo a data com o deslocamento (JsonTimeZoneCustomizer).
--
-- Cada ALTER reescreve a tabela e pega ACCESS EXCLUSIVE por instantes; as tabelas do Sentinel são pequenas e o
-- Flyway roda isto uma vez, na subida da API, antes de aceitar requisições.
-- squawk-ignore-file changing-column-type

ALTER TABLE public.app_setting
    ALTER COLUMN updated_at TYPE timestamp(6) with time zone USING updated_at AT TIME ZONE 'UTC';

ALTER TABLE public.audit_progress
    ALTER COLUMN updated_at TYPE timestamp(6) with time zone USING updated_at AT TIME ZONE 'UTC';

ALTER TABLE public.audit_run
    ALTER COLUMN completed_at TYPE timestamp(6) with time zone USING completed_at AT TIME ZONE 'UTC',
    ALTER COLUMN started_at TYPE timestamp(6) with time zone USING started_at AT TIME ZONE 'UTC';

ALTER TABLE public.audited_system
    ALTER COLUMN created_at TYPE timestamp(6) with time zone USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at TYPE timestamp(6) with time zone USING updated_at AT TIME ZONE 'UTC';

ALTER TABLE public.finding
    ALTER COLUMN created_at TYPE timestamp(6) with time zone USING created_at AT TIME ZONE 'UTC';

ALTER TABLE public.finding_suppression
    ALTER COLUMN created_at TYPE timestamp(6) with time zone USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN expires_at TYPE timestamp(6) with time zone USING expires_at AT TIME ZONE 'UTC';

ALTER TABLE public.mcp_access
    ALTER COLUMN key_created_at TYPE timestamp(6) with time zone USING key_created_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at TYPE timestamp(6) with time zone USING updated_at AT TIME ZONE 'UTC';

ALTER TABLE public.mcp_call_log
    ALTER COLUMN called_at TYPE timestamp(6) with time zone USING called_at AT TIME ZONE 'UTC';

ALTER TABLE public.pending_action
    ALTER COLUMN created_at TYPE timestamp(6) with time zone USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN decided_at TYPE timestamp(6) with time zone USING decided_at AT TIME ZONE 'UTC',
    ALTER COLUMN expires_at TYPE timestamp(6) with time zone USING expires_at AT TIME ZONE 'UTC';

ALTER TABLE public.review_suggestion
    ALTER COLUMN created_at TYPE timestamp(6) with time zone USING created_at AT TIME ZONE 'UTC';

ALTER TABLE public.rule_proposal
    ALTER COLUMN created_at TYPE timestamp(6) with time zone USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN decided_at TYPE timestamp(6) with time zone USING decided_at AT TIME ZONE 'UTC',
    ALTER COLUMN observation_started_at TYPE timestamp(6) with time zone USING observation_started_at AT TIME ZONE 'UTC';

ALTER TABLE public.system_engine_config
    ALTER COLUMN updated_at TYPE timestamp(6) with time zone USING updated_at AT TIME ZONE 'UTC';
