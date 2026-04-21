package com.rish.anneal.core.engine;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.rish.anneal.core.model.DetectionPattern;
import com.rish.anneal.core.model.Finding;
import com.rish.anneal.core.model.JavaVersion;
import com.rish.anneal.core.model.MigrationRule;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Applies a set of migration rules to a parsed Java CompilationUnit.
 * Returns a list of findings — one per matched pattern per rule.
 * <p>
 * Stateless — safe to use concurrently across multiple files.
 * Detection is fully deterministic — no LLM involvement.
 */
public class RuleEngine {

    /**
     * Applies all provided rules to the given CompilationUnit.
     *
     * @param cu       parsed Java file
     * @param filePath absolute path to the file — included in each finding
     * @param rules    rules to apply — typically scoped to a version boundary
     * @param source   detected source Java version
     * @param target   migration target version
     * @return list of findings, may be empty
     */
    public List<Finding> apply(CompilationUnit cu,
                               String filePath,
                               List<MigrationRule> rules,
                               JavaVersion source,
                               JavaVersion target) {
        List<Finding> findings = new ArrayList<>();

        for (MigrationRule rule : rules) {
            if (!rule.appliesTo(source, target)) {
                continue;
            }
            for (DetectionPattern pattern : rule.getPatterns()) {

                List<Finding> matched = matchPattern(cu, filePath, rule, pattern);

                findings.addAll(matched);
            }
        }

        return findings;
    }

    private List<Finding> matchPattern(CompilationUnit cu,
                                       String filePath,
                                       MigrationRule rule,
                                       DetectionPattern pattern) {
        return switch (pattern.getType()) {
            case IMPORT -> matchImport(cu, filePath, rule, pattern);
            case API_CALL -> matchApiCall(cu, filePath, rule, pattern);
            case AST_NODE -> matchAstNode(cu, filePath, rule, pattern);
            case REFLECTION -> matchReflection(cu, filePath, rule, pattern);
            case ANNOTATION, BUILD -> List.of(); // handled by dedicated scanners
        };
    }

    // --- Import matching ---

    private List<Finding> matchImport(CompilationUnit cu,
                                      String filePath,
                                      MigrationRule rule,
                                      DetectionPattern pattern) {
        List<Finding> findings = new ArrayList<>();
        String matcher = pattern.getMatcher();
        String prefix = matcher.endsWith(".*")
                ? matcher.substring(0, matcher.length() - 2)
                : matcher;

        for (ImportDeclaration imp : cu.getImports()) {
            String importName = imp.getNameAsString();
            boolean matches = matcher.endsWith(".*")
                    ? importName.startsWith(prefix)
                    : importName.equals(matcher);

            if (matches) {
                findings.add(buildFinding(rule, pattern, filePath,
                        imp.getBegin().map(p -> p.line).orElse(0),
                        "import " + importName + ";"));
            }
        }
        return findings;
    }

    // --- API call matching ---

    private List<Finding> matchApiCall(CompilationUnit cu,
                                       String filePath,
                                       MigrationRule rule,
                                       DetectionPattern pattern) {
        List<Finding> findings = new ArrayList<>();
        String methodName = extractMethodName(pattern.getMatcher());

        cu.findAll(MethodCallExpr.class).forEach(call -> {
            if (call.getNameAsString().equals(methodName)) {
                findings.add(buildFinding(rule, pattern, filePath,
                        call.getBegin().map(p -> p.line).orElse(0),
                        call.toString()));
            }
        });
        return findings;
    }

    // --- AST node matching ---

    private List<Finding> matchAstNode(CompilationUnit cu,
                                       String filePath,
                                       MigrationRule rule,
                                       DetectionPattern pattern) {
        List<Finding> findings = new ArrayList<>();
        String matcher = pattern.getMatcher();

        // Method declaration matching — e.g. finalize()
        if ("MethodDeclaration".equals(pattern.getNodeType())) {
            cu.findAll(MethodDeclaration.class).forEach(method -> {
                if (method.getNameAsString().equals(matcher)) {
                    findings.add(buildFinding(rule, pattern, filePath,
                            method.getBegin().map(p -> p.line).orElse(0),
                            method.getDeclarationAsString()));
                }
            });
        }

        // Object creation matching — e.g. new Thread(...)
        if ("ObjectCreationExpr".equals(pattern.getNodeType())) {
            cu.findAll(ObjectCreationExpr.class).forEach(expr -> {
                if (expr.getTypeAsString().contains(matcher.replace("new ", ""))) {
                    findings.add(buildFinding(rule, pattern, filePath,
                            expr.getBegin().map(p -> p.line).orElse(0),
                            expr.toString()));
                }
            });
        }

        return findings;
    }

    // --- Reflection matching ---

    private List<Finding> matchReflection(CompilationUnit cu,
                                          String filePath,
                                          MigrationRule rule,
                                          DetectionPattern pattern) {
        List<Finding> findings = new ArrayList<>();

        cu.findAll(MethodCallExpr.class).forEach(call -> {
            if (call.getNameAsString().equals(pattern.getMatcher())) {
                findings.add(buildFinding(rule, pattern, filePath,
                        call.getBegin().map(p -> p.line).orElse(0),
                        call.toString()));
            }
        });
        return findings;
    }

    // --- Finding builder ---

    private Finding buildFinding(MigrationRule rule,
                                 DetectionPattern pattern,
                                 String filePath,
                                 int lineNumber,
                                 String originalCode) {
        return Finding.builder()
                .findingId(UUID.randomUUID().toString())
                .ruleId(rule.getRuleId())
                .ruleName(rule.getName())
                .category(rule.getCategory())
                .severity(rule.getSeverity())
                .effort(rule.getEffort())
                .filePath(filePath)
                .lineNumber(lineNumber)
                .originalCode(originalCode)
                .description(buildDescription(rule, pattern))
                .confidence(pattern.getConfidence())
                .affectsVersion(rule.getIntroducedIn())
                .fixSuggestion(rule.getFixTemplate())
                .build();
    }

    private String buildDescription(MigrationRule rule, DetectionPattern pattern) {
        return "[%s] %s — detected via %s pattern (confidence: %.0f%%)"
                .formatted(rule.getRuleId(), rule.getName(),
                        pattern.getType().name().toLowerCase(),
                        pattern.getConfidence() * 100);
    }

    private String extractMethodName(String matcher) {
        // "java.lang.Thread#stop()" → "stop"
        int hash = matcher.indexOf('#');
        int paren = matcher.indexOf('(');
        if (hash >= 0 && paren > hash) {
            return matcher.substring(hash + 1, paren);
        }
        return matcher;
    }
}
