package com.rish.anneal.core.rule;

import com.rish.anneal.core.model.*;

import java.util.List;

/**
 * Rules for the 8 → 9 JPMS boundary — the highest risk migration step.
 * <p>
 * Java 9 introduced the Java Platform Module System (Project Jigsaw), which
 * encapsulates JDK internal APIs that were previously accessible on the classpath.
 * Libraries and code relying on sun.*, com.sun.*, or reflective access to
 * private JDK internals will break at this boundary.
 */
public class JpmsRules {

    public List<MigrationRule> rules() {
        return List.of(
                internalApiImport(),
                unsafeUsage(),
                illegalReflectiveAccess(),
                comSunImport()
        );
    }

    /**
     * Detects imports of sun.* packages — internal JDK APIs that are
     * encapsulated by JPMS and inaccessible from unnamed modules by default.
     */
    private MigrationRule internalApiImport() {
        return MigrationRule.builder()
                .ruleId("JPMS_SUN_IMPORT")
                .name("Internal sun.* API import")
                .category(RuleCategory.JPMS)
                .severity(Severity.BREAKING)
                .effort(Effort.HIGH)
                .introducedIn(JavaVersion.V9)
                .patterns(List.of(
                        DetectionPattern.builder()
                                .type(PatternType.IMPORT)
                                .matcher("sun.*")
                                .confidence(1.0f)
                                .build()
                ))
                .fixTemplate(FixSuggestion.builder()
                        .fixType(FixType.MANUAL)
                        .originalCode("import sun.*;")
                        .suggestedCode("// Replace with the public API equivalent.\n" +
                                "// Use --add-exports as a temporary workaround only.")
                        .autoApplicable(false)
                        .build())
                .referenceUrl("https://openjdk.org/jeps/261")
                .build();
    }

    /**
     * Detects direct use of sun.misc.Unsafe — a commonly used internal API
     * that is encapsulated in Java 9 and scheduled for removal.
     */
    private MigrationRule unsafeUsage() {
        return MigrationRule.builder()
                .ruleId("JPMS_UNSAFE_USAGE")
                .name("sun.misc.Unsafe usage")
                .category(RuleCategory.JPMS)
                .severity(Severity.BREAKING)
                .effort(Effort.HIGH)
                .introducedIn(JavaVersion.V9)
                .patterns(List.of(
                        DetectionPattern.builder()
                                .type(PatternType.IMPORT)
                                .matcher("sun.misc.Unsafe")
                                .confidence(1.0f)
                                .build(),
                        DetectionPattern.builder()
                                .type(PatternType.API_CALL)
                                .matcher("sun.misc.Unsafe#getUnsafe()")
                                .confidence(1.0f)
                                .build()
                ))
                .fixTemplate(FixSuggestion.builder()
                        .fixType(FixType.REFACTOR)
                        .originalCode("Unsafe unsafe = Unsafe.getUnsafe();")
                        .suggestedCode("// Java 9+: Use VarHandle for atomic field access\n" +
                                "// Java 22+: Use java.lang.foreign.MemorySegment for off-heap memory")
                        .autoApplicable(false)
                        .build())
                .referenceUrl("https://openjdk.org/jeps/193")
                .build();
    }

    /**
     * Detects reflective access to private JDK fields or methods.
     * setAccessible(true) on JDK types requires --add-opens in Java 9+
     * and will be blocked entirely in a future release.
     */
    private MigrationRule illegalReflectiveAccess() {
        return MigrationRule.builder()
                .ruleId("JPMS_ILLEGAL_REFLECTIVE_ACCESS")
                .name("Illegal reflective access to JDK internals")
                .category(RuleCategory.JPMS)
                .severity(Severity.BREAKING)
                .effort(Effort.HIGH)
                .introducedIn(JavaVersion.V9)
                .patterns(List.of(
                        DetectionPattern.builder()
                                .type(PatternType.REFLECTION)
                                .matcher("setAccessible")
                                .confidence(0.7f)
                                .build()
                ))
                .fixTemplate(FixSuggestion.builder()
                        .fixType(FixType.MANUAL)
                        .originalCode("field.setAccessible(true);")
                        .suggestedCode("// Add --add-opens as a temporary workaround:\n" +
                                "// --add-opens java.base/java.lang=ALL-UNNAMED\n" +
                                "// Long-term: eliminate reflection on JDK internals.")
                        .autoApplicable(false)
                        .build())
                .referenceUrl("https://openjdk.org/jeps/261")
                .build();
    }

    /**
     * Detects imports of com.sun.* packages — internal JDK implementation
     * classes that are not part of the public API and encapsulated by JPMS.
     */
    private MigrationRule comSunImport() {
        return MigrationRule.builder()
                .ruleId("JPMS_COM_SUN_IMPORT")
                .name("Internal com.sun.* API import")
                .category(RuleCategory.JPMS)
                .severity(Severity.BREAKING)
                .effort(Effort.HIGH)
                .introducedIn(JavaVersion.V9)
                .patterns(List.of(
                        DetectionPattern.builder()
                                .type(PatternType.IMPORT)
                                .matcher("com.sun.*")
                                .confidence(1.0f)
                                .build()
                ))
                .fixTemplate(FixSuggestion.builder()
                        .fixType(FixType.MANUAL)
                        .originalCode("import com.sun.*;")
                        .suggestedCode("// Replace with the public API equivalent.\n" +
                                "// Check https://docs.oracle.com for the supported replacement.")
                        .autoApplicable(false)
                        .build())
                .referenceUrl("https://openjdk.org/jeps/261")
                .build();
    }
}
