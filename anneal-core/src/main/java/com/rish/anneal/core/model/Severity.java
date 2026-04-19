package com.rish.anneal.core.model;

public enum Severity {

    /**
     * Will break compilation or runtime behaviour when migrating.
     * Must be resolved before the migration can proceed.
     */
    BREAKING,

    /**
     * API or feature is deprecated and scheduled for removal in a future version.
     * Should be resolved but will not immediately break the build.
     */
    DEPRECATED,

    /**
     * Not a risk — an opportunity to adopt a modern Java idiom.
     * Improves readability, performance, or maintainability.
     */
    MODERNIZATION
}
