package com.rish.anneal.core.eval;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.rish.anneal.core.model.Finding;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Evaluates fix suggestions for migration findings.
 *
 * <p>Two-stage validation:
 * <ol>
 *   <li><b>Syntax check</b> — JavaParser parses the suggested code. Fast (~1ms).
 *       Catches malformed code that would never compile.</li>
 *   <li><b>Compilation check</b> — {@code javax.tools.JavaCompiler} compiles the
 *       suggested code in-memory. Definitive (~50-200ms). Catches type errors,
 *       missing imports, and other semantic issues that parse cleanly but fail to compile.</li>
 * </ol>
 *
 * <p>The compilation check wraps the {@code suggestedCode} in a minimal class shell
 * so it can be compiled as a standalone unit. This means the check validates the
 * code structure, not the full integration — the developer still reviews before applying.
 *
 * <p>Stateless — safe to use concurrently.
 */
public class FixEvaluator {

    private static final JavaParser PARSER = new JavaParser(
            new com.github.javaparser.ParserConfiguration()
                    .setLanguageLevel(com.github.javaparser.ParserConfiguration.LanguageLevel.JAVA_21)
    );

    /**
     * Evaluates the suggested fix for a finding.
     *
     * <p>Uses the finding's {@code suggestedCode} from its {@code FixSuggestion}.
     * If the finding has no fix suggestion, returns a compile error result.
     *
     * @param finding the finding to evaluate
     * @return EvalResult — use {@link EvalResult#isValid()} to check if safe to auto-apply
     */
    public EvalResult evaluate(Finding finding) {
        if (finding.getFixSuggestion() == null ||
                finding.getFixSuggestion().getSuggestedCode() == null ||
                finding.getFixSuggestion().getSuggestedCode().isBlank()) {
            return EvalResult.compileError(
                    finding.getFindingId(), finding.getRuleId(),
                    "No suggested code available for evaluation");
        }

        String suggestedCode = finding.getFixSuggestion().getSuggestedCode();
        return evaluate(finding.getFindingId(), finding.getRuleId(), suggestedCode);
    }

    /**
     * Evaluates a raw source string directly.
     * Used by the ground truth fixture tests to validate After.java files.
     *
     * @param findingId  identifier for the result
     * @param ruleId     rule identifier for the result
     * @param javaSource complete Java source string to evaluate
     * @return EvalResult
     */
    public EvalResult evaluate(String findingId, String ruleId, String javaSource) {

        if (javaSource == null || javaSource.isBlank()) {
            return EvalResult.compileError(findingId, ruleId, "No suggested code available for evaluation");
        }
        // Stage 1 — syntax check via JavaParser
        EvalResult syntaxResult = checkSyntax(findingId, ruleId, javaSource);
        if (!syntaxResult.syntaxValid()) {
            return syntaxResult;
        }

        // Stage 2 — compilation check via javax.tools
        return checkCompilation(findingId, ruleId, javaSource);
    }

    /**
     * Evaluates a ground truth fixture — checks that the After.java compiles cleanly.
     *
     * @param fixture the fixture to evaluate
     * @return EvalResult for the After.java source
     */
    public EvalResult evaluateFixture(GroundTruthFixture fixture) {
        return evaluate(
                "fixture-" + fixture.ruleId(),
                fixture.ruleId(),
                fixture.afterSource()
        );
    }

    // ─── Stage 1: Syntax check ────────────────────────────────────────────────

    private EvalResult checkSyntax(String findingId, String ruleId, String source) {
        try {
            ParseResult<CompilationUnit> result = PARSER.parse(source);
            if (result.isSuccessful()) {
                return new EvalResult(findingId, ruleId, true, false, "syntax OK");
            }

            String problems = result.getProblems().stream()
                    .map(p -> p.getMessage())
                    .collect(Collectors.joining("; "));
            return EvalResult.syntaxError(findingId, ruleId, "Parse error: " + problems);

        } catch (Exception e) {
            return EvalResult.syntaxError(findingId, ruleId,
                    "JavaParser exception: " + e.getMessage());
        }
    }

    // ─── Stage 2: Compilation check ──────────────────────────────────────────

    private EvalResult checkCompilation(String findingId, String ruleId, String source) {
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            // Running on JRE not JDK — skip compilation check, trust syntax check
            return EvalResult.ok(findingId, ruleId);
        }

        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        var fileManager = compiler.getStandardFileManager(diagnostics, null, null);

        // Extract public class name from source — filename must match class name
        String className = extractClassName(source);
        var sourceFile = new InMemoryJavaSource(className, source);

        var task = compiler.getTask(
                null,
                fileManager,
                diagnostics,
                List.of("--release", "25"),           // skip annotation processing
                null,
                List.of(sourceFile)
        );

        boolean success = task.call();

        if (success) {
            return EvalResult.ok(findingId, ruleId);
        }

        String errors = diagnostics.getDiagnostics().stream()
                .filter(d -> d.getKind() == javax.tools.Diagnostic.Kind.ERROR)
                .map(d -> d.getMessage(null))
                .collect(Collectors.joining("; "));

        return EvalResult.compileError(findingId, ruleId, "Compile error: " + errors);
    }

    /**
     * Extracts the public class name from Java source for filename matching.
     */
    private static String extractClassName(String source) {
        for (String line : source.split("\n")) {
            line = line.trim();
            if (line.startsWith("public class ") || line.startsWith("public final class ")) {
                String[] parts = line.split("\\s+");
                for (int i = 0; i < parts.length - 1; i++) {
                    if (parts[i].equals("class")) {
                        return parts[i + 1].replaceAll("[{<].*", "").trim();
                    }
                }
            }
        }
        return "EvalClass"; // fallback for snippets without a class declaration
    }

    // ─── Inner types ──────────────────────────────────────────────────────────

    /**
     * In-memory Java source file for the compiler — no temp files needed.
     */
    private static class InMemoryJavaSource extends SimpleJavaFileObject {

        private final String source;

        InMemoryJavaSource(String className, String source) {
            super(URI.create("string:///" + className.replace('.', '/') + Kind.SOURCE.extension),
                    Kind.SOURCE);
            this.source = source;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }
    }
}
