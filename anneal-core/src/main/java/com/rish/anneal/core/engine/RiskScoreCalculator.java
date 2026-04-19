package com.rish.anneal.core.engine;

import com.rish.anneal.core.model.Effort;
import com.rish.anneal.core.model.Finding;
import com.rish.anneal.core.model.JavaVersion;
import com.rish.anneal.core.model.Severity;

import java.util.List;

/**
 * Calculates an aggregate risk score for a set of findings.
 *
 * Score formula per finding:
 *   findingScore = severityWeight × confidence × effortMultiplier
 *
 * Aggregate score is the sum of all finding scores, capped at 100.
 *
 * Risk bands:
 *   0–20   LOW      — mostly modernization, no blockers
 *   21–50  MEDIUM   — some deprecated APIs, addressable
 *   51–80  HIGH     — multiple breaking changes, plan carefully
 *   81–100 CRITICAL — JPMS violations or mass API removals
 *
 * Stateless and deterministic — no LLM involvement.
 */
public class RiskScoreCalculator {

    public static final int MAX_SCORE = 100;

    public static final int LOW_THRESHOLD = 20;
    public static final int MEDIUM_THRESHOLD = 50;
    public static final int HIGH_THRESHOLD = 80;

    /**
     * Calculates the aggregate risk score for a list of findings.
     *
     * @param findings list of findings from the rule engine
     * @return integer score 0–100
     */
    public int calculate(List<Finding> findings) {
        if (findings == null || findings.isEmpty()) {
            return 0;
        }

        double raw = findings.stream()
                .mapToDouble(this::findingScore)
                .sum();

        return (int) Math.min(MAX_SCORE, Math.round(raw));
    }

    /**
     * Calculates the weighted score for a single finding.
     */
    public double findingScore(Finding finding) {
        return severityWeight(finding.getSeverity())
                * finding.getConfidence()
                * effortMultiplier(finding.getEffort());
    }

    /**
     * Returns the risk band label for a given score.
     */
    public RiskBand band(int score) {
        if (score <= LOW_THRESHOLD) return RiskBand.LOW;
        if (score <= MEDIUM_THRESHOLD) return RiskBand.MEDIUM;
        if (score <= HIGH_THRESHOLD) return RiskBand.HIGH;
        return RiskBand.CRITICAL;
    }

    private double severityWeight(Severity severity) {
        return switch (severity) {
            case BREAKING -> 10.0;
            case DEPRECATED -> 5.0;
            case MODERNIZATION -> 1.0;
        };
    }

    private double effortMultiplier(Effort effort) {
        return switch (effort) {
            case MANUAL -> 1.5;
            case HIGH -> 1.3;
            case MEDIUM -> 1.1;
            case LOW -> 1.0;
            case TRIVIAL -> 0.8;
        };
    }

    /**
     * Calculates risk scores broken down by version boundary.
     * More actionable than a single aggregate — tells the developer
     * which boundary to tackle first.
     *
     * Example output:
     *   8  → 11:  52 / HIGH     (JPMS violations, API removals)
     *   11 → 17:  18 / LOW      (SecurityManager usage)
     *   17 → 21:   3 / LOW      (finalize override)
     *   21 → 25:   0 / LOW      (modernization only)
     *
     * @param findings all findings from the scan
     * @return list of boundary scores in version order
     */
    public List<BoundaryScore> calculatePerBoundary(List<Finding> findings) {
        return List.of(
                boundaryScore(findings, JavaVersion.V8,  JavaVersion.V11),
                boundaryScore(findings, JavaVersion.V11, JavaVersion.V17),
                boundaryScore(findings, JavaVersion.V17, JavaVersion.V21),
                boundaryScore(findings, JavaVersion.V21, JavaVersion.V25)
        );
    }

    private BoundaryScore boundaryScore(List<Finding> findings,
                                        JavaVersion from,
                                        JavaVersion to) {
        List<Finding> scoped = findings.stream()
                .filter(f -> f.getAffectsVersion().isNewerThan(from)
                        && !f.getAffectsVersion().isNewerThan(to))
                .toList();

        int score = calculate(scoped);

        return new BoundaryScore(from, to, score, band(score), scoped.size());
    }

    /**
     * Risk score for a single version boundary.
     *
     * @param from       source version
     * @param to         target version
     * @param score      aggregate risk score 0–100
     * @param band       risk band label
     * @param findingCount number of findings in this boundary
     */
    public record BoundaryScore(
            JavaVersion from,
            JavaVersion to,
            int score,
            RiskBand band,
            int findingCount
    ) {
        @Override
        public String toString() {
            return "%s → %s: %d / %s (%d findings)"
                    .formatted(from, to, score, band, findingCount);
        }
    }

    public enum RiskBand {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }
}
