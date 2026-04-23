-- V4 — Create finding_embeddings table for per-finding vector embeddings
--
-- Separate from code_embeddings (file-chunk RAG — reserved for future use).
-- This table is one row per Finding, keyed by finding_id.
--
-- Vector: 384-dim AllMiniLmL6V2, cosine similarity via <=> operator.
-- Requires pgvector extension — available in pgvector/pgvector:pg16 (dev + CI).

CREATE TABLE IF NOT EXISTS anneal.finding_embeddings (
    id              BIGINT          NOT NULL DEFAULT nextval('code_embeddings_seq'),
    finding_id      VARCHAR(255)    NOT NULL UNIQUE,
    scan_id         VARCHAR(255)    NOT NULL,
    rule_id         VARCHAR(255)    NOT NULL,
    embedding       vector(384)     NOT NULL,
    embedded_text   TEXT,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    PRIMARY KEY (id)
);

-- IVFFlat cosine index — accelerates <=> queries at scale
-- lists=100 is appropriate for up to ~1M rows; tune upward beyond that
CREATE INDEX IF NOT EXISTS idx_finding_embeddings_vector
    ON anneal.finding_embeddings
    USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);

-- Scan-level lookup — bulk retrieval for cross-scan similarity
CREATE INDEX IF NOT EXISTS idx_finding_embeddings_scan_id
    ON anneal.finding_embeddings (scan_id);
