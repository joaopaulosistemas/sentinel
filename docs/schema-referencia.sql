-- V1__init.sql
-- Migração inicial: cria as tabelas base do Sentinel Audit Tool

-- Sequences
CREATE SEQUENCE IF NOT EXISTS audited_system_seq START 1 INCREMENT 1;
CREATE SEQUENCE IF NOT EXISTS audit_run_seq      START 1 INCREMENT 1;
CREATE SEQUENCE IF NOT EXISTS finding_seq        START 1 INCREMENT 1;

-- ============================================================
-- Tabela: audited_system
-- ============================================================
CREATE TABLE IF NOT EXISTS audited_system (
    id          BIGINT       NOT NULL DEFAULT nextval('audited_system_seq'),
    name        VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    repo_url    VARCHAR(500),
    stack       VARCHAR(50)  NOT NULL,
    team        VARCHAR(255),
    profile     VARCHAR(63),            -- perfil de regras (vazio = default)
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP,

    CONSTRAINT pk_audited_system PRIMARY KEY (id)
);

-- ============================================================
-- Tabela: audit_run
-- ============================================================
CREATE TABLE IF NOT EXISTS audit_run (
    id           BIGINT       NOT NULL DEFAULT nextval('audit_run_seq'),
    system_id    BIGINT       NOT NULL,
    branch       VARCHAR(255),
    base_ref     VARCHAR(255),          -- auditoria de PR: branch base (nulo = auditoria completa)
    sbom_json    TEXT,                  -- SBOM CycloneDX das dependências
    context_json TEXT,                  -- mapa do projeto e situação dos analisadores (insights)
    triggered_by VARCHAR(255),
    status       VARCHAR(50)  NOT NULL,
    overall      DOUBLE PRECISION,
    security     DOUBLE PRECISION,
    quality      DOUBLE PRECISION,
    architecture DOUBLE PRECISION,
    dependencies DOUBLE PRECISION,
    observability DOUBLE PRECISION,
    started_at   TIMESTAMP,
    completed_at TIMESTAMP,

    CONSTRAINT pk_audit_run PRIMARY KEY (id),
    CONSTRAINT fk_audit_run_system FOREIGN KEY (system_id) REFERENCES audited_system(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_audit_run_system_id ON audit_run (system_id);
CREATE INDEX IF NOT EXISTS idx_audit_run_status    ON audit_run (status);

-- ============================================================
-- Tabela: finding
-- ============================================================
CREATE TABLE IF NOT EXISTS finding (
    id               BIGINT       NOT NULL DEFAULT nextval('finding_seq'),
    audit_run_id     BIGINT       NOT NULL,
    category         VARCHAR(50)  NOT NULL,
    severity         VARCHAR(50)  NOT NULL,
    title            VARCHAR(500) NOT NULL,
    description      VARCHAR(2000),
    file_path        VARCHAR(1000),
    line_number      INT,
    code_snippet     VARCHAR(2000),
    rule_id          VARCHAR(255),
    tool_source      VARCHAR(100),
    cve_id           VARCHAR(50),
    ai_recommendation VARCHAR(2000),
    status           VARCHAR(50)  NOT NULL DEFAULT 'OPEN',
    shadow           BOOLEAN,               -- regra em modo sombra: não pesa no score
    created_at       TIMESTAMP    NOT NULL,

    CONSTRAINT pk_finding PRIMARY KEY (id),
    CONSTRAINT fk_finding_audit_run FOREIGN KEY (audit_run_id) REFERENCES audit_run(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_finding_audit_run_id ON finding (audit_run_id);
CREATE INDEX IF NOT EXISTS idx_finding_severity     ON finding (severity);
