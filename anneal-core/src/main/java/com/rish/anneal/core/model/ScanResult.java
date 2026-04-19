package com.rish.anneal.core.model;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The result of a full codebase scan.
 * Immutable — produced by the scanner, consumed by the phase orchestrator.
 */
@Value
@Builder
public class ScanResult {

    /** Unique identifier for this scan. */
    String scanId;

    /** Absolute path to the scanned repository. */
    String repoPath;

    /** Java version detected from pom.xml or build.gradle. */
    JavaVersion detectedVersion;

    /** The migration target version. Always V25. */
    @Builder.Default
    JavaVersion targetVersion = JavaVersion.V25;

    /** All findings produced by the rule engine. */
    List<Finding> findings;

    /** Aggregate risk score — 0 (no risk) to 100 (critical). */
    int riskScore;

    /** Current phase this scan is in. */
    MigrationPhase phase;

    /** When this scan was initiated. */
    Instant scannedAt;

    /** Total number of Java files scanned. */
    int filesScanned;

    /** Total number of Java files that produced at least one finding. */
    int filesWithFindings;

    // --- Convenience methods ---

    public List<Finding> findingsBySeverity(Severity severity) {
        return findings.stream()
                .filter(f -> f.getSeverity() == severity)
                .toList();
    }

    public List<Finding> findingsByCategory(RuleCategory category) {
        return findings.stream()
                .filter(f -> f.getCategory() == category)
                .toList();
    }

    public Map<Severity, Long> countBySeverity() {
        return findings.stream()
                .collect(Collectors.groupingBy(Finding::getSeverity, Collectors.counting()));
    }

    public long breakingCount() {
        return findings.stream()
                .filter(f -> f.getSeverity() == Severity.BREAKING)
                .count();
    }

    public boolean hasBlockers() {
        return breakingCount() > 0;
    }
}
