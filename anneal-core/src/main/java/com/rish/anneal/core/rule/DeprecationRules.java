package com.rish.anneal.core.rule;

import com.rish.anneal.core.model.*;

import java.util.List;

/**
 * Rules for deprecated APIs scheduled for removal.
 * Includes SecurityManager, finalize(), and other deprecations
 * introduced across the 11 → 17 → 21 → 25 boundaries.
 */
public class DeprecationRules {

    public List<MigrationRule> rules() {
        return List.of(
                securityManagerDeprecated(),
                finalizeDeprecated(),
                appletApiDeprecated()
        );
    }

    private MigrationRule securityManagerDeprecated() {
        return MigrationRule.builder()
                .ruleId("DEPRECATION_SECURITY_MANAGER")
                .name("SecurityManager deprecated for removal")
                .category(RuleCategory.DEPRECATION)
                .severity(Severity.DEPRECATED)
                .effort(Effort.HIGH)
                .introducedIn(JavaVersion.V17)
                .patterns(List.of(
                        DetectionPattern.builder()
                                .type(PatternType.API_CALL)
                                .matcher("java.lang.System#setSecurityManager()")
                                .confidence(1.0f)
                                .build(),
                        DetectionPattern.builder()
                                .type(PatternType.API_CALL)
                                .matcher("java.lang.System#getSecurityManager()")
                                .confidence(1.0f)
                                .build(),
                        DetectionPattern.builder()
                                .type(PatternType.IMPORT)
                                .matcher("java.lang.SecurityManager")
                                .confidence(1.0f)
                                .build()
                ))
                .fixTemplate(FixSuggestion.builder()
                        .fixType(FixType.MANUAL)
                        .originalCode("System.setSecurityManager(new CustomSecurityManager());")
                        .suggestedCode("""
                                // SecurityManager is removed — use modern alternatives:
                                // - Module system (JPMS) for encapsulation
                                // - Java agents for monitoring
                                // - OS-level sandboxing for isolation
                                // See: https://openjdk.org/jeps/411
                                """)
                        .autoApplicable(false)
                        .build())
                .referenceUrl("https://openjdk.org/jeps/411")
                .build();
    }

    private MigrationRule finalizeDeprecated() {
        return MigrationRule.builder()
                .ruleId("DEPRECATION_FINALIZE")
                .name("Object.finalize() deprecated for removal")
                .category(RuleCategory.DEPRECATION)
                .severity(Severity.DEPRECATED)
                .effort(Effort.MEDIUM)
                .introducedIn(JavaVersion.V17)
                .patterns(List.of(
                        DetectionPattern.builder()
                                .type(PatternType.AST_NODE)
                                .matcher("finalize")
                                .nodeType("MethodDeclaration")
                                .confidence(0.9f)
                                .build()
                ))
                .fixTemplate(FixSuggestion.builder()
                        .fixType(FixType.REFACTOR)
                        .originalCode("""
                                @Override
                                protected void finalize() throws Throwable {
                                    // cleanup
                                }
                                """)
                        .suggestedCode("""
                                // Use Cleaner API (Java 9+) instead of finalize():
                                private static final Cleaner cleaner = Cleaner.create();
                                private final Cleaner.Cleanable cleanable;
                                
                                public MyResource() {
                                    this.cleanable = cleaner.register(this, new CleanupAction());
                                }
                                
                                private static class CleanupAction implements Runnable {
                                    @Override
                                    public void run() {
                                        // cleanup logic here
                                    }
                                }
                                """)
                        .autoApplicable(false)
                        .build())
                .referenceUrl("https://openjdk.org/jeps/421")
                .build();
    }

    private MigrationRule appletApiDeprecated() {
        return MigrationRule.builder()
                .ruleId("DEPRECATION_APPLET_API")
                .name("Applet API removed in Java 25")
                .category(RuleCategory.DEPRECATION)
                .severity(Severity.BREAKING)
                .effort(Effort.HIGH)
                .introducedIn(JavaVersion.V25)
                .patterns(List.of(
                        DetectionPattern.builder()
                                .type(PatternType.IMPORT)
                                .matcher("java.applet.*")
                                .confidence(1.0f)
                                .build()
                ))
                .fixTemplate(FixSuggestion.builder()
                        .fixType(FixType.MANUAL)
                        .originalCode("import java.applet.*;")
                        .suggestedCode("""
                                // Applet API is removed in Java 25.
                                // Migrate to a desktop application framework:
                                // - JavaFX for rich client applications
                                // - Swing for simpler desktop UIs
                                // - Web technologies for browser-based delivery
                                """)
                        .autoApplicable(false)
                        .build())
                .referenceUrl("https://openjdk.org/jeps/492")
                .build();
    }
}
