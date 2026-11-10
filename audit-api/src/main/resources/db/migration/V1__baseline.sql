-- Linha de base do esquema do Sentinel (versao 1).
-- Gerada com pg_dump -s a partir do esquema que o Hibernate criou no teste ponta a ponta do CI.
-- Bancos que ja existem (criados pelo Hibernate) entram em baseline na versao 1 e nao rodam este arquivo.
-- Mudancas de esquema dai em diante: novos arquivos V2__*.sql, V3__*.sql ...

--
--



SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: app_setting; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.app_setting (
    setting_key character varying(100) NOT NULL,
    updated_at timestamp(6) without time zone,
    setting_value character varying(2000) NOT NULL
);


--
-- Name: audit_progress; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.audit_progress (
    audit_run_id bigint NOT NULL,
    payload character varying(100000) NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL
);


--
-- Name: audit_run; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.audit_run (
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


--
-- Name: audit_run_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.audit_run_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: audited_system; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.audited_system (
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


--
-- Name: audited_system_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.audited_system_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: engine_config_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.engine_config_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: finding; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.finding (
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


--
-- Name: finding_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.finding_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: finding_suppression; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.finding_suppression (
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


--
-- Name: finding_suppression_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.finding_suppression_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: mcp_access; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.mcp_access (
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


--
-- Name: mcp_access_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.mcp_access_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: mcp_call_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.mcp_call_log (
    id bigint NOT NULL,
    called_at timestamp(6) without time zone NOT NULL,
    detail character varying(300),
    duration_ms bigint,
    ok boolean NOT NULL,
    system_id bigint NOT NULL,
    tool character varying(60) NOT NULL
);


--
-- Name: mcp_call_log_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.mcp_call_log_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: pending_action; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.pending_action (
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


--
-- Name: pending_action_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.pending_action_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: review_suggestion; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.review_suggestion (
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


--
-- Name: review_suggestion_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.review_suggestion_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: rule_proposal; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.rule_proposal (
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


--
-- Name: rule_proposal_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.rule_proposal_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_engine_config; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_engine_config (
    id bigint NOT NULL,
    config character varying(20000) NOT NULL,
    system_id bigint NOT NULL,
    updated_at timestamp(6) without time zone
);


--
-- Name: app_setting app_setting_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_setting
    ADD CONSTRAINT app_setting_pkey PRIMARY KEY (setting_key);


--
-- Name: audit_progress audit_progress_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_progress
    ADD CONSTRAINT audit_progress_pkey PRIMARY KEY (audit_run_id);


--
-- Name: audit_run audit_run_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_run
    ADD CONSTRAINT audit_run_pkey PRIMARY KEY (id);


--
-- Name: audited_system audited_system_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audited_system
    ADD CONSTRAINT audited_system_pkey PRIMARY KEY (id);


--
-- Name: finding finding_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finding
    ADD CONSTRAINT finding_pkey PRIMARY KEY (id);


--
-- Name: finding_suppression finding_suppression_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finding_suppression
    ADD CONSTRAINT finding_suppression_pkey PRIMARY KEY (id);


--
-- Name: mcp_access mcp_access_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mcp_access
    ADD CONSTRAINT mcp_access_pkey PRIMARY KEY (id);


--
-- Name: mcp_call_log mcp_call_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mcp_call_log
    ADD CONSTRAINT mcp_call_log_pkey PRIMARY KEY (id);


--
-- Name: pending_action pending_action_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pending_action
    ADD CONSTRAINT pending_action_pkey PRIMARY KEY (id);


--
-- Name: review_suggestion review_suggestion_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.review_suggestion
    ADD CONSTRAINT review_suggestion_pkey PRIMARY KEY (id);


--
-- Name: rule_proposal rule_proposal_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rule_proposal
    ADD CONSTRAINT rule_proposal_pkey PRIMARY KEY (id);


--
-- Name: system_engine_config system_engine_config_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_engine_config
    ADD CONSTRAINT system_engine_config_pkey PRIMARY KEY (id);


--
-- Name: system_engine_config uk_engine_config_system; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_engine_config
    ADD CONSTRAINT uk_engine_config_system UNIQUE (system_id);


--
-- Name: mcp_access uk_mcp_access_system; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mcp_access
    ADD CONSTRAINT uk_mcp_access_system UNIQUE (system_id);


--
-- Name: rule_proposal uk_rule_proposal_slug; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rule_proposal
    ADD CONSTRAINT uk_rule_proposal_slug UNIQUE (system_id, slug);


--
-- Name: finding_suppression uk_suppression_system_fp; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finding_suppression
    ADD CONSTRAINT uk_suppression_system_fp UNIQUE (system_id, fingerprint);


--
-- Name: idx_audit_run_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_run_status ON public.audit_run USING btree (status);


--
-- Name: idx_audit_run_system_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_run_system_id ON public.audit_run USING btree (system_id);


--
-- Name: idx_finding_audit_run_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_finding_audit_run_id ON public.finding USING btree (audit_run_id);


--
-- Name: idx_finding_fingerprint; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_finding_fingerprint ON public.finding USING btree (fingerprint);


--
-- Name: idx_finding_severity; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_finding_severity ON public.finding USING btree (severity);


--
-- Name: idx_mcp_call_system; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_mcp_call_system ON public.mcp_call_log USING btree (system_id);


--
-- Name: idx_pending_action_system; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pending_action_system ON public.pending_action USING btree (system_id);


--
-- Name: idx_review_suggestion_system; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_review_suggestion_system ON public.review_suggestion USING btree (system_id);


--
-- Name: idx_suppression_system; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_suppression_system ON public.finding_suppression USING btree (system_id);


--
-- Name: finding fk402gy1t073lpsi5gw2oxhjrb2; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finding
    ADD CONSTRAINT fk402gy1t073lpsi5gw2oxhjrb2 FOREIGN KEY (audit_run_id) REFERENCES public.audit_run(id);


--
-- Name: finding_suppression fkap9kvqgh89ece6xisra0imeky; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finding_suppression
    ADD CONSTRAINT fkap9kvqgh89ece6xisra0imeky FOREIGN KEY (system_id) REFERENCES public.audited_system(id);


--
-- Name: audit_run fkp81ebpx2iwckqgp2w0nrrkoc4; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_run
    ADD CONSTRAINT fkp81ebpx2iwckqgp2w0nrrkoc4 FOREIGN KEY (system_id) REFERENCES public.audited_system(id);


--
--
