package com.rish.anneal.core.engine;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.rish.anneal.core.model.*;
import com.rish.anneal.core.rule.MigrationRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RuleEngineTest {

    private RuleEngine engine;

    @BeforeEach
    void setUp() {
        engine = new RuleEngine();
    }

    @Nested
    @DisplayName("IMPORT pattern matching")
    class ImportMatchingTests {

        @Test
        @DisplayName("detects exact import match")
        void detectsExactImport() {
            CompilationUnit cu = StaticJavaParser.parse("""
                    import sun.misc.Unsafe;
                    public class Test {}
                    """);

            List<Finding> findings = engine.apply(
                    cu, "/Test.java",
                    List.of(rule(PatternType.IMPORT, "sun.misc.Unsafe", 1.0f)),
                    JavaVersion.V8, JavaVersion.V25
            );

            assertThat(findings).hasSize(1);
            assertThat(findings.get(0).getRuleId()).isEqualTo("TEST_RULE");
            assertThat(findings.get(0).getLineNumber()).isEqualTo(1);
        }

        @Test
        @DisplayName("detects wildcard import match")
        void detectsWildcardImport() {
            CompilationUnit cu = StaticJavaParser.parse("""
                    import sun.misc.*;
                    public class Test {}
                    """);

            List<Finding> findings = engine.apply(
                    cu, "/Test.java",
                    List.of(rule(PatternType.IMPORT, "sun.*", 1.0f)),
                    JavaVersion.V8, JavaVersion.V25
            );

            assertThat(findings).hasSize(1);
        }

        @Test
        @DisplayName("detects javax.xml.bind import")
        void detectsJaxbImport() {
            CompilationUnit cu = StaticJavaParser.parse("""
                    import javax.xml.bind.JAXBContext;
                    public class Test {}
                    """);

            List<Finding> findings = engine.apply(
                    cu, "/Test.java",
                    List.of(rule(PatternType.IMPORT, "javax.xml.bind.*", 1.0f)),
                    JavaVersion.V8, JavaVersion.V25
            );

            assertThat(findings).hasSize(1);
            assertThat(findings.get(0).getOriginalCode()).contains("javax.xml.bind.JAXBContext");
        }

        @Test
        @DisplayName("no match when import is absent")
        void noMatchWhenImportAbsent() {
            CompilationUnit cu = StaticJavaParser.parse("""
                    import java.util.List;
                    public class Test {}
                    """);

            List<Finding> findings = engine.apply(
                    cu, "/Test.java",
                    List.of(rule(PatternType.IMPORT, "sun.*", 1.0f)),
                    JavaVersion.V8, JavaVersion.V25
            );

            assertThat(findings).isEmpty();
        }

        @Test
        @DisplayName("detects multiple imports from same wildcard")
        void detectsMultipleImports() {
            CompilationUnit cu = StaticJavaParser.parse("""
                    import sun.misc.Unsafe;
                    import sun.reflect.Reflection;
                    public class Test {}
                    """);

            List<Finding> findings = engine.apply(
                    cu, "/Test.java",
                    List.of(rule(PatternType.IMPORT, "sun.*", 1.0f)),
                    JavaVersion.V8, JavaVersion.V25
            );

            assertThat(findings).hasSize(2);
        }
    }

    @Nested
    @DisplayName("API_CALL pattern matching")
    class ApiCallMatchingTests {

        @Test
        @DisplayName("detects Thread.stop() call")
        void detectsThreadStop() {
            CompilationUnit cu = StaticJavaParser.parse("""
                    public class Test {
                        void m() {
                            Thread t = new Thread(() -> {});
                            t.stop();
                        }
                    }
                    """);

            List<Finding> findings = engine.apply(
                    cu, "/Test.java",
                    List.of(rule(PatternType.API_CALL, "java.lang.Thread#stop()", 1.0f)),
                    JavaVersion.V8, JavaVersion.V25
            );

            assertThat(findings).hasSize(1);
            assertThat(findings.get(0).getOriginalCode()).contains("stop");
        }

        @Test
        @DisplayName("detects setAccessible call")
        void detectsSetAccessible() {
            CompilationUnit cu = StaticJavaParser.parse("""
                    import java.lang.reflect.Field;
                    public class Test {
                        void m() throws Exception {
                            Field f = String.class.getDeclaredField("value");
                            f.setAccessible(true);
                        }
                    }
                    """);

            List<Finding> findings = engine.apply(
                    cu, "/Test.java",
                    List.of(rule(PatternType.REFLECTION, "setAccessible", 0.7f)),
                    JavaVersion.V8, JavaVersion.V25
            );

            assertThat(findings).hasSize(1);
            assertThat(findings.get(0).getConfidence()).isEqualTo(0.7f);
        }
    }

    @Nested
    @DisplayName("AST_NODE pattern matching")
    class AstNodeMatchingTests {

        @Test
        @DisplayName("detects finalize() method override")
        void detectsFinalizeMethod() {
            CompilationUnit cu = StaticJavaParser.parse("""
                    public class Test {
                        @Override
                        protected void finalize() throws Throwable {
                            super.finalize();
                        }
                    }
                    """);

            MigrationRule rule = MigrationRule.builder()
                    .ruleId("TEST_RULE")
                    .name("Test Rule")
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
                            .originalCode("")
                            .suggestedCode("")
                            .autoApplicable(false)
                            .build())
                    .referenceUrl("https://openjdk.org")
                    .build();

            List<Finding> findings = engine.apply(
                    cu, "/Test.java",
                    List.of(rule),
                    JavaVersion.V8, JavaVersion.V25
            );

            assertThat(findings).hasSize(1);
            assertThat(findings.get(0).getConfidence()).isEqualTo(0.9f);
        }
    }

    @Nested
    @DisplayName("Rule filtering")
    class RuleFilteringTests {

        @Test
        @DisplayName("skips rules that don't apply to version range")
        void skipsRulesOutsideVersionRange() {
            CompilationUnit cu = StaticJavaParser.parse("""
                    import sun.misc.Unsafe;
                    public class Test {}
                    """);

            // Rule introduced in V9, but we're scanning V17 → V25
            // V9 is not newer than V17, so appliesTo returns false
            List<Finding> findings = engine.apply(
                    cu, "/Test.java",
                    List.of(rule(PatternType.IMPORT, "sun.*", 1.0f)),
                    JavaVersion.V17, JavaVersion.V25
            );

            // JPMS rules apply to 8→9 boundary, not 17→25
            assertThat(findings).isEmpty();
        }

        @Test
        @DisplayName("returns empty for empty rule list")
        void returnsEmptyForEmptyRules() {
            CompilationUnit cu = StaticJavaParser.parse("""
                    import sun.misc.Unsafe;
                    public class Test {}
                    """);

            List<Finding> findings = engine.apply(
                    cu, "/Test.java",
                    List.of(),
                    JavaVersion.V8, JavaVersion.V25
            );

            assertThat(findings).isEmpty();
        }
    }

    // --- Helpers ---

    private MigrationRule rule(PatternType type, String matcher, float confidence) {
        return MigrationRule.builder()
                .ruleId("TEST_RULE")
                .name("Test Rule")
                .category(RuleCategory.JPMS)
                .severity(Severity.BREAKING)
                .effort(Effort.HIGH)
                .introducedIn(JavaVersion.V9)
                .patterns(List.of(
                        DetectionPattern.builder()
                                .type(type)
                                .matcher(matcher)
                                .confidence(confidence)
                                .build()
                ))
                .fixTemplate(FixSuggestion.builder()
                        .fixType(FixType.MANUAL)
                        .originalCode("")
                        .suggestedCode("")
                        .autoApplicable(false)
                        .build())
                .referenceUrl("https://openjdk.org")
                .build();
    }
}
