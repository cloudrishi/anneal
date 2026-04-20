package com.rish.anneal.api.dto;

import java.util.List;

/**
 * Response body for POST /api/scan.
 * Contains the full scan result including all findings,
 * risk score, and per-boundary breakdown.
 *
 * @param scanId           unique identifier for this scan
 * @param repoPath         scanned repository path
 * @param detectedVersion  Java version detected from build file
 * @param targetVersion    migration target — always Java 25
 * @param riskScore        aggregate risk score 0–100
 * @param riskBand         LOW · MEDIUM · HIGH · CRITICAL
 * @param phase            current migration phase
 * @param filesScanned     total number of Java files scanned
 * @param filesWithFindings number of files that produced at least one finding
 * @param findings         all findings ordered by severity then confidence
 * @param boundaryScores   risk score broken down per version boundary
 * @param scannedAt        ISO-8601 timestamp of when the scan ran
 */
public record ScanResponse(
        String scanId,
        String repoPath,
        String detectedVersion,
        String targetVersion,
        int riskScore,
        String riskBand,
        String phase,
        int filesScanned,
        int filesWithFindings,
        List<FindingDto> findings,
        List<BoundaryScoreDto> boundaryScores,
        String scannedAt
) {

    /**
     * Per-boundary risk score breakdown.
     *
     * @param from         source version e.g. "Java 8"
     * @param to           target version e.g. "Java 11"
     * @param score        risk score 0–100 for this boundary
     * @param band         LOW · MEDIUM · HIGH · CRITICAL
     * @param findingCount number of findings in this boundary
     */
    public record BoundaryScoreDto(
            String from,
            String to,
            int score,
            String band,
            int findingCount
    ) {}
}
