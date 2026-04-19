package com.rish.anneal.core.model;

public enum MigrationPhase {

    /**
     * Phase 1 — Read-only scan.
     * Detect source Java version, scan AST, produce findings and risk score.
     * No code changes occur in this phase.
     */
    ANALYSIS,

    /**
     * Phase 2 — Plan the incremental path.
     * Plot LTS-to-LTS stops, scope each boundary, estimate total effort.
     * Developer reviews and confirms before proceeding.
     */
    PLANNING,

    /**
     * Phase 3 — Apply fixes.
     * Per finding: show original code, suggested fix, LLM explanation.
     * Developer accepts or rejects each suggestion.
     * Tool tracks accepted/rejected/deferred state.
     */
    FIXING,

    /**
     * Phase 4 — Validate.
     * Re-scan after developer applies fixes.
     * Confirm resolved findings are cleared.
     * Track progress and generate SARIF report for CI.
     */
    VALIDATION
}
