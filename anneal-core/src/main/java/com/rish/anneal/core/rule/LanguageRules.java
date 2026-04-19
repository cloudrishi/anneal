package com.rish.anneal.core.rule;

import com.rish.anneal.core.model.DetectionPattern;
import com.rish.anneal.core.model.Effort;
import com.rish.anneal.core.model.FixSuggestion;
import com.rish.anneal.core.model.FixType;
import com.rish.anneal.core.model.JavaVersion;
import com.rish.anneal.core.model.MigrationRule;
import com.rish.anneal.core.model.PatternType;
import com.rish.anneal.core.model.RuleCategory;
import com.rish.anneal.core.model.Severity;

import java.util.List;

/**
 * Language modernization rules — not risks, opportunities.
 * These surface Java 21/25 idioms that improve readability,
 * reduce boilerplate, and signal modern Java fluency.
 */
public class LanguageRules {

    public List<MigrationRule> rules() {
        return List.of(
                anonymousClassToLambda(),
                oldDateTimeApi(),
                instanceofPatternMatching(),
                recordOpportunity()
        );
    }

    private MigrationRule anonymousClassToLambda() {
        return MigrationRule.builder()
                .ruleId("LANGUAGE_ANONYMOUS_CLASS_LAMBDA")
                .name("Anonymous class can be replaced with lambda")
                .category(RuleCategory.LANGUAGE)
                .severity(Severity.MODERNIZATION)
                .effort(Effort.TRIVIAL)
                .introducedIn(JavaVersion.V8)
                .patterns(List.of(
                        DetectionPattern.builder()
                                .type(PatternType.AST_NODE)
                                .matcher("AnonymousClassBody")
                                .nodeType("ObjectCreationExpr")
                                .confidence(0.7f)
                                .build()
                ))
                .fixTemplate(FixSuggestion.builder()
                        .fixType(FixType.REFACTOR)
                        .originalCode("""
                                Runnable r = new Runnable() {
                                    @Override
                                    public void run() {
                                        doSomething();
                                    }
                                };
                                """)
                        .suggestedCode("Runnable r = () -> doSomething();")
                        .autoApplicable(false)
                        .build())
                .referenceUrl("https://docs.oracle.com/javase/tutorial/java/javaOO/lambdaexpressions.html")
                .build();
    }

    private MigrationRule oldDateTimeApi() {
        return MigrationRule.builder()
                .ruleId("LANGUAGE_OLD_DATETIME_API")
                .name("Legacy Date/Calendar API — use java.time")
                .category(RuleCategory.LANGUAGE)
                .severity(Severity.MODERNIZATION)
                .effort(Effort.LOW)
                .introducedIn(JavaVersion.V8)
                .patterns(List.of(
                        DetectionPattern.builder()
                                .type(PatternType.IMPORT)
                                .matcher("java.util.Date")
                                .confidence(0.8f)
                                .build(),
                        DetectionPattern.builder()
                                .type(PatternType.IMPORT)
                                .matcher("java.util.Calendar")
                                .confidence(0.9f)
                                .build(),
                        DetectionPattern.builder()
                                .type(PatternType.IMPORT)
                                .matcher("java.text.SimpleDateFormat")
                                .confidence(0.9f)
                                .build()
                ))
                .fixTemplate(FixSuggestion.builder()
                        .fixType(FixType.API_REPLACE)
                        .originalCode("new Date(), Calendar.getInstance(), new SimpleDateFormat()")
                        .suggestedCode("""
                                // Replace with java.time equivalents:
                                // new Date()              → LocalDate.now() / Instant.now()
                                // Calendar.getInstance()  → ZonedDateTime.now()
                                // new SimpleDateFormat()  → DateTimeFormatter
                                import java.time.LocalDate;
                                import java.time.ZonedDateTime;
                                import java.time.format.DateTimeFormatter;
                                """)
                        .autoApplicable(false)
                        .build())
                .referenceUrl("https://docs.oracle.com/javase/8/docs/api/java/time/package-summary.html")
                .build();
    }

    private MigrationRule instanceofPatternMatching() {
        return MigrationRule.builder()
                .ruleId("LANGUAGE_INSTANCEOF_CAST")
                .name("instanceof check followed by cast — use pattern matching")
                .category(RuleCategory.LANGUAGE)
                .severity(Severity.MODERNIZATION)
                .effort(Effort.TRIVIAL)
                .introducedIn(JavaVersion.V17)
                .patterns(List.of(
                        DetectionPattern.builder()
                                .type(PatternType.AST_NODE)
                                .matcher("InstanceOfExpr")
                                .nodeType("InstanceOfExpr")
                                .confidence(0.6f)
                                .build()
                ))
                .fixTemplate(FixSuggestion.builder()
                        .fixType(FixType.REFACTOR)
                        .originalCode("""
                                if (obj instanceof String) {
                                    String s = (String) obj;
                                    s.toLowerCase();
                                }
                                """)
                        .suggestedCode("""
                                if (obj instanceof String s) {
                                    s.toLowerCase();
                                }
                                """)
                        .autoApplicable(false)
                        .build())
                .referenceUrl("https://openjdk.org/jeps/441")
                .build();
    }

    private MigrationRule recordOpportunity() {
        return MigrationRule.builder()
                .ruleId("LANGUAGE_RECORD_OPPORTUNITY")
                .name("Immutable data class — consider record")
                .category(RuleCategory.LANGUAGE)
                .severity(Severity.MODERNIZATION)
                .effort(Effort.LOW)
                .introducedIn(JavaVersion.V17)
                .patterns(List.of(
                        DetectionPattern.builder()
                                .type(PatternType.AST_NODE)
                                .matcher("ClassDeclaration")
                                .nodeType("ClassDeclaration")
                                .confidence(0.5f)
                                .build()
                ))
                .fixTemplate(FixSuggestion.builder()
                        .fixType(FixType.REFACTOR)
                        .originalCode("""
                                public final class Point {
                                    private final int x;
                                    private final int y;
                                    // constructor, getters, equals, hashCode, toString...
                                }
                                """)
                        .suggestedCode("public record Point(int x, int y) {}")
                        .autoApplicable(false)
                        .build())
                .referenceUrl("https://openjdk.org/jeps/395")
                .build();
    }
}
