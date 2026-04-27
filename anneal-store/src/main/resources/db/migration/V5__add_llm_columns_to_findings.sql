-- V5 — Add LLM enrichment columns to findings
--
-- llm_explanation, llm_provider, llm_model are runtime-enriched after scan completes.
-- All nullable — existing rows retain NULL, background enrichment populates them
-- as each finding is processed. History retrieval reads from these columns directly.
--
-- llm_provider: OLLAMA | ANTHROPIC
-- llm_model:    config-driven e.g. codellama:13b, llama3.1:8b, claude-sonnet-4-6

ALTER TABLE anneal.findings
    ADD COLUMN IF NOT EXISTS llm_explanation TEXT,
    ADD COLUMN IF NOT EXISTS llm_provider    VARCHAR(32),
    ADD COLUMN IF NOT EXISTS llm_model       VARCHAR(128);
