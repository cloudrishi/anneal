package com.rish.anneal.core.model;

public enum Effort {

    /**
     * Mechanical change — import swap, annotation rename.
     * Can be applied in under 5 minutes with no risk.
     */
    TRIVIAL,

    /**
     * Simple change with low risk — API replacement, dependency coordinate update.
     * Under 30 minutes, well-understood pattern.
     */
    LOW,

    /**
     * Moderate refactoring — method replacement, introducing a new Java feature.
     * 30 minutes to a few hours, requires testing.
     */
    MEDIUM,

    /**
     * Significant refactoring — concurrency model change, module system restructure.
     * Multiple hours, requires design thought and thorough testing.
     */
    HIGH,

    /**
     * Cannot be automated or reliably suggested.
     * Requires human judgment — architectural decision, business logic understanding.
     */
    MANUAL
}
