package com.rish.anneal.core.model;

import lombok.Builder;
import lombok.Value;

/**
 * Describes how a MigrationRule detects a violation in source code.
 * Rules may have multiple patterns — any match triggers the finding.
 */
@Value
@Builder
public class DetectionPattern {

    /**
     * The type of pattern — drives which detection strategy is used
     * by the rule engine and AST visitor.
     */
    PatternType type;

    /**
     * The string matcher for this pattern.
     * Interpretation depends on PatternType:
     *   IMPORT      — fully qualified package or class, supports wildcards: sun.misc.*
     *   API_CALL    — fully qualified method signature: java.lang.Thread#stop()
     *   AST_NODE    — JavaParser node class name: MethodCallExpr
     *   ANNOTATION  — annotation type: java.lang.Override
     *   REFLECTION  — method being called reflectively: setAccessible
     *   BUILD       — property key in pom.xml: maven.compiler.source
     */
    String matcher;

    /**
     * JavaParser AST node class to match when type is AST_NODE.
     * Null for other pattern types.
     */
    String nodeType;

    /**
     * Confidence level for this pattern match. 0.0 to 1.0.
     * 1.0 — match is always a real violation (e.g. import sun.misc.Unsafe)
     * 0.7 — match is likely a violation but context matters
     * 0.5 — match may be a false positive — needs developer confirmation
     */
    @Builder.Default
    float confidence = 1.0f;
}
