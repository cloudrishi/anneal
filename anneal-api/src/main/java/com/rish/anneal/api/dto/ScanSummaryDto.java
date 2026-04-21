package com.rish.anneal.api.dto;

/**
 * Lightweight summary of a scan for the list view.
 * Does not include findings — use GET /api/scans/{scanId} for full detail.
 *
 * @param scanId           unique identifier
 * @param repoPath         scanned repository path
 * @param detectedVersion  Java version detected
 * @param targetVersion    migration target
 * @param riskScore        aggregate risk score 0–100
 * @param riskBand         LOW · MEDIUM · HIGH · CRITICAL
 * @param phase            current migration phase
 * @param filesScanned     total files scanned
 * @param filesWithFindings files that produced findings
 * @param findingCount     total number of findings
 * @param scannedAt        ISO-8601 timestamp
 */
public record ScanSummaryDto(
        String scanId,
        String repoPath,
        String detectedVersion,
        String targetVersion,
        int riskScore,
        String riskBand,
        String phase,
        int filesScanned,
        int filesWithFindings,
        long findingCount,
        String scannedAt
) {}
