package com.rish.anneal.core.scanner;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.rish.anneal.core.engine.RiskScoreCalculator;
import com.rish.anneal.core.engine.RuleEngine;
import com.rish.anneal.core.model.Finding;
import com.rish.anneal.core.model.JavaVersion;
import com.rish.anneal.core.model.MigrationPhase;
import com.rish.anneal.core.model.ScanResult;
import com.rish.anneal.core.rule.MigrationRule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Walks a Java repository, parses every .java file, applies the rule engine,
 * and assembles a ScanResult.
 *
 * <p>Stateless — create once, scan many times.
 * <h2>Progress streaming</h2>
 * The overloaded {@link #scan(Path, List, JavaVersion, JavaVersion, Consumer)} method
 * accepts a {@link ScanProgressEvent} callback that is invoked after each file is
 * scanned. This powers the SSE streaming endpoint in {@code ScanProgressResource}.
 *
 * <p>Callers that don't need progress (tests, curl, programmatic use) call the
 * no-arg overload {@link #scan(Path, List, JavaVersion, JavaVersion)} which passes
 * a no-op consumer — zero overhead, zero breaking changes.
 */
public class CodebaseScanner {

    private static final Logger log = Logger.getLogger(CodebaseScanner.class.getName());

    private final RuleEngine ruleEngine;
    private final RiskScoreCalculator riskScoreCalculator;
    private final BuildFileScanner buildFileScanner;

    public CodebaseScanner(RuleEngine ruleEngine,
                           RiskScoreCalculator riskScoreCalculator,
                           BuildFileScanner buildFileScanner) {
        this.ruleEngine = ruleEngine;
        this.riskScoreCalculator = riskScoreCalculator;
        this.buildFileScanner = buildFileScanner;
    }

    /**
     * Scans a Java repository and returns a ScanResult.
     * No progress events are emitted — use this for programmatic access and tests.
     *
     * @param repoPath absolute path to the repository root
     * @param rules    rules to apply — typically all rules for the target boundary
     * @param source   detected or specified source Java version
     * @param target   migration target version
     * @return populated ScanResult
     */
    public ScanResult scan(Path repoPath,
                           List<MigrationRule> rules,
                           JavaVersion source,
                           JavaVersion target) {
        return scan(repoPath, rules, source, target, event -> {
        });
    }

    /**
     * Scans a Java repository and returns a ScanResult, emitting progress events
     * via the supplied callback.
     *
     * <p>Events emitted per file:
     * <ul>
     *   <li>{@link ScanProgressEvent.Type#FILE} — after each .java file is scanned,
     *       carrying the filename, finding count for that file, and running totals</li>
     *   <li>{@link ScanProgressEvent.Type#COMPLETE} — once after all files and build
     *       files are processed</li>
     *   <li>{@link ScanProgressEvent.Type#ERROR} — if file collection fails</li>
     * </ul>
     *
     * <p>The callback is invoked synchronously on the scanning thread. SSE callers
     * should ensure the callback is non-blocking (i.e. just pushes to a queue or
     * channel) to avoid stalling the scanner.
     *
     * @param repoPath   absolute path to the repository root
     * @param rules      rules to apply
     * @param source     source Java version
     * @param target     target Java version
     * @param onProgress callback invoked after each file and on completion
     * @return populated ScanResult
     */
    public ScanResult scan(Path repoPath,
                           List<MigrationRule> rules,
                           JavaVersion source,
                           JavaVersion target,
                           Consumer<ScanProgressEvent> onProgress) {

        configureParser(repoPath);

        List<Finding> findings = new ArrayList<>();
        List<Path> javaFiles = collectJavaFiles(repoPath, onProgress);
        int totalFiles = javaFiles.size();
        int filesScanned = 0;
        int filesWithFindings = 0;

        for (Path javaFile : javaFiles) {
            List<Finding> fileFindings = scanFile(javaFile, rules, source, target);
            filesScanned++;

            if (!fileFindings.isEmpty()) {
                filesWithFindings++;
                findings.addAll(fileFindings);
            }

            // Emit FILE event — short filename for readable UI display
            onProgress.accept(ScanProgressEvent.file(
                    javaFile.getFileName().toString(),
                    fileFindings.size(),
                    filesScanned,
                    totalFiles
            ));
        }

        // Scan build files — no per-file events, included in COMPLETE count
        List<Finding> buildFindings = buildFileScanner.scan(repoPath, rules, source, target);
        findings.addAll(buildFindings);

        int riskScore = riskScoreCalculator.calculate(findings);

        // Emit COMPLETE event
        onProgress.accept(ScanProgressEvent.complete(filesScanned, totalFiles, findings.size()));

        return ScanResult.builder()
                .scanId(UUID.randomUUID().toString())
                .repoPath(repoPath.toString())
                .detectedVersion(source)
                .targetVersion(target)
                .findings(List.copyOf(findings))
                .riskScore(riskScore)
                .phase(MigrationPhase.ANALYSIS)
                .scannedAt(Instant.now())
                .filesScanned(javaFiles.size())
                .filesWithFindings(filesWithFindings)
                .build();
    }

    private List<Finding> scanFile(Path javaFile,
                                   List<MigrationRule> rules,
                                   JavaVersion source,
                                   JavaVersion target) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(javaFile);
            return ruleEngine.apply(cu, javaFile.toString(), rules, source, target);
        } catch (Exception e) {
            log.warning("Skipping %s — parse error: %s"
                    .formatted(javaFile, e.getMessage()));
            return List.of();
        }
    }

    private List<Path> collectJavaFiles(Path repoPath,
                                        Consumer<ScanProgressEvent> onProgress) {
        try (Stream<Path> walk = Files.walk(repoPath)) {
            return walk
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.toString().contains("/target/"))
                    .filter(p -> !p.toString().contains("/build/"))
                    .toList();
        } catch (IOException e) {
            log.severe("Failed to walk repository: " + e.getMessage());
            onProgress.accept(ScanProgressEvent.error(
                    "Failed to walk repository: " + e.getMessage()));
            return List.of();
        }
    }

    private void configureParser(Path repoPath) {
        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        typeSolver.add(new ReflectionTypeSolver());

        // Add source root for symbol resolution if src/main/java exists
        Path srcMain = repoPath.resolve("src/main/java");
        if (Files.exists(srcMain)) {
            typeSolver.add(new JavaParserTypeSolver(srcMain));
        }

        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(typeSolver);
        StaticJavaParser.getParserConfiguration()
                .setSymbolResolver(symbolSolver)
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);
    }
}
