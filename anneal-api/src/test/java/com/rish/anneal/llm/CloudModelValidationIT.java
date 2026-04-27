package com.rish.anneal.llm;

import com.rish.anneal.core.model.*;
import com.rish.anneal.core.rule.MigrationRule;
import com.rish.anneal.llm.model.EnrichedFix;
import com.rish.anneal.llm.service.FixEnrichmentService;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Opt-in validation harness for {@code claude-sonnet-4-6}.
 *
 * <p>This test is intentionally NOT part of the standard test run. It:
 * <ul>
 *   <li>Requires {@code ANTHROPIC_API_KEY} to be set — skipped otherwise via
 *       {@code @EnabledIfEnvironmentVariable}.</li>
 *   <li>Runs against the same test-legacy fixture used in {@code ScanResourceTest}
 *       (the fixture that produces a risk score of 66) so results are comparable.</li>
 *   <li>Enables {@code allow-cloud-fallback: true} via {@code CloudValidationProfile}
 *       so the Anthropic model is wired at startup.</li>
 * </ul>
 *
 * <h2>Running</h2>
 * <pre>
 *   ANTHROPIC_API_KEY=sk-ant-... \
 *   ./gradlew :anneal-llm:test --tests "*CloudModelValidationIT" --info
 * </pre>
 *
 * <h2>Purpose</h2>
 * The primary goal is a side-by-side quality comparison — explanations are printed
 * to stdout for manual review. The assertions are a minimal sanity bar (non-empty,
 * no obvious [INST] leakage) not a full quality gate. Quality is human-evaluated.
 *
 * <h2>Version hallucination check</h2>
 * Each finding's expected version facts are declared alongside the fixture data.
 * The test asserts the explanation does not contradict those facts. For example,
 * a JPMS_SUN_IMPORT finding (introduced Java 9, no hard removal) should not
 * contain "removed in Java 11" or "removed in Java 17".
 */
@QuarkusTest
@TestProfile(CloudModelValidationIT.CloudValidationProfile.class)
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class CloudModelValidationIT {

    @Inject
    FixEnrichmentService enrichmentService;

    // ─── Test ─────────────────────────────────────────────────────────────────

    @Test
    void cloudExplanations_areNonEmpty_andRespectVersionFacts() {
        List<FindingWithRule> fixture = testLegacyFixture();

        List<Finding> findings = fixture.stream().map(FindingWithRule::finding).toList();
        Map<String, MigrationRule> ruleById = new HashMap<>();
        Map<String, VersionExpectation> expected = new HashMap<>();

        for (FindingWithRule fwr : fixture) {
            ruleById.put(fwr.finding().getRuleId(), fwr.rule());
            expected.put(fwr.finding().getFindingId(), fwr.expectation());
        }

        System.out.println("\n══════════════════════════════════════════════════");
        System.out.println("  Cloud model validation — claude-sonnet-4-6");
        System.out.println("══════════════════════════════════════════════════");

        Map<String, EnrichedFix> results = enrichmentService.enrichAll(findings, ruleById);

        // Sanity: every finding must have been enriched (cloud should not fail on this fixture)
        assertThat(results)
                .as("Cloud model must enrich all findings in the test-legacy fixture")
                .hasSize(findings.size());

        results.forEach((findingId, fix) -> {
            VersionExpectation exp = expected.get(findingId);

            // ── Print for manual review ────────────────────────────────────────
            System.out.printf("%n[%s] rule=%s severity=%s%n",
                    findingId, fixture.stream()
                            .filter(f -> f.finding().getFindingId().equals(findingId))
                            .findFirst().map(f -> f.finding().getRuleId()).orElse("?"),
                    fixture.stream()
                            .filter(f -> f.finding().getFindingId().equals(findingId))
                            .findFirst().map(f -> f.finding().getSeverity().name()).orElse("?"));
            System.out.printf("model    : %s%n", fix.model().modelName());
            System.out.printf("explanation: %s%n", fix.explanation());
            if (exp != null) {
                System.out.printf("expected version facts: introduced=%s, hardBreak=%s%n",
                        exp.introducedIn(), exp.hardBreak());
            }
            System.out.println("──────────────────────────────────────────────────");

            // ── Assertions ────────────────────────────────────────────────────

            assertThat(fix.explanation())
                    .as("Explanation for %s must not be blank", findingId)
                    .isNotBlank();

            assertThat(fix.explanation())
                    .as("Explanation must not contain raw [INST] artefacts (prompt leakage)")
                    .doesNotContain("[INST]", "[/INST]", "<<SYS>>", "<</SYS>>");

            assertThat(fix.model().modelName())
                    .as("Model attribution must be set")
                    .isNotBlank();

            // Version fact check: explanation must not contradict the known correct versions
            if (exp != null) {
                for (String wrongVersion : exp.wrongVersions()) {
                    assertThat(fix.explanation())
                            .as("Explanation for %s (rule=%s) must not hallucinate '%s' — " +
                                            "correct facts: introduced=%s, hardBreak=%s",
                                    findingId,
                                    fixture.stream()
                                            .filter(f -> f.finding().getFindingId().equals(findingId))
                                            .findFirst().map(f -> f.finding().getRuleId()).orElse("?"),
                                    wrongVersion,
                                    exp.introducedIn(),
                                    exp.hardBreak())
                            .doesNotContainIgnoringCase(wrongVersion);
                }
            }
        });

        System.out.println("\n══ Validation complete ══\n");
    }

    // ─── Test-legacy fixture ──────────────────────────────────────────────────

    /**
     * Reconstructs the test-legacy fixture used in {@code ScanResourceTest}.
     * These findings collectively produce risk score 66 — the regression anchor.
     *
     * <p>Each entry also declares {@code VersionExpectation} — the correct version
     * facts and the wrong strings the model should NOT produce.
     */
    private static List<FindingWithRule> testLegacyFixture() {
        return List.of(
                // ── JPMS violations — highest weight ──────────────────────────
                findingWithRule(
                        "JPMS_SUN_IMPORT",
                        "import sun.misc.BASE64Encoder;",
                        "Use java.util.Base64 (available since Java 8)",
                        Severity.BREAKING, Effort.HIGH,
                        JavaVersion.V9, null,
                        new VersionExpectation("Java 9", "no hard removal date",
                                List.of("removed in Java 11", "removed in Java 17", "removed in Java 21"))
                ),
                findingWithRule(
                        "JPMS_UNSAFE_USAGE",
                        "sun.misc.Unsafe unsafe = sun.misc.Unsafe.getUnsafe();",
                        "Use VarHandles (java.lang.invoke.VarHandle) for atomic operations",
                        Severity.BREAKING, Effort.MANUAL,
                        JavaVersion.V9, null,
                        new VersionExpectation("Java 9", "no hard removal date",
                                List.of("removed in Java 8", "removed in Java 11"))
                ),
                findingWithRule(
                        "JPMS_ILLEGAL_REFLECTIVE_ACCESS",
                        "Field f = String.class.getDeclaredField(\"value\"); f.setAccessible(true);",
                        "Add --add-opens java.base/java.lang=ALL-UNNAMED or refactor to avoid reflection",
                        Severity.BREAKING, Effort.HIGH,
                        JavaVersion.V9, JavaVersion.V17,
                        new VersionExpectation("Java 9", "Java 17",
                                List.of("removed in Java 11", "removed in Java 21", "removed in Java 25"))
                ),

                // ── API removals ───────────────────────────────────────────────
                findingWithRule(
                        "API_JAXB_REMOVED",
                        "import javax.xml.bind.JAXBContext;",
                        "Add dependency: jakarta.xml.bind:jakarta.xml.bind-api:4.0.0",
                        Severity.BREAKING, Effort.LOW,
                        JavaVersion.V9, JavaVersion.V11,
                        new VersionExpectation("Java 9 (deprecated)", "Java 11",
                                List.of("removed in Java 8", "removed in Java 17", "removed in Java 21"))
                ),
                findingWithRule(
                        "API_JAX_WS_REMOVED",
                        "import javax.xml.ws.Service;",
                        "Add dependency: jakarta.xml.ws:jakarta.xml.ws-api:4.0.0",
                        Severity.BREAKING, Effort.LOW,
                        JavaVersion.V9, JavaVersion.V11,
                        new VersionExpectation("Java 9 (deprecated)", "Java 11",
                                List.of("removed in Java 8", "removed in Java 17", "removed in Java 21"))
                ),

                // ── Deprecations ───────────────────────────────────────────────
                findingWithRule(
                        "DEPRECATION_SECURITY_MANAGER",
                        "System.setSecurityManager(new SecurityManager());",
                        "Remove SecurityManager usage — no direct replacement; use OS-level sandboxing",
                        Severity.DEPRECATED, Effort.MANUAL,
                        JavaVersion.V17, JavaVersion.V21,
                        new VersionExpectation("Java 17", "Java 21",
                                List.of("removed in Java 11", "removed in Java 25"))
                ),
                findingWithRule(
                        "DEPRECATION_FINALIZE",
                        "@Override protected void finalize() { cleanup(); }",
                        "Use Cleaner (java.lang.ref.Cleaner) or try-with-resources instead",
                        Severity.DEPRECATED, Effort.MEDIUM,
                        JavaVersion.V9, JavaVersion.V21,
                        new VersionExpectation("Java 9", "Java 21",
                                List.of("removed in Java 11", "removed in Java 17"))
                ),

                // ── Modernization ──────────────────────────────────────────────
                findingWithRule(
                        "LANGUAGE_OLD_DATETIME_API",
                        "Date date = new Date(); Calendar cal = Calendar.getInstance();",
                        "Use java.time.LocalDate / ZonedDateTime (available since Java 8)",
                        Severity.MODERNIZATION, Effort.LOW,
                        JavaVersion.V8, null,
                        new VersionExpectation("Java 8 (modern alternative available)", "not removed",
                                List.of("removed in Java 11", "removed in Java 17",
                                        "removed in Java 21", "removed in Java 25"))
                )
        );
    }

    // ─── Fixture builder helpers ──────────────────────────────────────────────

    private static FindingWithRule findingWithRule(
            String ruleId,
            String originalCode,
            String suggestedCode,
            Severity severity,
            Effort effort,
            JavaVersion introducedIn,
            JavaVersion removedIn,
            VersionExpectation expectation
    ) {
        String findingId = UUID.randomUUID().toString();

        Finding finding = Finding.builder()
                .findingId(findingId)
                .ruleId(ruleId)
                .severity(severity)
                .filePath("/test/legacy/src/main/java/com/example/Legacy.java")
                .lineNumber(1)
                .originalCode(originalCode)
                .confidence(1.0f)
                .referenceUrl("https://openjdk.org/jeps/0")  // placeholder
                .status(Finding.FindingStatus.OPEN)
                .build();

        FixSuggestion fixSuggestion = FixSuggestion.builder()
                .fixType(effort == Effort.MANUAL ? FixType.MANUAL : FixType.API_REPLACE)
                .originalCode(originalCode)
                .suggestedCode(suggestedCode)
                .explanation("")    // empty — this is what we're enriching
                .autoApplicable(effort == Effort.TRIVIAL || effort == Effort.LOW)
                .build();

        MigrationRule rule = MigrationRule.builder()
                .ruleId(ruleId)
                .category(categoryFor(ruleId))
                .severity(severity)
                .effort(effort)
                .introducedIn(introducedIn)
                .removedIn(removedIn)
                .patterns(List.of(
                        DetectionPattern.builder()
                                .type(PatternType.IMPORT)
                                .matcher(originalCode)
                                .confidence(1.0f)
                                .build()))
                .fixTemplate(fixSuggestion)
                .referenceUrl("https://openjdk.org/jeps/0")
                .build();

        return new FindingWithRule(finding, rule, expectation);
    }

    private static RuleCategory categoryFor(String ruleId) {
        if (ruleId.startsWith("JPMS_")) return RuleCategory.JPMS;
        if (ruleId.startsWith("API_")) return RuleCategory.API_REMOVAL;
        if (ruleId.startsWith("DEPRECATION_")) return RuleCategory.DEPRECATION;
        if (ruleId.startsWith("LANGUAGE_")) return RuleCategory.LANGUAGE;
        if (ruleId.startsWith("CONCURRENCY_")) return RuleCategory.CONCURRENCY;
        if (ruleId.startsWith("BUILD_")) return RuleCategory.BUILD;
        return RuleCategory.LANGUAGE;
    }

    // ─── Inner types ──────────────────────────────────────────────────────────

    record FindingWithRule(Finding finding, MigrationRule rule, VersionExpectation expectation) {
    }

    /**
     * Declares the correct version facts for a finding and the wrong strings that
     * would indicate version hallucination in the model's explanation.
     */
    record VersionExpectation(
            String introducedIn,
            String hardBreak,
            List<String> wrongVersions   // strings that must NOT appear in the explanation
    ) {
    }

    // ─── Test profile ─────────────────────────────────────────────────────────

    /**
     * Overrides config to enable cloud fallback for this test class only.
     * The ANTHROPIC_API_KEY is read from the environment — not hardcoded.
     */
    public static class CloudValidationProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "anneal.llm.allow-cloud-fallback", "true",
                    "anneal.llm.enrichment-enabled", "true",
                    "anneal.llm.timeout-seconds", "60",
                    // Read API key from env — QuarkusTestProfile can reference env vars
                    "anneal.llm.anthropic.api-key", System.getenv().getOrDefault("ANTHROPIC_API_KEY", "")
            );
        }
    }
}
