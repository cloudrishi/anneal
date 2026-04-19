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
 * Rules for APIs removed from the JDK.
 * Primary boundary: 9 → 11 (Java EE module removal).
 * Secondary: 17 → 21, 21 → 25 (finalization, Thread methods).
 */
public class ApiRemovalRules {

    public List<MigrationRule> rules() {
        return List.of(
                jaxbRemoved(),
                javaxActivationRemoved(),
                jaxWsRemoved(),
                javaxAnnotationRemoved(),
                threadStopRemoved()
        );
    }

    private MigrationRule jaxbRemoved() {
        return MigrationRule.builder()
                .ruleId("API_JAXB_REMOVED")
                .name("JAXB removed from JDK")
                .category(RuleCategory.API_REMOVAL)
                .severity(Severity.BREAKING)
                .effort(Effort.LOW)
                .introducedIn(JavaVersion.V11)
                .patterns(List.of(
                        DetectionPattern.builder()
                                .type(PatternType.IMPORT)
                                .matcher("javax.xml.bind.*")
                                .confidence(1.0f)
                                .build()
                ))
                .fixTemplate(FixSuggestion.builder()
                        .fixType(FixType.ADD_DEPENDENCY)
                        .originalCode("import javax.xml.bind.*;")
                        .suggestedCode("""
                                <!-- Add to pom.xml -->
                                <dependency>
                                    <groupId>jakarta.xml.bind</groupId>
                                    <artifactId>jakarta.xml.bind-api</artifactId>
                                    <version>4.0.2</version>
                                </dependency>
                                <dependency>
                                    <groupId>com.sun.xml.bind</groupId>
                                    <artifactId>jaxb-impl</artifactId>
                                    <version>4.0.5</version>
                                    <scope>runtime</scope>
                                </dependency>
                                """)
                        .autoApplicable(true)
                        .build())
                .referenceUrl("https://openjdk.org/jeps/320")
                .build();
    }

    private MigrationRule javaxActivationRemoved() {
        return MigrationRule.builder()
                .ruleId("API_JAVAX_ACTIVATION_REMOVED")
                .name("javax.activation removed from JDK")
                .category(RuleCategory.API_REMOVAL)
                .severity(Severity.BREAKING)
                .effort(Effort.LOW)
                .introducedIn(JavaVersion.V11)
                .patterns(List.of(
                        DetectionPattern.builder()
                                .type(PatternType.IMPORT)
                                .matcher("javax.activation.*")
                                .confidence(1.0f)
                                .build()
                ))
                .fixTemplate(FixSuggestion.builder()
                        .fixType(FixType.ADD_DEPENDENCY)
                        .originalCode("import javax.activation.*;")
                        .suggestedCode("""
                                <!-- Add to pom.xml -->
                                <dependency>
                                    <groupId>jakarta.activation</groupId>
                                    <artifactId>jakarta.activation-api</artifactId>
                                    <version>2.1.3</version>
                                </dependency>
                                """)
                        .autoApplicable(true)
                        .build())
                .referenceUrl("https://openjdk.org/jeps/320")
                .build();
    }

    private MigrationRule jaxWsRemoved() {
        return MigrationRule.builder()
                .ruleId("API_JAX_WS_REMOVED")
                .name("JAX-WS removed from JDK")
                .category(RuleCategory.API_REMOVAL)
                .severity(Severity.BREAKING)
                .effort(Effort.LOW)
                .introducedIn(JavaVersion.V11)
                .patterns(List.of(
                        DetectionPattern.builder()
                                .type(PatternType.IMPORT)
                                .matcher("javax.xml.ws.*")
                                .confidence(1.0f)
                                .build()
                ))
                .fixTemplate(FixSuggestion.builder()
                        .fixType(FixType.ADD_DEPENDENCY)
                        .originalCode("import javax.xml.ws.*;")
                        .suggestedCode("""
                                <!-- Add to pom.xml -->
                                <dependency>
                                    <groupId>jakarta.xml.ws</groupId>
                                    <artifactId>jakarta.xml.ws-api</artifactId>
                                    <version>4.0.2</version>
                                </dependency>
                                """)
                        .autoApplicable(true)
                        .build())
                .referenceUrl("https://openjdk.org/jeps/320")
                .build();
    }

    private MigrationRule javaxAnnotationRemoved() {
        return MigrationRule.builder()
                .ruleId("API_JAVAX_ANNOTATION_REMOVED")
                .name("javax.annotation removed from JDK")
                .category(RuleCategory.API_REMOVAL)
                .severity(Severity.BREAKING)
                .effort(Effort.LOW)
                .introducedIn(JavaVersion.V11)
                .patterns(List.of(
                        DetectionPattern.builder()
                                .type(PatternType.IMPORT)
                                .matcher("javax.annotation.*")
                                .confidence(0.8f)
                                .build()
                ))
                .fixTemplate(FixSuggestion.builder()
                        .fixType(FixType.ADD_DEPENDENCY)
                        .originalCode("import javax.annotation.*;")
                        .suggestedCode("""
                                <!-- Add to pom.xml -->
                                <dependency>
                                    <groupId>jakarta.annotation</groupId>
                                    <artifactId>jakarta.annotation-api</artifactId>
                                    <version>3.0.0</version>
                                </dependency>
                                """)
                        .autoApplicable(true)
                        .build())
                .referenceUrl("https://openjdk.org/jeps/320")
                .build();
    }

    private MigrationRule threadStopRemoved() {
        return MigrationRule.builder()
                .ruleId("API_THREAD_STOP_REMOVED")
                .name("Thread.stop() removed")
                .category(RuleCategory.API_REMOVAL)
                .severity(Severity.BREAKING)
                .effort(Effort.MEDIUM)
                .introducedIn(JavaVersion.V21)
                .patterns(List.of(
                        DetectionPattern.builder()
                                .type(PatternType.API_CALL)
                                .matcher("java.lang.Thread#stop()")
                                .confidence(1.0f)
                                .build(),
                        DetectionPattern.builder()
                                .type(PatternType.API_CALL)
                                .matcher("java.lang.Thread#suspend()")
                                .confidence(1.0f)
                                .build(),
                        DetectionPattern.builder()
                                .type(PatternType.API_CALL)
                                .matcher("java.lang.Thread#resume()")
                                .confidence(1.0f)
                                .build()
                ))
                .fixTemplate(FixSuggestion.builder()
                        .fixType(FixType.REFACTOR)
                        .originalCode("thread.stop();")
                        .suggestedCode("""
                                // Use cooperative cancellation with a volatile flag or interrupt:
                                thread.interrupt();
                                // Or use structured concurrency (Java 25):
                                try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
                                    scope.fork(() -> yourTask());
                                    scope.join();
                                }
                                """)
                        .autoApplicable(false)
                        .build())
                .referenceUrl("https://openjdk.org/jeps/453")
                .build();
    }
}
