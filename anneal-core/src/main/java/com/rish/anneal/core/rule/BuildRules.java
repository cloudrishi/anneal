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
 * Build configuration rules — pom.xml, build.gradle, JVM flags.
 * These are detected by scanning build files rather than Java source AST.
 * Low effort, high value — usually the first fixes applied.
 */
public class BuildRules {

    public List<MigrationRule> rules() {
        return List.of(
                compilerSourceTarget(),
                illegalAccessFlag(),
                javaxToJakartaCoordinates()
        );
    }

    private MigrationRule compilerSourceTarget() {
        return MigrationRule.builder()
                .ruleId("BUILD_COMPILER_SOURCE_TARGET")
                .name("maven.compiler.source/target below Java 25")
                .category(RuleCategory.BUILD)
                .severity(Severity.BREAKING)
                .effort(Effort.TRIVIAL)
                .introducedIn(JavaVersion.V9)
                .patterns(List.of(
                        DetectionPattern.builder()
                                .type(PatternType.BUILD)
                                .matcher("maven.compiler.source")
                                .confidence(1.0f)
                                .build(),
                        DetectionPattern.builder()
                                .type(PatternType.BUILD)
                                .matcher("maven.compiler.target")
                                .confidence(1.0f)
                                .build(),
                        DetectionPattern.builder()
                                .type(PatternType.BUILD)
                                .matcher("maven.compiler.release")
                                .confidence(1.0f)
                                .build()
                ))
                .fixTemplate(FixSuggestion.builder()
                        .fixType(FixType.IMPORT_REPLACE)
                        .originalCode("""
                                <properties>
                                    <maven.compiler.source>8</maven.compiler.source>
                                    <maven.compiler.target>8</maven.compiler.target>
                                </properties>
                                """)
                        .suggestedCode("""
                                <properties>
                                    <maven.compiler.release>25</maven.compiler.release>
                                </properties>
                                """)
                        .autoApplicable(true)
                        .build())
                .referenceUrl("https://maven.apache.org/plugins/maven-compiler-plugin/compile-mojo.html")
                .build();
    }

    private MigrationRule illegalAccessFlag() {
        return MigrationRule.builder()
                .ruleId("BUILD_ILLEGAL_ACCESS_FLAG")
                .name("--illegal-access JVM flag removed")
                .category(RuleCategory.BUILD)
                .severity(Severity.BREAKING)
                .effort(Effort.MEDIUM)
                .introducedIn(JavaVersion.V17)
                .patterns(List.of(
                        DetectionPattern.builder()
                                .type(PatternType.BUILD)
                                .matcher("--illegal-access")
                                .confidence(1.0f)
                                .build()
                ))
                .fixTemplate(FixSuggestion.builder()
                        .fixType(FixType.MANUAL)
                        .originalCode("--illegal-access=permit")
                        .suggestedCode("""
                                // --illegal-access removed in Java 17.
                                // Replace with specific --add-opens flags per package:
                                // --add-opens java.base/java.lang=ALL-UNNAMED
                                // --add-opens java.base/java.util=ALL-UNNAMED
                                // Long-term: eliminate reliance on internal APIs.
                                """)
                        .autoApplicable(false)
                        .build())
                .referenceUrl("https://openjdk.org/jeps/403")
                .build();
    }

    private MigrationRule javaxToJakartaCoordinates() {
        return MigrationRule.builder()
                .ruleId("BUILD_JAVAX_TO_JAKARTA_COORDS")
                .name("javax.* Maven coordinates — migrate to jakarta.*")
                .category(RuleCategory.BUILD)
                .severity(Severity.BREAKING)
                .effort(Effort.LOW)
                .introducedIn(JavaVersion.V11)
                .patterns(List.of(
                        DetectionPattern.builder()
                                .type(PatternType.BUILD)
                                .matcher("javax.servlet:javax.servlet-api")
                                .confidence(1.0f)
                                .build(),
                        DetectionPattern.builder()
                                .type(PatternType.BUILD)
                                .matcher("javax.persistence:javax.persistence-api")
                                .confidence(1.0f)
                                .build(),
                        DetectionPattern.builder()
                                .type(PatternType.BUILD)
                                .matcher("javax.validation:validation-api")
                                .confidence(1.0f)
                                .build()
                ))
                .fixTemplate(FixSuggestion.builder()
                        .fixType(FixType.IMPORT_REPLACE)
                        .originalCode("""
                                <dependency>
                                    <groupId>javax.servlet</groupId>
                                    <artifactId>javax.servlet-api</artifactId>
                                </dependency>
                                """)
                        .suggestedCode("""
                                <dependency>
                                    <groupId>jakarta.servlet</groupId>
                                    <artifactId>jakarta.servlet-api</artifactId>
                                    <version>6.1.0</version>
                                </dependency>
                                """)
                        .autoApplicable(true)
                        .build())
                .referenceUrl("https://jakarta.ee/specifications/")
                .build();
    }
}
