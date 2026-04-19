package com.rish.anneal.core.model;

public enum RuleCategory {

    /**
     * Java Platform Module System — internal API access, module encapsulation,
     * illegal reflective access, missing module-info.java.
     * Highest risk category — 8 → 9 boundary.
     */
    JPMS,

    /**
     * APIs removed from the JDK — Java EE modules (JAXB, JAX-WS, javax.activation),
     * deprecated methods that have been deleted, removed JVM flags.
     */
    API_REMOVAL,

    /**
     * APIs deprecated in the target version — scheduled for removal in a future release.
     * finalize(), SecurityManager, Thread.stop/suspend/resume.
     */
    DEPRECATION,

    /**
     * Language modernization opportunities — not risks, improvements.
     * Records, sealed classes, pattern matching, switch expressions, text blocks.
     */
    LANGUAGE,

    /**
     * Concurrency modernization — Thread to virtual threads, ThreadLocal to scoped values,
     * synchronized blocks to structured concurrency.
     */
    CONCURRENCY,

    /**
     * Build configuration — pom.xml source/target version, removed JVM flags,
     * dependency coordinate changes (javax → jakarta).
     */
    BUILD
}
