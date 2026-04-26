package com.rish.anneal.api.dto;

import com.rish.anneal.api.model.LlmProvider;

/**
 * API representation of a single migration finding, returned by POST /api/scan
 * and GET /api/scans/{scanId}.
 *
 * <p>{@code llmExplanation}, {@code llmProvider}, and {@code llmModel} are
 * runtime-only — they are populated during the scan response and are {@code null}
 * on history retrieval (GET /api/scans/{scanId}). They are never persisted.
 *
 * @param findingId      unique identifier for this finding
 * @param ruleId         rule that produced this finding e.g. JPMS_SUN_IMPORT
 * @param ruleName       human-readable rule name
 * @param category       JPMS · API_REMOVAL · DEPRECATION · LANGUAGE · CONCURRENCY · BUILD
 * @param severity       BREAKING · DEPRECATED · MODERNIZATION
 * @param effort         TRIVIAL · LOW · MEDIUM · HIGH · MANUAL
 * @param filePath       absolute path to the affected file
 * @param lineNumber     line where the finding was detected
 * @param originalCode   the code snippet that triggered the finding
 * @param description    what was detected and why it is a problem
 * @param confidence     0.0 to 1.0 — how certain the detection is
 * @param affectsVersion Java version where this concern is introduced
 * @param fixType        type of fix available
 * @param suggestedCode  suggested replacement code or guidance
 * @param autoApplicable whether the fix can be applied without developer review
 * @param status         OPEN · ACCEPTED · REJECTED · DEFERRED
 * @param referenceUrl   link to JEP or migration guide
 * @param llmExplanation LLM-generated rationale for the fix — null until enriched
 * @param llmProvider    which provider produced the explanation — OLLAMA · ANTHROPIC · null
 * @param llmModel       model name as configured e.g. codellama:13b — null until enriched
 */
public record FindingDto(
        String findingId,
        String ruleId,
        String ruleName,
        String category,
        String severity,
        String effort,
        String filePath,
        int lineNumber,
        String originalCode,
        String description,
        float confidence,
        String affectsVersion,
        String fixType,
        String suggestedCode,
        boolean autoApplicable,
        String status,
        String referenceUrl,
        String llmExplanation,
        LlmProvider llmProvider,
        String llmModel
) {
}