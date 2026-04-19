package com.rish.anneal.core.model;

public enum FixType {

    /**
     * Replace an import statement with a new one.
     * e.g. javax.xml.bind.* → jakarta.xml.bind.*
     * autoApplicable: true
     */
    IMPORT_REPLACE,

    /**
     * Replace an API call with its modern equivalent.
     * e.g. new Date() → LocalDate.now()
     * autoApplicable: true for simple cases, false for complex
     */
    API_REPLACE,

    /**
     * Non-trivial refactoring of existing code.
     * e.g. anonymous class → lambda, Thread → virtual thread
     * autoApplicable: false — requires developer review
     */
    REFACTOR,

    /**
     * Add a missing Maven dependency.
     * e.g. jakarta.xml.bind-api when JAXB is removed from JDK
     * autoApplicable: true
     */
    ADD_DEPENDENCY,

    /**
     * Generate or update module-info.java.
     * Always requires developer review — incorrect module descriptors break the build.
     * autoApplicable: false
     */
    MODULE_INFO,

    /**
     * No automated fix available.
     * Requires human judgment — architectural decision or business logic understanding.
     * autoApplicable: false
     */
    MANUAL
}
