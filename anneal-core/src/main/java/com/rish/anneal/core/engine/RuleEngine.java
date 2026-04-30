package com.rish.anneal.core.engine;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.rish.anneal.core.model.DetectionPattern;
import com.rish.anneal.core.model.Finding;
import com.rish.anneal.core.model.JavaVersion;
import com.rish.anneal.core.rule.MigrationRule;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Applies a set of migration rules to a parsed Java CompilationUnit.
 * Returns a list of findings — one per matched pattern per rule.
 *
 * <p>Stateless — safe to use concurrently across multiple files.
 * Detection is fully deterministic — no LLM involvement.
 *
 * <h2>Suppression</h2>
 * If the matched AST node is enclosed by a method, constructor, or type
 * annotated with {@code @SuppressAnneal}, the finding is recorded with
 * {@code status: SUPPRESSED} rather than {@code OPEN}. Suppressed findings
 * are excluded from the risk score but remain visible in the report —
 * the decision is auditable.
 *
 * <p>Suppression scope (checked in order):
 * <ol>
 *   <li>Enclosing method or constructor of the matched node</li>
 *   <li>Enclosing type (class or interface)</li>
 * </ol>
 */
public class RuleEngine {

    private static final String SUPPRESS_ANNOTATION = "SuppressAnneal";

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
            case ANNOTATION -> matchAnnotation(cu, filePath, rule, pattern);
            case BUILD -> List.of(); // handled by BuildFileScanner
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
                // For imports, check the enclosing type (imports are file-level)
                Finding.FindingStatus status = isSuppressedAtTypeLevel(cu, rule.getRuleId())
                        ? Finding.FindingStatus.SUPPRESSED
                        : Finding.FindingStatus.OPEN;

                findings.add(buildFinding(rule, pattern, filePath,
                        imp.getBegin().map(p -> p.line).orElse(0),
                        "import " + importName + ";",
                        status));
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
                Finding.FindingStatus status = isSuppressed(call, rule.getRuleId())
                        ? Finding.FindingStatus.SUPPRESSED
                        : Finding.FindingStatus.OPEN;
                findings.add(buildFinding(rule, pattern, filePath,
                        call.getBegin().map(p -> p.line).orElse(0),
                        call.toString(), status));
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
                    Finding.FindingStatus status = isSuppressed(method, rule.getRuleId())
                            ? Finding.FindingStatus.SUPPRESSED
                            : Finding.FindingStatus.OPEN;
                    findings.add(buildFinding(rule, pattern, filePath,
                            method.getBegin().map(p -> p.line).orElse(0),
                            method.getDeclarationAsString(), status));
                }
            });
        }

        // Object creation matching — e.g. new Thread(...)
        if ("ObjectCreationExpr".equals(pattern.getNodeType())) {
            cu.findAll(ObjectCreationExpr.class).forEach(expr -> {
                if (expr.getTypeAsString().contains(matcher.replace("new ", ""))) {
                    Finding.FindingStatus status = isSuppressed(expr, rule.getRuleId())
                            ? Finding.FindingStatus.SUPPRESSED
                            : Finding.FindingStatus.OPEN;
                    findings.add(buildFinding(rule, pattern, filePath,
                            expr.getBegin().map(p -> p.line).orElse(0),
                            expr.toString(), status));
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
                Finding.FindingStatus status = isSuppressed(call, rule.getRuleId())
                        ? Finding.FindingStatus.SUPPRESSED
                        : Finding.FindingStatus.OPEN;
                findings.add(buildFinding(rule, pattern, filePath,
                        call.getBegin().map(p -> p.line).orElse(0),
                        call.toString(), status));
            }
        });
        return findings;
    }

    // --- Annotation matching ---

    /**
     * Matches annotation usages by simple or fully-qualified name.
     * <p>
     * Complements IMPORT-based detection: catches usage of fully-qualified
     * annotations that appear without an import statement, and provides
     * line-level precision on the annotated element rather than the import block.
     * <p>
     * Matcher format: simple name only — e.g. {@code PostConstruct}.
     * JavaParser normalises annotation names to their simple form in most cases;
     * FQN matching ({@code javax.annotation.PostConstruct}) is also supported
     * for fully-qualified usages in source.
     */
    private List<Finding> matchAnnotation(CompilationUnit cu,
                                          String filePath,
                                          MigrationRule rule,
                                          DetectionPattern pattern) {
        List<Finding> findings = new ArrayList<>();
        String matcher = pattern.getMatcher();

        // Support both simple name and FQN in the matcher
        String simpleName = matcher.contains(".")
                ? matcher.substring(matcher.lastIndexOf('.') + 1)
                : matcher;

        cu.findAll(AnnotationExpr.class).forEach(annotation -> {
            String name = annotation.getNameAsString();
            // Match simple name OR trailing segment of FQN usage in source
            if (name.equals(simpleName) || name.equals(matcher) || name.endsWith("." + simpleName)) {
                Finding.FindingStatus status = isSuppressed(annotation, rule.getRuleId())
                        ? Finding.FindingStatus.SUPPRESSED
                        : Finding.FindingStatus.OPEN;
                findings.add(buildFinding(rule, pattern, filePath,
                        annotation.getBegin().map(p -> p.line).orElse(0),
                        "@" + name, status));
            }
        });
        return findings;
    }

    // ─── Suppression check ────────────────────────────────────────────────────

    /**
     * Returns true if the matched node is enclosed by a method, constructor,
     * or type annotated with {@code @SuppressAnneal} covering the given ruleId.
     *
     * <p>Walk order:
     * <ol>
     *   <li>Enclosing MethodDeclaration or ConstructorDeclaration</li>
     *   <li>Enclosing ClassOrInterfaceDeclaration</li>
     * </ol>
     */
    private boolean isSuppressed(Node node, String ruleId) {
        // Check enclosing method or constructor first
        Optional<Node> enclosingMethod = node.findAncestor(
                n -> n instanceof MethodDeclaration || n instanceof ConstructorDeclaration);

        if (enclosingMethod.isPresent() && enclosingMethod.get() instanceof NodeWithAnnotations<?> nwa) {
            if (hasSuppressAnnotation(nwa, ruleId)) return true;
        }

        // Then check enclosing type
        return node.findAncestor(ClassOrInterfaceDeclaration.class)
                .map(cls -> hasSuppressAnnotation(cls, ruleId))
                .orElse(false);
    }

    /**
     * For import-level findings, only the type-level annotation applies
     * since imports are not inside any method.
     */
    private boolean isSuppressedAtTypeLevel(CompilationUnit cu, String ruleId) {
        return cu.findAll(ClassOrInterfaceDeclaration.class).stream()
                .anyMatch(cls -> hasSuppressAnnotation(cls, ruleId));
    }

    /**
     * Returns true if the node has {@code @SuppressAnneal} with no value
     * (suppress all) or with a value array that contains the given ruleId.
     */
    private boolean hasSuppressAnnotation(NodeWithAnnotations<?> node, String ruleId) {
        return node.getAnnotations().stream()
                .filter(a -> a.getNameAsString().equals(SUPPRESS_ANNOTATION))
                .anyMatch(a -> suppressesRule(a, ruleId));
    }

    /**
     * Returns true if the annotation suppresses the given ruleId.
     *
     * <p>Three forms supported:
     * <ul>
     *   <li>{@code @SuppressAnneal} — no value, suppresses all rules</li>
     *   <li>{@code @SuppressAnneal("RULE_ID")} — single string value</li>
     *   <li>{@code @SuppressAnneal({"RULE_A", "RULE_B"})} — array value</li>
     * </ul>
     */
    private boolean suppressesRule(AnnotationExpr annotation, String ruleId) {
        // @SuppressAnneal with no value — suppress all
        if (!annotation.isSingleMemberAnnotationExpr() &&
                !annotation.isNormalAnnotationExpr()) {
            return true;
        }

        // Extract the value expression
        var valueOpt = annotation.isSingleMemberAnnotationExpr()
                ? Optional.of(annotation.asSingleMemberAnnotationExpr().getMemberValue())
                : annotation.asNormalAnnotationExpr().getPairs().stream()
                .filter(p -> p.getNameAsString().equals("value"))
                .map(p -> p.getValue())
                .findFirst();

        if (valueOpt.isEmpty()) return true; // no value = suppress all

        var value = valueOpt.get();

        // Single string: @SuppressAnneal("RULE_ID")
        if (value instanceof StringLiteralExpr str) {
            return str.asString().equals(ruleId);
        }

        // Array: @SuppressAnneal({"RULE_A", "RULE_B"})
        if (value instanceof ArrayInitializerExpr arr) {
            return arr.getValues().stream()
                    .filter(v -> v instanceof StringLiteralExpr)
                    .map(v -> ((StringLiteralExpr) v).asString())
                    .anyMatch(s -> s.equals(ruleId));
        }

        return false;
    }

    // ─── Finding builder ──────────────────────────────────────────────────────

    private Finding buildFinding(MigrationRule rule,
                                 DetectionPattern pattern,
                                 String filePath,
                                 int lineNumber,
                                 String originalCode,
                                 Finding.FindingStatus status) {
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
                .referenceUrl(rule.getReferenceUrl())
                .status(status)
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
