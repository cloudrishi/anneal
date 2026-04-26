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
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Walks a Java repository, parses every .java file, applies the rule engine,
 * and assembles a ScanResult.
 * <p>
 * Stateless — create once, scan many times.
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

        configureParser(repoPath);

        List<Finding> findings = new ArrayList<>();
        List<Path> javaFiles = collectJavaFiles(repoPath);
        int filesWithFindings = 0;

        for (Path javaFile : javaFiles) {
            List<Finding> fileFindings = scanFile(javaFile, rules, source, target);
            if (!fileFindings.isEmpty()) {
                filesWithFindings++;
                findings.addAll(fileFindings);
            }
        }

        // Scan build files for BUILD category rules
        findings.addAll(buildFileScanner.scan(repoPath, rules, source, target));

        int riskScore = riskScoreCalculator.calculate(findings);

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

    private List<Path> collectJavaFiles(Path repoPath) {
        try (Stream<Path> walk = Files.walk(repoPath)) {
            return walk
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.toString().contains("/target/"))
                    .filter(p -> !p.toString().contains("/build/"))
                    .toList();
        } catch (IOException e) {
            log.severe("Failed to walk repository: " + e.getMessage());
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
