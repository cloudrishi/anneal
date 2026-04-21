package com.rish.anneal.core.engine;

import com.rish.anneal.core.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class RiskScoreCalculatorTest {

    private RiskScoreCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new RiskScoreCalculator();
    }

    @Nested
    @DisplayName("findingScore()")
    class FindingScoreTests {

        @Test
        @DisplayName("BREAKING + HIGH effort = 13.0")
        void breakingHighEffort() {
            Finding f = finding(Severity.BREAKING, Effort.HIGH, 1.0f);
            assertThat(calculator.findingScore(f)).isCloseTo(13.0, within(0.01));
        }

        @Test
        @DisplayName("BREAKING + HIGH effort + 0.7 confidence = 9.1")
        void breakingHighEffortLowConfidence() {
            Finding f = finding(Severity.BREAKING, Effort.HIGH, 0.7f);
            assertThat(calculator.findingScore(f)).isCloseTo(9.1, within(0.01));
        }

        @Test
        @DisplayName("DEPRECATED + MEDIUM effort + 0.9 confidence = 4.95")
        void deprecatedMediumEffort() {
            Finding f = finding(Severity.DEPRECATED, Effort.MEDIUM, 0.9f);
            assertThat(calculator.findingScore(f)).isCloseTo(4.95, within(0.01));
        }

        @Test
        @DisplayName("MODERNIZATION + LOW effort + 0.5 confidence = 0.5")
        void modernizationLowEffort() {
            Finding f = finding(Severity.MODERNIZATION, Effort.LOW, 0.5f);
            assertThat(calculator.findingScore(f)).isCloseTo(0.5, within(0.01));
        }

        @Test
        @DisplayName("BREAKING + MANUAL effort = 15.0")
        void breakingManualEffort() {
            Finding f = finding(Severity.BREAKING, Effort.MANUAL, 1.0f);
            assertThat(calculator.findingScore(f)).isCloseTo(15.0, within(0.01));
        }

        @Test
        @DisplayName("BREAKING + TRIVIAL effort = 8.0")
        void breakingTrivialEffort() {
            Finding f = finding(Severity.BREAKING, Effort.TRIVIAL, 1.0f);
            assertThat(calculator.findingScore(f)).isCloseTo(8.0, within(0.01));
        }
    }

    @Nested
    @DisplayName("calculate()")
    class CalculateTests {

        @Test
        @DisplayName("empty findings = 0")
        void emptyFindings() {
            assertThat(calculator.calculate(List.of())).isEqualTo(0);
        }

        @Test
        @DisplayName("null findings = 0")
        void nullFindings() {
            assertThat(calculator.calculate(null)).isEqualTo(0);
        }

        @Test
        @DisplayName("score is capped at 100")
        void scoreIsCapped() {
            // 10 BREAKING HIGH findings = 130 raw, capped at 100
            List<Finding> findings = List.of(
                    finding(Severity.BREAKING, Effort.HIGH, 1.0f),
                    finding(Severity.BREAKING, Effort.HIGH, 1.0f),
                    finding(Severity.BREAKING, Effort.HIGH, 1.0f),
                    finding(Severity.BREAKING, Effort.HIGH, 1.0f),
                    finding(Severity.BREAKING, Effort.HIGH, 1.0f),
                    finding(Severity.BREAKING, Effort.HIGH, 1.0f),
                    finding(Severity.BREAKING, Effort.HIGH, 1.0f),
                    finding(Severity.BREAKING, Effort.HIGH, 1.0f),
                    finding(Severity.BREAKING, Effort.HIGH, 1.0f),
                    finding(Severity.BREAKING, Effort.HIGH, 1.0f)
            );
            assertThat(calculator.calculate(findings)).isEqualTo(100);
        }

        @Test
        @DisplayName("test-legacy file produces score of 66")
        void testLegacyScore() {
            // Mirrors the actual test-legacy findings
            List<Finding> findings = List.of(
                    finding(Severity.BREAKING, Effort.HIGH, 1.0f),   // JPMS_SUN_IMPORT
                    finding(Severity.BREAKING, Effort.HIGH, 1.0f),   // JPMS_UNSAFE_USAGE (import)
                    finding(Severity.BREAKING, Effort.HIGH, 1.0f),   // JPMS_UNSAFE_USAGE (api call)
                    finding(Severity.BREAKING, Effort.LOW, 1.0f),    // API_JAXB_REMOVED
                    finding(Severity.BREAKING, Effort.MEDIUM, 1.0f), // API_THREAD_STOP_REMOVED
                    finding(Severity.DEPRECATED, Effort.MEDIUM, 0.9f), // DEPRECATION_FINALIZE
                    finding(Severity.MODERNIZATION, Effort.MEDIUM, 0.6f) // CONCURRENCY_THREAD_VIRTUAL
            );
            assertThat(calculator.calculate(findings)).isEqualTo(66);
        }
    }

    @Nested
    @DisplayName("band()")
    class BandTests {

        @Test
        @DisplayName("0 = LOW")
        void zero() {
            assertThat(calculator.band(0)).isEqualTo(RiskScoreCalculator.RiskBand.LOW);
        }

        @Test
        @DisplayName("20 = LOW")
        void twentyIsLow() {
            assertThat(calculator.band(20)).isEqualTo(RiskScoreCalculator.RiskBand.LOW);
        }

        @Test
        @DisplayName("21 = MEDIUM")
        void twentyOneIsMedium() {
            assertThat(calculator.band(21)).isEqualTo(RiskScoreCalculator.RiskBand.MEDIUM);
        }

        @Test
        @DisplayName("50 = MEDIUM")
        void fiftyIsMedium() {
            assertThat(calculator.band(50)).isEqualTo(RiskScoreCalculator.RiskBand.MEDIUM);
        }

        @Test
        @DisplayName("51 = HIGH")
        void fiftyOneIsHigh() {
            assertThat(calculator.band(51)).isEqualTo(RiskScoreCalculator.RiskBand.HIGH);
        }

        @Test
        @DisplayName("80 = HIGH")
        void eightyIsHigh() {
            assertThat(calculator.band(80)).isEqualTo(RiskScoreCalculator.RiskBand.HIGH);
        }

        @Test
        @DisplayName("81 = CRITICAL")
        void eightyOneIsCritical() {
            assertThat(calculator.band(81)).isEqualTo(RiskScoreCalculator.RiskBand.CRITICAL);
        }

        @Test
        @DisplayName("100 = CRITICAL")
        void hundredIsCritical() {
            assertThat(calculator.band(100)).isEqualTo(RiskScoreCalculator.RiskBand.CRITICAL);
        }
    }

    @Nested
    @DisplayName("calculatePerBoundary()")
    class PerBoundaryTests {

        @Test
        @DisplayName("returns 4 boundaries")
        void returnsFourBoundaries() {
            assertThat(calculator.calculatePerBoundary(List.of())).hasSize(4);
        }

        @Test
        @DisplayName("boundaries are in version order")
        void boundariesInOrder() {
            var boundaries = calculator.calculatePerBoundary(List.of());
            assertThat(boundaries.get(0).from()).isEqualTo(JavaVersion.V8);
            assertThat(boundaries.get(0).to()).isEqualTo(JavaVersion.V11);
            assertThat(boundaries.get(1).from()).isEqualTo(JavaVersion.V11);
            assertThat(boundaries.get(1).to()).isEqualTo(JavaVersion.V17);
            assertThat(boundaries.get(2).from()).isEqualTo(JavaVersion.V17);
            assertThat(boundaries.get(2).to()).isEqualTo(JavaVersion.V21);
            assertThat(boundaries.get(3).from()).isEqualTo(JavaVersion.V21);
            assertThat(boundaries.get(3).to()).isEqualTo(JavaVersion.V25);
        }

        @Test
        @DisplayName("empty findings = all zero scores")
        void emptyFindingsAllZero() {
            var boundaries = calculator.calculatePerBoundary(List.of());
            boundaries.forEach(bs -> assertThat(bs.score()).isEqualTo(0));
        }
    }

    // --- Helpers ---

    private Finding finding(Severity severity, Effort effort, float confidence) {
        return Finding.builder()
                .findingId(java.util.UUID.randomUUID().toString())
                .ruleId("TEST_RULE")
                .ruleName("Test Rule")
                .category(RuleCategory.JPMS)
                .severity(severity)
                .effort(effort)
                .filePath("/tmp/Test.java")
                .lineNumber(1)
                .originalCode("test code")
                .description("test description")
                .confidence(confidence)
                .affectsVersion(JavaVersion.V9)
                .status(Finding.FindingStatus.OPEN)
                .build();
    }
}
