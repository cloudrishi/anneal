package com.rish.anneal.core.rule;

import com.rish.anneal.core.model.*;
import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * A single migration rule — defines what to detect and what to suggest.
 * Rules are code-driven, not data-driven. Each rule is a Java class
 * registered with the RuleRegistry.
 * <p>
 * Rules are immutable and stateless. The rule engine applies them to AST nodes.
 */
@Value
@Builder
public class MigrationRule {

    /**
     * Human-readable unique key used in reports, CI output, and suppression annotations.
     * Convention: CATEGORY_DESCRIPTION in SCREAMING_SNAKE_CASE.
     * Examples: JPMS_INTERNAL_API, API_JAXB_REMOVED, CONCURRENCY_THREAD_VIRTUAL
     */
    String ruleId;

    /**
     * Display name for the rule.
     */
    String name;

    /**
     * Category of migration concern.
     */
    RuleCategory category;

    /**
     * Severity of violations detected by this rule.
     */
    Severity severity;

    /**
     * Estimated effort to fix violations of this rule.
     */
    Effort effort;

    /**
     * The Java version where this rule's concern is introduced.
     * e.g. JPMS rules apply from V9 onwards.
     */
    JavaVersion introducedIn;

    /**
     * The Java version where this rule's concern becomes a hard break.
     * e.g. Thread.stop() was deprecated in V9, removed in V21.
     * Null if the concern is already a hard break at introducedIn.
     */
    JavaVersion removedIn;

    /**
     * One or more patterns the rule engine uses to detect violations.
     * Any match triggers a finding.
     */
    List<DetectionPattern> patterns;

    /**
     * A template fix suggestion for violations of this rule.
     * The LLM layer may enrich this with context-specific code.
     */
    FixSuggestion fixTemplate;

    /**
     * Link to official Java migration guide or JEP for this rule.
     * Shown in the migration report.
     */
    String referenceUrl;

    /**
     * Whether this rule applies for the given source and target version boundary.
     * A rule applies when its introducedIn version falls within (source, target] —
     * i.e. the concern was introduced after the source version and at or before the target.
     */
    public boolean appliesTo(JavaVersion source, JavaVersion target) {
        return introducedIn.isNewerThan(source) && !introducedIn.isNewerThan(target);
    }
}
