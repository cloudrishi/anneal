-- V3 — Add reference_url to findings
-- Denormalized from MigrationRule at scan time.
-- Nullable — existing rows retain NULL, new scans populate the column.

ALTER TABLE anneal.findings
    ADD COLUMN IF NOT EXISTS reference_url VARCHAR(512);
