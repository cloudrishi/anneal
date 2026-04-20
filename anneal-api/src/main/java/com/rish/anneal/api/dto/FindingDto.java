package com.rish.anneal.api.dto;

/**
 * DTO representation of a Finding for the REST API response.
 * Snake_case field names match the frontend convention.
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
        String referenceUrl
) {}
