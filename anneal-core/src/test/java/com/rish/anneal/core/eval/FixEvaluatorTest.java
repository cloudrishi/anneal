package com.rish.anneal.core.eval;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validates the ground truth fixture files and the FixEvaluator.
 *
 * <p>Two test types:
 * <ol>
 *   <li>Fixture validation — asserts that every After.java compiles cleanly.
 *       This is the ground truth integrity check — if After.java doesn't compile,
 *       the fixture itself is wrong.</li>
 *   <li>Evaluator correctness — asserts that valid Java passes and invalid Java fails.</li>
 * </ol>
 *
 * <p>If any fixture test fails, the ground truth is wrong — fix the fixture, not the evaluator.
 */
class FixEvaluatorTest {

    private final FixEvaluator evaluator = new FixEvaluator();

    // ─── Ground truth fixture validation ─────────────────────────────────────

    /**
     * Every After.java must compile cleanly.
     * These are the ground truth files — if they don't compile, the fixture is wrong.
     */
    @ParameterizedTest(name = "After.java compiles for rule: {0}")
    @ValueSource(strings = {
            "JPMS_SUN_IMPORT",
            "API_JAXB_REMOVED",
            "API_JAX_WS_REMOVED",
            "LANGUAGE_OLD_DATETIME_API",
            "LANGUAGE_ANONYMOUS_CLASS_LAMBDA",
            "LANGUAGE_INSTANCEOF_CAST"
    })
    void afterFixture_compilesCleanly(String ruleId) {
        GroundTruthFixture fixture = GroundTruthFixture.load(ruleId);

        assertThat(fixture.afterSource())
                .as("After.java for %s must not be blank", ruleId)
                .isNotBlank();

        EvalResult result = evaluator.evaluateFixture(fixture);

        assertThat(result.syntaxValid())
                .as("After.java for %s must be syntactically valid — fix the fixture", ruleId)
                .isTrue();

        assertThat(result.compiles())
                .as("After.java for %s must compile — fix the fixture. Error: %s",
                        ruleId, result.message())
                .isTrue();
    }

    /**
     * Every Before.java must load — it doesn't need to compile
     * (it uses APIs removed in Java 9+) but it must exist and be non-blank.
     */
    @ParameterizedTest(name = "Before.java loads for rule: {0}")
    @ValueSource(strings = {
            "JPMS_SUN_IMPORT",
            "API_JAXB_REMOVED",
            "API_JAX_WS_REMOVED",
            "LANGUAGE_OLD_DATETIME_API",
            "LANGUAGE_ANONYMOUS_CLASS_LAMBDA",
            "LANGUAGE_INSTANCEOF_CAST"
    })
    void beforeFixture_loadsAndIsNonBlank(String ruleId) {
        GroundTruthFixture fixture = GroundTruthFixture.load(ruleId);

        assertThat(fixture.beforeSource())
                .as("Before.java for %s must not be blank", ruleId)
                .isNotBlank();

        assertThat(fixture.beforeSource())
                .as("Before.java for %s must contain the rule trigger", ruleId)
                .contains("package fixtures");
    }

    // ─── Evaluator correctness ────────────────────────────────────────────────

    @Test
    void validJavaSource_returnsOk() {
        String source = """
                package test;
                
                import java.time.Instant;
                
                public class Valid {
                    public Instant now() {
                        return Instant.now();
                    }
                }
                """;

        EvalResult result = evaluator.evaluate("test-id", "TEST_RULE", source);

        assertThat(result.syntaxValid()).isTrue();
        assertThat(result.compiles()).isTrue();
        assertThat(result.isValid()).isTrue();
        assertThat(result.message()).isEqualTo("OK");
    }

    @Test
    void syntacticallyInvalidCode_returnsSyntaxError() {
        String source = "this is not java {{{";

        EvalResult result = evaluator.evaluate("test-id", "TEST_RULE", source);

        assertThat(result.syntaxValid()).isFalse();
        assertThat(result.compiles()).isFalse();
        assertThat(result.isValid()).isFalse();
        assertThat(result.message()).contains("Parse error");
    }

    @Test
    void syntacticallyValidButNonCompiling_returnsCompileError() {
        // Valid syntax but references a type that doesn't exist
        String source = """
                package test;
                
                public class Invalid {
                    public void method() {
                        NonExistentType x = new NonExistentType();
                    }
                }
                """;

        EvalResult result = evaluator.evaluate("test-id", "TEST_RULE", source);

        assertThat(result.syntaxValid()).isTrue();
        assertThat(result.compiles()).isFalse();
        assertThat(result.isValid()).isFalse();
        assertThat(result.message()).contains("Compile error");
    }

    @Test
    void blankSuggestedCode_returnsCompileError() {
        EvalResult result = evaluator.evaluate("test-id", "TEST_RULE", "");

        assertThat(result.isValid()).isFalse();
    }
}
