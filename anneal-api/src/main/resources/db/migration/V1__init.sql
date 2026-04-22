CREATE EXTENSION IF NOT EXISTS vector;

CREATE SCHEMA IF NOT EXISTS anneal;

CREATE TABLE IF NOT EXISTS anneal.scan_results (
    id              BIGSERIAL PRIMARY KEY,
    scan_id         TEXT        NOT NULL UNIQUE,
    repo_path       TEXT        NOT NULL,
    detected_version TEXT       NOT NULL,
    target_version  TEXT        NOT NULL DEFAULT 'V25',
    risk_score      INTEGER     NOT NULL DEFAULT 0,
    risk_band       TEXT        NOT NULL DEFAULT 'LOW',
    phase           TEXT        NOT NULL DEFAULT 'ANALYSIS',
    files_scanned   INTEGER     NOT NULL DEFAULT 0,
    files_with_findings INTEGER NOT NULL DEFAULT 0,
    scanned_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS anneal.findings (
    id              BIGSERIAL PRIMARY KEY,
    finding_id      TEXT        NOT NULL UNIQUE,
    scan_id         TEXT        NOT NULL REFERENCES anneal.scan_results(scan_id),
    rule_id         TEXT        NOT NULL,
    rule_name       TEXT        NOT NULL,
    category        TEXT        NOT NULL,
    severity        TEXT        NOT NULL,
    effort          TEXT        NOT NULL,
    file_path       TEXT        NOT NULL,
    line_number     INTEGER     NOT NULL DEFAULT 0,
    original_code   TEXT,
    description     TEXT,
    confidence      FLOAT       NOT NULL DEFAULT 1.0,
    affects_version TEXT        NOT NULL,
    fix_type        TEXT,
    suggested_code  TEXT,
    auto_applicable BOOLEAN     NOT NULL DEFAULT FALSE,
    status          TEXT        NOT NULL DEFAULT 'OPEN',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS anneal.code_embeddings (
    id              BIGSERIAL PRIMARY KEY,
    scan_id         TEXT        NOT NULL,
    file_path       TEXT        NOT NULL,
    chunk_content   TEXT        NOT NULL,
    embedding       vector(384),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS findings_scan_id_idx
    ON anneal.findings (scan_id);

CREATE INDEX IF NOT EXISTS findings_severity_idx
    ON anneal.findings (severity);

CREATE INDEX IF NOT EXISTS code_embeddings_scan_id_idx
    ON anneal.code_embeddings (scan_id);

CREATE INDEX IF NOT EXISTS code_embeddings_embedding_idx
    ON anneal.code_embeddings
    USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);

CREATE SEQUENCE IF NOT EXISTS scan_results_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE IF NOT EXISTS findings_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE IF NOT EXISTS code_embeddings_seq START WITH 1 INCREMENT BY 50;
