-- Completa bancos que o Hibernate criou antes de colunas e tabelas mais novas.
-- Contexto: ate a V1 o Hibernate (generation=update) acrescentava colunas sozinho a cada subida. Um banco que ficou
-- sem subir por algumas versoes entra em baseline na V1 sem as colunas posteriores (ex.: audit_run.incomplete).
-- Tudo aqui e idempotente: em banco novo (V1 ja aplicada) nao muda nada. Colunas entram anuladas, como o Hibernate fazia.
-- Gerado a partir do V1__baseline.sql.

-- Sequencias
CREATE SEQUENCE IF NOT EXISTS public.audit_run_seq
 START WITH 1
 INCREMENT BY 1
 NO MINVALUE
 NO MAXVALUE
 CACHE 1;
CREATE SEQUENCE IF NOT EXISTS public.audited_system_seq
 START WITH 1
 INCREMENT BY 1
 NO MINVALUE
 NO MAXVALUE
 CACHE 1;
CREATE SEQUENCE IF NOT EXISTS public.engine_config_seq
 START WITH 1
 INCREMENT BY 1
 NO MINVALUE
 NO MAXVALUE
 CACHE 1;
CREATE SEQUENCE IF NOT EXISTS public.finding_seq
 START WITH 1
 INCREMENT BY 1
 NO MINVALUE
 NO MAXVALUE
 CACHE 1;
CREATE SEQUENCE IF NOT EXISTS public.finding_suppression_seq
 START WITH 1
 INCREMENT BY 1
 NO MINVALUE
 NO MAXVALUE
 CACHE 1;
CREATE SEQUENCE IF NOT EXISTS public.mcp_access_seq
 START WITH 1
 INCREMENT BY 1
 NO MINVALUE
 NO MAXVALUE
 CACHE 1;
CREATE SEQUENCE IF NOT EXISTS public.mcp_call_log_seq
 START WITH 1
 INCREMENT BY 1
 NO MINVALUE
 NO MAXVALUE
 CACHE 1;
CREATE SEQUENCE IF NOT EXISTS public.pending_action_seq
 START WITH 1
 INCREMENT BY 1
 NO MINVALUE
 NO MAXVALUE
 CACHE 1;
CREATE SEQUENCE IF NOT EXISTS public.review_suggestion_seq
 START WITH 1
 INCREMENT BY 1
 NO MINVALUE
 NO MAXVALUE
 CACHE 1;
CREATE SEQUENCE IF NOT EXISTS public.rule_proposal_seq
 START WITH 1
 INCREMENT BY 1
 NO MINVALUE
 NO MAXVALUE
 CACHE 1;

-- Tabelas
CREATE TABLE IF NOT EXISTS public.app_setting (
 setting_key character varying(100) NOT NULL,
 updated_at timestamp(6) without time zone,
 setting_value character varying(2000) NOT NULL
);
CREATE TABLE IF NOT EXISTS public.audit_progress (
 audit_run_id bigint NOT NULL,
 payload character varying(100000) NOT NULL,
 updated_at timestamp(6) without time zone NOT NULL
);
CREATE TABLE IF NOT EXISTS public.audit_run (
 id bigint NOT NULL,
 base_ref character varying(255),
 branch character varying(255),
 commit_sha character varying(64),
 completed_at timestamp(6) without time zone,
 context_json text,
 error_message character varying(1000),
 incomplete boolean,
 incomplete_reasons character varying(1000),
 lines_of_code bigint,
 architecture double precision,
 dependencies double precision,
 observability double precision,
 overall double precision,
 performance double precision,
 quality double precision,
 security double precision,
 sbom_json text,
 started_at timestamp(6) without time zone,
 status character varying(50) NOT NULL,
 triggered_by character varying(255),
 system_id bigint NOT NULL
);
CREATE TABLE IF NOT EXISTS public.audited_system (
 id bigint NOT NULL,
 audit_interval_hours integer,
 audit_on_push boolean,
 created_at timestamp(6) without time zone NOT NULL,
 description character varying(1000),
 git_history_depth integer,
 git_token_enc character varying(1000),
 git_token_hosts character varying(500),
 local_path character varying(500),
 local_snapshot character varying(20),
 name character varying(255) NOT NULL,
 notify_format character varying(20),
 notify_min_score_drop double precision,
 notify_min_severity character varying(20),
 notify_public_url character varying(500),
 notify_url_enc character varying(1000),
 profile character varying(63),
 repo_url character varying(500),
 scheduled_branch character varying(255),
 source_type character varying(20),
 stack character varying(50) NOT NULL,
 team character varying(255),
 updated_at timestamp(6) without time zone,
 webhook_secret_enc character varying(1000),
 CONSTRAINT audited_system_local_snapshot_check CHECK (((local_snapshot)::text = ANY ((ARRAY['COMMIT'::character varying, 'TREE'::character varying])::text[]))),
 CONSTRAINT audited_system_source_type_check CHECK (((source_type)::text = ANY ((ARRAY['GIT_URL'::character varying, 'LOCAL_PATH'::character varying])::text[]))),
 CONSTRAINT audited_system_stack_check CHECK (((stack)::text = ANY ((ARRAY['JAVA'::character varying, 'JAVASCRIPT'::character varying, 'DOTNET'::character varying, 'PYTHON'::character varying, 'COBOL'::character varying, 'MIXED'::character varying])::text[])))
);
CREATE TABLE IF NOT EXISTS public.finding (
 id bigint NOT NULL,
 ai_recommendation character varying(2000),
 category character varying(50) NOT NULL,
 code_snippet character varying(2000),
 confidence character varying(10),
 confidence_reason character varying(300),
 created_at timestamp(6) without time zone NOT NULL,
 cve_id character varying(50),
 description character varying(2000),
 file_path character varying(1000),
 fingerprint character varying(64),
 is_new boolean,
 line_number integer,
 rule_id character varying(255),
 severity character varying(50) NOT NULL,
 shadow boolean,
 status character varying(50) NOT NULL,
 title character varying(500) NOT NULL,
 tool_source character varying(100),
 audit_run_id bigint NOT NULL,
 CONSTRAINT finding_confidence_check CHECK (((confidence)::text = ANY ((ARRAY['HIGH'::character varying, 'MEDIUM'::character varying, 'LOW'::character varying])::text[]))),
 CONSTRAINT finding_severity_check CHECK (((severity)::text = ANY ((ARRAY['CRITICAL'::character varying, 'HIGH'::character varying, 'MEDIUM'::character varying, 'LOW'::character varying, 'INFO'::character varying])::text[]))),
 CONSTRAINT finding_status_check CHECK (((status)::text = ANY ((ARRAY['OPEN'::character varying, 'ACKNOWLEDGED'::character varying, 'FIXED'::character varying, 'SUPPRESSED'::character varying])::text[])))
);
CREATE TABLE IF NOT EXISTS public.finding_suppression (
 id bigint NOT NULL,
 created_at timestamp(6) without time zone NOT NULL,
 created_by character varying(255),
 expires_at timestamp(6) without time zone,
 file_path character varying(1000),
 fingerprint character varying(64) NOT NULL,
 justification character varying(1000) NOT NULL,
 reason character varying(50) NOT NULL,
 rule_id character varying(255),
 title character varying(500),
 system_id bigint NOT NULL,
 CONSTRAINT finding_suppression_reason_check CHECK (((reason)::text = ANY ((ARRAY['FALSE_POSITIVE'::character varying, 'ACCEPTED_RISK'::character varying, 'WONT_FIX'::character varying])::text[])))
);
CREATE TABLE IF NOT EXISTS public.mcp_access (
 id bigint NOT NULL,
 data_level character varying(20) NOT NULL,
 enabled boolean NOT NULL,
 key_created_at timestamp(6) without time zone,
 key_hash character varying(64),
 key_prefix character varying(16),
 system_id bigint NOT NULL,
 tools character varying(500),
 updated_at timestamp(6) without time zone,
 CONSTRAINT mcp_access_data_level_check CHECK (((data_level)::text = ANY ((ARRAY['TITLES'::character varying, 'FILE_LINE'::character varying, 'SENSITIVE'::character varying])::text[])))
);
CREATE TABLE IF NOT EXISTS public.mcp_call_log (
 id bigint NOT NULL,
 called_at timestamp(6) without time zone NOT NULL,
 detail character varying(300),
 duration_ms bigint,
 ok boolean NOT NULL,
 system_id bigint NOT NULL,
 tool character varying(60) NOT NULL
);
CREATE TABLE IF NOT EXISTS public.pending_action (
 id bigint NOT NULL,
 created_at timestamp(6) without time zone NOT NULL,
 decided_at timestamp(6) without time zone,
 expires_at timestamp(6) without time zone NOT NULL,
 kind character varying(30) NOT NULL,
 payload character varying(2000) NOT NULL,
 result_audit_run_id bigint,
 result_note character varying(500),
 status character varying(20) NOT NULL,
 summary character varying(600) NOT NULL,
 system_id bigint NOT NULL,
 CONSTRAINT pending_action_kind_check CHECK (((kind)::text = ANY ((ARRAY['START_AUDIT'::character varying, 'SUPPRESS_FINDING'::character varying])::text[]))),
 CONSTRAINT pending_action_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying, 'EXPIRED'::character varying])::text[])))
);
CREATE TABLE IF NOT EXISTS public.review_suggestion (
 id bigint NOT NULL,
 created_at timestamp(6) without time zone NOT NULL,
 detail character varying(2000),
 file_path character varying(500),
 line_number integer,
 rule_ref character varying(255),
 status character varying(20) NOT NULL,
 system_id bigint NOT NULL,
 title character varying(200) NOT NULL,
 CONSTRAINT review_suggestion_status_check CHECK (((status)::text = ANY ((ARRAY['OPEN'::character varying, 'ADDRESSED'::character varying, 'DISMISSED'::character varying])::text[])))
);
CREATE TABLE IF NOT EXISTS public.rule_proposal (
 id bigint NOT NULL,
 copied_from_id bigint,
 created_at timestamp(6) without time zone NOT NULL,
 decided_at timestamp(6) without time zone,
 decision_note character varying(500),
 observation_started_at timestamp(6) without time zone,
 rationale character varying(2000),
 sample_note character varying(500),
 semgrep_yaml character varying(8000) NOT NULL,
 slug character varying(60) NOT NULL,
 stage character varying(20) NOT NULL,
 system_id bigint NOT NULL,
 title character varying(200) NOT NULL,
 CONSTRAINT rule_proposal_stage_check CHECK (((stage)::text = ANY ((ARRAY['PROPOSED'::character varying, 'REVIEWED'::character varying, 'SAMPLE_PASSED'::character varying, 'OBSERVING'::character varying, 'PROMOTED'::character varying, 'DISCARDED'::character varying])::text[])))
);
CREATE TABLE IF NOT EXISTS public.system_engine_config (
 id bigint NOT NULL,
 config character varying(20000) NOT NULL,
 system_id bigint NOT NULL,
 updated_at timestamp(6) without time zone
);

-- Colunas
ALTER TABLE public.app_setting ADD COLUMN IF NOT EXISTS setting_key character varying(100);
ALTER TABLE public.app_setting ADD COLUMN IF NOT EXISTS updated_at timestamp(6) without time zone;
ALTER TABLE public.app_setting ADD COLUMN IF NOT EXISTS setting_value character varying(2000);
ALTER TABLE public.audit_progress ADD COLUMN IF NOT EXISTS audit_run_id bigint;
ALTER TABLE public.audit_progress ADD COLUMN IF NOT EXISTS payload character varying(100000);
ALTER TABLE public.audit_progress ADD COLUMN IF NOT EXISTS updated_at timestamp(6) without time zone;
ALTER TABLE public.audit_run ADD COLUMN IF NOT EXISTS id bigint;
ALTER TABLE public.audit_run ADD COLUMN IF NOT EXISTS base_ref character varying(255);
ALTER TABLE public.audit_run ADD COLUMN IF NOT EXISTS branch character varying(255);
ALTER TABLE public.audit_run ADD COLUMN IF NOT EXISTS commit_sha character varying(64);
ALTER TABLE public.audit_run ADD COLUMN IF NOT EXISTS completed_at timestamp(6) without time zone;
ALTER TABLE public.audit_run ADD COLUMN IF NOT EXISTS context_json text;
ALTER TABLE public.audit_run ADD COLUMN IF NOT EXISTS error_message character varying(1000);
ALTER TABLE public.audit_run ADD COLUMN IF NOT EXISTS incomplete boolean;
ALTER TABLE public.audit_run ADD COLUMN IF NOT EXISTS incomplete_reasons character varying(1000);
ALTER TABLE public.audit_run ADD COLUMN IF NOT EXISTS lines_of_code bigint;
ALTER TABLE public.audit_run ADD COLUMN IF NOT EXISTS architecture double precision;
ALTER TABLE public.audit_run ADD COLUMN IF NOT EXISTS dependencies double precision;
ALTER TABLE public.audit_run ADD COLUMN IF NOT EXISTS observability double precision;
ALTER TABLE public.audit_run ADD COLUMN IF NOT EXISTS overall double precision;
ALTER TABLE public.audit_run ADD COLUMN IF NOT EXISTS performance double precision;
ALTER TABLE public.audit_run ADD COLUMN IF NOT EXISTS quality double precision;
ALTER TABLE public.audit_run ADD COLUMN IF NOT EXISTS security double precision;
ALTER TABLE public.audit_run ADD COLUMN IF NOT EXISTS sbom_json text;
ALTER TABLE public.audit_run ADD COLUMN IF NOT EXISTS started_at timestamp(6) without time zone;
ALTER TABLE public.audit_run ADD COLUMN IF NOT EXISTS status character varying(50);
ALTER TABLE public.audit_run ADD COLUMN IF NOT EXISTS triggered_by character varying(255);
ALTER TABLE public.audit_run ADD COLUMN IF NOT EXISTS system_id bigint;
ALTER TABLE public.audited_system ADD COLUMN IF NOT EXISTS id bigint;
ALTER TABLE public.audited_system ADD COLUMN IF NOT EXISTS audit_interval_hours integer;
ALTER TABLE public.audited_system ADD COLUMN IF NOT EXISTS audit_on_push boolean;
ALTER TABLE public.audited_system ADD COLUMN IF NOT EXISTS created_at timestamp(6) without time zone;
ALTER TABLE public.audited_system ADD COLUMN IF NOT EXISTS description character varying(1000);
ALTER TABLE public.audited_system ADD COLUMN IF NOT EXISTS git_history_depth integer;
ALTER TABLE public.audited_system ADD COLUMN IF NOT EXISTS git_token_enc character varying(1000);
ALTER TABLE public.audited_system ADD COLUMN IF NOT EXISTS git_token_hosts character varying(500);
ALTER TABLE public.audited_system ADD COLUMN IF NOT EXISTS local_path character varying(500);
ALTER TABLE public.audited_system ADD COLUMN IF NOT EXISTS local_snapshot character varying(20);
ALTER TABLE public.audited_system ADD COLUMN IF NOT EXISTS name character varying(255);
ALTER TABLE public.audited_system ADD COLUMN IF NOT EXISTS notify_format character varying(20);
ALTER TABLE public.audited_system ADD COLUMN IF NOT EXISTS notify_min_score_drop double precision;
ALTER TABLE public.audited_system ADD COLUMN IF NOT EXISTS notify_min_severity character varying(20);
ALTER TABLE public.audited_system ADD COLUMN IF NOT EXISTS notify_public_url character varying(500);
ALTER TABLE public.audited_system ADD COLUMN IF NOT EXISTS notify_url_enc character varying(1000);
ALTER TABLE public.audited_system ADD COLUMN IF NOT EXISTS profile character varying(63);
ALTER TABLE public.audited_system ADD COLUMN IF NOT EXISTS repo_url character varying(500);
ALTER TABLE public.audited_system ADD COLUMN IF NOT EXISTS scheduled_branch character varying(255);
ALTER TABLE public.audited_system ADD COLUMN IF NOT EXISTS source_type character varying(20);
ALTER TABLE public.audited_system ADD COLUMN IF NOT EXISTS stack character varying(50);
ALTER TABLE public.audited_system ADD COLUMN IF NOT EXISTS team character varying(255);
ALTER TABLE public.audited_system ADD COLUMN IF NOT EXISTS updated_at timestamp(6) without time zone;
ALTER TABLE public.audited_system ADD COLUMN IF NOT EXISTS webhook_secret_enc character varying(1000);
ALTER TABLE public.finding ADD COLUMN IF NOT EXISTS id bigint;
ALTER TABLE public.finding ADD COLUMN IF NOT EXISTS ai_recommendation character varying(2000);
ALTER TABLE public.finding ADD COLUMN IF NOT EXISTS category character varying(50);
ALTER TABLE public.finding ADD COLUMN IF NOT EXISTS code_snippet character varying(2000);
ALTER TABLE public.finding ADD COLUMN IF NOT EXISTS confidence character varying(10);
ALTER TABLE public.finding ADD COLUMN IF NOT EXISTS confidence_reason character varying(300);
ALTER TABLE public.finding ADD COLUMN IF NOT EXISTS created_at timestamp(6) without time zone;
ALTER TABLE public.finding ADD COLUMN IF NOT EXISTS cve_id character varying(50);
ALTER TABLE public.finding ADD COLUMN IF NOT EXISTS description character varying(2000);
ALTER TABLE public.finding ADD COLUMN IF NOT EXISTS file_path character varying(1000);
ALTER TABLE public.finding ADD COLUMN IF NOT EXISTS fingerprint character varying(64);
ALTER TABLE public.finding ADD COLUMN IF NOT EXISTS is_new boolean;
ALTER TABLE public.finding ADD COLUMN IF NOT EXISTS line_number integer;
ALTER TABLE public.finding ADD COLUMN IF NOT EXISTS rule_id character varying(255);
ALTER TABLE public.finding ADD COLUMN IF NOT EXISTS severity character varying(50);
ALTER TABLE public.finding ADD COLUMN IF NOT EXISTS shadow boolean;
ALTER TABLE public.finding ADD COLUMN IF NOT EXISTS status character varying(50);
ALTER TABLE public.finding ADD COLUMN IF NOT EXISTS title character varying(500);
ALTER TABLE public.finding ADD COLUMN IF NOT EXISTS tool_source character varying(100);
ALTER TABLE public.finding ADD COLUMN IF NOT EXISTS audit_run_id bigint;
ALTER TABLE public.finding_suppression ADD COLUMN IF NOT EXISTS id bigint;
ALTER TABLE public.finding_suppression ADD COLUMN IF NOT EXISTS created_at timestamp(6) without time zone;
ALTER TABLE public.finding_suppression ADD COLUMN IF NOT EXISTS created_by character varying(255);
ALTER TABLE public.finding_suppression ADD COLUMN IF NOT EXISTS expires_at timestamp(6) without time zone;
ALTER TABLE public.finding_suppression ADD COLUMN IF NOT EXISTS file_path character varying(1000);
ALTER TABLE public.finding_suppression ADD COLUMN IF NOT EXISTS fingerprint character varying(64);
ALTER TABLE public.finding_suppression ADD COLUMN IF NOT EXISTS justification character varying(1000);
ALTER TABLE public.finding_suppression ADD COLUMN IF NOT EXISTS reason character varying(50);
ALTER TABLE public.finding_suppression ADD COLUMN IF NOT EXISTS rule_id character varying(255);
ALTER TABLE public.finding_suppression ADD COLUMN IF NOT EXISTS title character varying(500);
ALTER TABLE public.finding_suppression ADD COLUMN IF NOT EXISTS system_id bigint;
ALTER TABLE public.mcp_access ADD COLUMN IF NOT EXISTS id bigint;
ALTER TABLE public.mcp_access ADD COLUMN IF NOT EXISTS data_level character varying(20);
ALTER TABLE public.mcp_access ADD COLUMN IF NOT EXISTS enabled boolean;
ALTER TABLE public.mcp_access ADD COLUMN IF NOT EXISTS key_created_at timestamp(6) without time zone;
ALTER TABLE public.mcp_access ADD COLUMN IF NOT EXISTS key_hash character varying(64);
ALTER TABLE public.mcp_access ADD COLUMN IF NOT EXISTS key_prefix character varying(16);
ALTER TABLE public.mcp_access ADD COLUMN IF NOT EXISTS system_id bigint;
ALTER TABLE public.mcp_access ADD COLUMN IF NOT EXISTS tools character varying(500);
ALTER TABLE public.mcp_access ADD COLUMN IF NOT EXISTS updated_at timestamp(6) without time zone;
ALTER TABLE public.mcp_call_log ADD COLUMN IF NOT EXISTS id bigint;
ALTER TABLE public.mcp_call_log ADD COLUMN IF NOT EXISTS called_at timestamp(6) without time zone;
ALTER TABLE public.mcp_call_log ADD COLUMN IF NOT EXISTS detail character varying(300);
ALTER TABLE public.mcp_call_log ADD COLUMN IF NOT EXISTS duration_ms bigint;
ALTER TABLE public.mcp_call_log ADD COLUMN IF NOT EXISTS ok boolean;
ALTER TABLE public.mcp_call_log ADD COLUMN IF NOT EXISTS system_id bigint;
ALTER TABLE public.mcp_call_log ADD COLUMN IF NOT EXISTS tool character varying(60);
ALTER TABLE public.pending_action ADD COLUMN IF NOT EXISTS id bigint;
ALTER TABLE public.pending_action ADD COLUMN IF NOT EXISTS created_at timestamp(6) without time zone;
ALTER TABLE public.pending_action ADD COLUMN IF NOT EXISTS decided_at timestamp(6) without time zone;
ALTER TABLE public.pending_action ADD COLUMN IF NOT EXISTS expires_at timestamp(6) without time zone;
ALTER TABLE public.pending_action ADD COLUMN IF NOT EXISTS kind character varying(30);
ALTER TABLE public.pending_action ADD COLUMN IF NOT EXISTS payload character varying(2000);
ALTER TABLE public.pending_action ADD COLUMN IF NOT EXISTS result_audit_run_id bigint;
ALTER TABLE public.pending_action ADD COLUMN IF NOT EXISTS result_note character varying(500);
ALTER TABLE public.pending_action ADD COLUMN IF NOT EXISTS status character varying(20);
ALTER TABLE public.pending_action ADD COLUMN IF NOT EXISTS summary character varying(600);
ALTER TABLE public.pending_action ADD COLUMN IF NOT EXISTS system_id bigint;
ALTER TABLE public.review_suggestion ADD COLUMN IF NOT EXISTS id bigint;
ALTER TABLE public.review_suggestion ADD COLUMN IF NOT EXISTS created_at timestamp(6) without time zone;
ALTER TABLE public.review_suggestion ADD COLUMN IF NOT EXISTS detail character varying(2000);
ALTER TABLE public.review_suggestion ADD COLUMN IF NOT EXISTS file_path character varying(500);
ALTER TABLE public.review_suggestion ADD COLUMN IF NOT EXISTS line_number integer;
ALTER TABLE public.review_suggestion ADD COLUMN IF NOT EXISTS rule_ref character varying(255);
ALTER TABLE public.review_suggestion ADD COLUMN IF NOT EXISTS status character varying(20);
ALTER TABLE public.review_suggestion ADD COLUMN IF NOT EXISTS system_id bigint;
ALTER TABLE public.review_suggestion ADD COLUMN IF NOT EXISTS title character varying(200);
ALTER TABLE public.rule_proposal ADD COLUMN IF NOT EXISTS id bigint;
ALTER TABLE public.rule_proposal ADD COLUMN IF NOT EXISTS copied_from_id bigint;
ALTER TABLE public.rule_proposal ADD COLUMN IF NOT EXISTS created_at timestamp(6) without time zone;
ALTER TABLE public.rule_proposal ADD COLUMN IF NOT EXISTS decided_at timestamp(6) without time zone;
ALTER TABLE public.rule_proposal ADD COLUMN IF NOT EXISTS decision_note character varying(500);
ALTER TABLE public.rule_proposal ADD COLUMN IF NOT EXISTS observation_started_at timestamp(6) without time zone;
ALTER TABLE public.rule_proposal ADD COLUMN IF NOT EXISTS rationale character varying(2000);
ALTER TABLE public.rule_proposal ADD COLUMN IF NOT EXISTS sample_note character varying(500);
ALTER TABLE public.rule_proposal ADD COLUMN IF NOT EXISTS semgrep_yaml character varying(8000);
ALTER TABLE public.rule_proposal ADD COLUMN IF NOT EXISTS slug character varying(60);
ALTER TABLE public.rule_proposal ADD COLUMN IF NOT EXISTS stage character varying(20);
ALTER TABLE public.rule_proposal ADD COLUMN IF NOT EXISTS system_id bigint;
ALTER TABLE public.rule_proposal ADD COLUMN IF NOT EXISTS title character varying(200);
ALTER TABLE public.system_engine_config ADD COLUMN IF NOT EXISTS id bigint;
ALTER TABLE public.system_engine_config ADD COLUMN IF NOT EXISTS config character varying(20000);
ALTER TABLE public.system_engine_config ADD COLUMN IF NOT EXISTS system_id bigint;
ALTER TABLE public.system_engine_config ADD COLUMN IF NOT EXISTS updated_at timestamp(6) without time zone;

-- Indices
CREATE INDEX IF NOT EXISTS idx_audit_run_status ON public.audit_run USING btree (status);
CREATE INDEX IF NOT EXISTS idx_audit_run_system_id ON public.audit_run USING btree (system_id);
CREATE INDEX IF NOT EXISTS idx_finding_audit_run_id ON public.finding USING btree (audit_run_id);
CREATE INDEX IF NOT EXISTS idx_finding_fingerprint ON public.finding USING btree (fingerprint);
CREATE INDEX IF NOT EXISTS idx_finding_severity ON public.finding USING btree (severity);
CREATE INDEX IF NOT EXISTS idx_mcp_call_system ON public.mcp_call_log USING btree (system_id);
CREATE INDEX IF NOT EXISTS idx_pending_action_system ON public.pending_action USING btree (system_id);
CREATE INDEX IF NOT EXISTS idx_review_suggestion_system ON public.review_suggestion USING btree (system_id);

-- Chaves e restricoes
DO $$ BEGIN
    ALTER TABLE ONLY public.app_setting  ADD CONSTRAINT app_setting_pkey PRIMARY KEY (setting_key);
EXCEPTION WHEN duplicate_object OR duplicate_table OR invalid_table_definition OR unique_violation OR foreign_key_violation THEN NULL;
END $$;
DO $$ BEGIN
    ALTER TABLE ONLY public.audit_progress  ADD CONSTRAINT audit_progress_pkey PRIMARY KEY (audit_run_id);
EXCEPTION WHEN duplicate_object OR duplicate_table OR invalid_table_definition OR unique_violation OR foreign_key_violation THEN NULL;
END $$;
DO $$ BEGIN
    ALTER TABLE ONLY public.audit_run  ADD CONSTRAINT audit_run_pkey PRIMARY KEY (id);
EXCEPTION WHEN duplicate_object OR duplicate_table OR invalid_table_definition OR unique_violation OR foreign_key_violation THEN NULL;
END $$;
DO $$ BEGIN
    ALTER TABLE ONLY public.audited_system  ADD CONSTRAINT audited_system_pkey PRIMARY KEY (id);
EXCEPTION WHEN duplicate_object OR duplicate_table OR invalid_table_definition OR unique_violation OR foreign_key_violation THEN NULL;
END $$;
DO $$ BEGIN
    ALTER TABLE ONLY public.finding  ADD CONSTRAINT finding_pkey PRIMARY KEY (id);
EXCEPTION WHEN duplicate_object OR duplicate_table OR invalid_table_definition OR unique_violation OR foreign_key_violation THEN NULL;
END $$;
DO $$ BEGIN
    ALTER TABLE ONLY public.finding_suppression  ADD CONSTRAINT finding_suppression_pkey PRIMARY KEY (id);
EXCEPTION WHEN duplicate_object OR duplicate_table OR invalid_table_definition OR unique_violation OR foreign_key_violation THEN NULL;
END $$;
DO $$ BEGIN
    ALTER TABLE ONLY public.mcp_access  ADD CONSTRAINT mcp_access_pkey PRIMARY KEY (id);
EXCEPTION WHEN duplicate_object OR duplicate_table OR invalid_table_definition OR unique_violation OR foreign_key_violation THEN NULL;
END $$;
DO $$ BEGIN
    ALTER TABLE ONLY public.mcp_call_log  ADD CONSTRAINT mcp_call_log_pkey PRIMARY KEY (id);
EXCEPTION WHEN duplicate_object OR duplicate_table OR invalid_table_definition OR unique_violation OR foreign_key_violation THEN NULL;
END $$;
DO $$ BEGIN
    ALTER TABLE ONLY public.pending_action  ADD CONSTRAINT pending_action_pkey PRIMARY KEY (id);
EXCEPTION WHEN duplicate_object OR duplicate_table OR invalid_table_definition OR unique_violation OR foreign_key_violation THEN NULL;
END $$;
DO $$ BEGIN
    ALTER TABLE ONLY public.review_suggestion  ADD CONSTRAINT review_suggestion_pkey PRIMARY KEY (id);
EXCEPTION WHEN duplicate_object OR duplicate_table OR invalid_table_definition OR unique_violation OR foreign_key_violation THEN NULL;
END $$;
DO $$ BEGIN
    ALTER TABLE ONLY public.rule_proposal  ADD CONSTRAINT rule_proposal_pkey PRIMARY KEY (id);
EXCEPTION WHEN duplicate_object OR duplicate_table OR invalid_table_definition OR unique_violation OR foreign_key_violation THEN NULL;
END $$;
DO $$ BEGIN
    ALTER TABLE ONLY public.system_engine_config  ADD CONSTRAINT system_engine_config_pkey PRIMARY KEY (id);
EXCEPTION WHEN duplicate_object OR duplicate_table OR invalid_table_definition OR unique_violation OR foreign_key_violation THEN NULL;
END $$;
DO $$ BEGIN
    ALTER TABLE ONLY public.system_engine_config  ADD CONSTRAINT uk_engine_config_system UNIQUE (system_id);
EXCEPTION WHEN duplicate_object OR duplicate_table OR invalid_table_definition OR unique_violation OR foreign_key_violation THEN NULL;
END $$;
DO $$ BEGIN
    ALTER TABLE ONLY public.mcp_access  ADD CONSTRAINT uk_mcp_access_system UNIQUE (system_id);
EXCEPTION WHEN duplicate_object OR duplicate_table OR invalid_table_definition OR unique_violation OR foreign_key_violation THEN NULL;
END $$;
DO $$ BEGIN
    ALTER TABLE ONLY public.rule_proposal  ADD CONSTRAINT uk_rule_proposal_slug UNIQUE (system_id, slug);
EXCEPTION WHEN duplicate_object OR duplicate_table OR invalid_table_definition OR unique_violation OR foreign_key_violation THEN NULL;
END $$;
DO $$ BEGIN
    ALTER TABLE ONLY public.finding_suppression  ADD CONSTRAINT uk_suppression_system_fp UNIQUE (system_id, fingerprint);
EXCEPTION WHEN duplicate_object OR duplicate_table OR invalid_table_definition OR unique_violation OR foreign_key_violation THEN NULL;
END $$;
DO $$ BEGIN
    ALTER TABLE ONLY public.finding  ADD CONSTRAINT fk402gy1t073lpsi5gw2oxhjrb2 FOREIGN KEY (audit_run_id) REFERENCES public.audit_run(id);
EXCEPTION WHEN duplicate_object OR duplicate_table OR invalid_table_definition OR unique_violation OR foreign_key_violation THEN NULL;
END $$;
DO $$ BEGIN
    ALTER TABLE ONLY public.finding_suppression  ADD CONSTRAINT fkap9kvqgh89ece6xisra0imeky FOREIGN KEY (system_id) REFERENCES public.audited_system(id);
EXCEPTION WHEN duplicate_object OR duplicate_table OR invalid_table_definition OR unique_violation OR foreign_key_violation THEN NULL;
END $$;
DO $$ BEGIN
    ALTER TABLE ONLY public.audit_run  ADD CONSTRAINT fkp81ebpx2iwckqgp2w0nrrkoc4 FOREIGN KEY (system_id) REFERENCES public.audited_system(id);
EXCEPTION WHEN duplicate_object OR duplicate_table OR invalid_table_definition OR unique_violation OR foreign_key_violation THEN NULL;
END $$;
