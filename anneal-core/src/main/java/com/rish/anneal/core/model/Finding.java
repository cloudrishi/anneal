package com.rish.anneal.core.model;

import lombok.Builder;
import lombok.Value;

/**
 * A single rule violation detected during a scan.
 * Immutable — created by the rule engine, never modified.
 */
@Value
@Builder
public class Finding {

    /** Unique identifier for this finding within a scan. */
    String findingId;

    /** The rule that produced this finding. */
    String ruleId;

    /** Human-readable rule name for display. */
    String ruleName;

    /** Category of the migration concern. */
    RuleCategory category;

    /** How severe this finding is. */
    Severity severity;

    /** How much effort is required to fix this finding. */
    Effort effort;

    /** Absolute path to the file where the finding was detected. */
    String filePath;

    /** Line number in the file where the finding was detected. Zero if unknown. */
    int lineNumber;

    /** The original source code snippet that triggered the finding. */
    String originalCode;

    /** Human-readable description of what was detected and why it is a problem. */
    String description;

    /** How confident the rule engine is in this finding. 0.0 to 1.0. */
    float confidence;

    /** The Java version boundary this finding applies to. */
    JavaVersion affectsVersion;

    /** The suggested fix for this finding. Null if no fix is available. */
    FixSuggestion fixSuggestion;

    /** Whether the developer has accepted, rejected, or deferred this finding. */
    @Builder.Default
    FindingStatus status = FindingStatus.OPEN;

    public enum FindingStatus {
        OPEN,
        ACCEPTED,
        REJECTED,
        DEFERRED
    }
}
