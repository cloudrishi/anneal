package com.rish.anneal.core.model;

import lombok.Builder;
import lombok.Value;

/**
 * A suggested fix for a detected finding.
 * The rule engine populates fixType, originalCode, and a static suggestedCode template.
 * The LLM layer in anneal-llm enriches explanation and may refine suggestedCode
 * based on the actual context of the finding.
 */
@Value
@Builder
public class FixSuggestion {

    /** The type of fix — drives how the UI presents it and whether it can be auto-applied. */
    FixType fixType;

    /** The original code snippet that needs to change. */
    String originalCode;

    /**
     * The suggested replacement code.
     * For TRIVIAL and LOW effort fixes this is a concrete replacement.
     * For MANUAL fixes this may be a pattern or pseudocode guide.
     */
    String suggestedCode;

    /**
     * Natural language explanation of why this change is needed and what it does.
     * Populated by the LLM layer — empty string if LLM is not available.
     */
    @Builder.Default
    String explanation = "";

    /**
     * Whether the tool can apply this fix without developer review.
     * true only for mechanical changes with no ambiguity:
     *   IMPORT_REPLACE, ADD_DEPENDENCY, and simple API_REPLACE.
     * false for REFACTOR, MODULE_INFO, and MANUAL.
     */
    boolean autoApplicable;
}
