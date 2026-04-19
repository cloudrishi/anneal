package com.rish.anneal.core.scanner;

import com.rish.anneal.core.model.Effort;
import com.rish.anneal.core.model.Finding;
import com.rish.anneal.core.model.JavaVersion;
import com.rish.anneal.core.model.MigrationRule;
import com.rish.anneal.core.model.PatternType;
import com.rish.anneal.core.model.RuleCategory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Scans pom.xml and build.gradle files for BUILD category rule violations.
 * Text-based scanning — no XML/Gradle parser needed for the patterns we detect.
 *
 * Separated from CodebaseScanner because build file scanning
 * is fundamentally different from Java AST scanning.
 */
public class BuildFileScanner {

    private static final Logger log = Logger.getLogger(BuildFileScanner.class.getName());

    /**
     * Scans build files in the repository for BUILD category rule violations.
     */
    public List<Finding> scan(Path repoPath,
                              List<MigrationRule> rules,
                              JavaVersion source,
                              JavaVersion target) {
        List<Finding> findings = new ArrayList<>();

        List<MigrationRule> buildRules = rules.stream()
                .filter(r -> r.getCategory() == RuleCategory.BUILD)
                .filter(r -> r.appliesTo(source, target))
                .toList();

        if (buildRules.isEmpty()) {
            return findings;
        }

        // Scan pom.xml files
        scanBuildFile(repoPath.resolve("pom.xml"), buildRules, findings);

        // Scan build.gradle files (basic support)
        scanBuildFile(repoPath.resolve("build.gradle"), buildRules, findings);
        scanBuildFile(repoPath.resolve("build.gradle.kts"), buildRules, findings);

        return findings;
    }

    private void scanBuildFile(Path buildFile,
                               List<MigrationRule> buildRules,
                               List<Finding> findings) {
        if (!Files.exists(buildFile)) {
            return;
        }

        String content;
        try {
            content = Files.readString(buildFile);
        } catch (IOException e) {
            log.warning("Could not read build file: " + buildFile);
            return;
        }

        String[] lines = content.split("\n");

        for (MigrationRule rule : buildRules) {
            rule.getPatterns().stream()
                    .filter(p -> p.getType() == PatternType.BUILD)
                    .forEach(pattern -> {
                        for (int i = 0; i < lines.length; i++) {
                            if (lines[i].contains(pattern.getMatcher())) {
                                findings.add(Finding.builder()
                                        .findingId(UUID.randomUUID().toString())
                                        .ruleId(rule.getRuleId())
                                        .ruleName(rule.getName())
                                        .category(rule.getCategory())
                                        .severity(rule.getSeverity())
                                        .effort(rule.getEffort())
                                        .filePath(buildFile.toString())
                                        .lineNumber(i + 1)
                                        .originalCode(lines[i].trim())
                                        .description("[%s] %s — detected in build file"
                                                .formatted(rule.getRuleId(), rule.getName()))
                                        .confidence(pattern.getConfidence())
                                        .affectsVersion(rule.getIntroducedIn())
                                        .fixSuggestion(rule.getFixTemplate())
                                        .build());
                            }
                        }
                    });
        }
    }
}
