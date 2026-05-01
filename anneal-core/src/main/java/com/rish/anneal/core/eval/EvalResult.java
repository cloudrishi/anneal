package com.rish.anneal.core.eval;

/**
 * Result of evaluating a single fix suggestion against the ground truth.
 *
 * <p>Two levels of validation are performed:
 * <ol>
 *   <li>{@code syntaxValid} — the suggested code is parseable by JavaParser.
 *       Fast, no compiler required.</li>
 *   <li>{@code compiles} — the suggested code compiles via {@code javax.tools.JavaCompiler}.
 *       Slower but definitive. A fix that parses but doesn't compile is still wrong.</li>
 * </ol>
 *
 * <p>Both are required to be {@code true} for a fix to be considered safe for auto-apply.
 *
 * @param findingId   the finding this evaluation covers
 * @param ruleId      the rule that produced the finding
 * @param syntaxValid true if JavaParser can parse the suggested code without errors
 * @param compiles    true if javac can compile the suggested code without errors
 * @param message     diagnostic message — compiler error, parse error, or "OK"
 */
public record EvalResult(
        String findingId,
        String ruleId,
        boolean syntaxValid,
        boolean compiles,
        String message
) {
    /** Convenience — returns true only when both syntax and compilation checks pass. */
    public boolean isValid() {
        return syntaxValid && compiles;
    }

    /** Convenience factory for a passing result. */
    public static EvalResult ok(String findingId, String ruleId) {
        return new EvalResult(findingId, ruleId, true, true, "OK");
    }

    /** Convenience factory for a syntax failure. */
    public static EvalResult syntaxError(String findingId, String ruleId, String message) {
        return new EvalResult(findingId, ruleId, false, false, message);
    }

    /** Convenience factory for a compilation failure. */
    public static EvalResult compileError(String findingId, String ruleId, String message) {
        return new EvalResult(findingId, ruleId, true, false, message);
    }
}
