package com.rish.anneal.llm.model;

/**
 * Result of LLM enrichment for a single finding.
 * Carries the generated explanation and metadata — does not mutate the Finding.
 *
 * @param findingId   the finding this enrichment applies to
 * @param explanation LLM-generated rationale — why the fix is correct and what it achieves
 * @param modelUsed   model that produced this explanation e.g. codellama:13b
 * @param latencyMs   time taken for the LLM call in milliseconds
 */
public record EnrichedFix(
        String findingId,
        String explanation,
        String modelUsed,
        long latencyMs
) {}
