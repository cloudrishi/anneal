package com.rish.anneal.llm.model;

/**
 * Result of a single LLM enrichment call.
 *
 * <p>{@code modelUsed} carries the raw model name string (e.g. "codellama:13b",
 * "llama3.1:8b", "claude-sonnet-4-6") so the API layer can surface it in
 * {@code FindingDto} and the UI can render a "via &lt;model&gt;" attribution label
 * without hardcoding model names in the frontend.
 *
 * <p>This record is never persisted — it is runtime-only.
 * History retrieval returns {@code llmExplanation: null} intentionally.
 */
public record EnrichedFix(
        String findingId,
        String explanation,
        LlmModel model
) {
}
