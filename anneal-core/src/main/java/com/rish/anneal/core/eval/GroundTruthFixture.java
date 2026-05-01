package com.rish.anneal.core.eval;

/**
 * A ground truth fixture pair for a single migration rule.
 *
 * <p>Each fixture consists of a {@code before} Java source file (the code that triggers
 * the rule) and an {@code after} Java source file (the correct migrated version).
 * The {@code after} source is the ground truth — the eval layer verifies that the
 * suggested fix produces code that compiles as cleanly as the {@code after} file.
 *
 * <p>Fixtures live in {@code anneal-core/src/test/resources/fixtures/{ruleId}/}:
 * <pre>
 * fixtures/
 * ├── JPMS_SUN_IMPORT/
 * │   ├── Before.java
 * │   └── After.java
 * ├── API_JAXB_REMOVED/
 * │   ├── Before.java
 * │   └── After.java
 * ...
 * </pre>
 *
 * @param ruleId       the rule this fixture covers e.g. JPMS_SUN_IMPORT
 * @param beforeSource the Java source that triggers the rule (the "broken" state)
 * @param afterSource  the Java source after correct migration (the ground truth)
 */
public record GroundTruthFixture(
        String ruleId,
        String beforeSource,
        String afterSource
) {
    /**
     * Loads a fixture from the classpath resources directory.
     * Expects files at {@code fixtures/{ruleId}/Before.java} and
     * {@code fixtures/{ruleId}/After.java}.
     *
     * @param ruleId the rule identifier
     * @return loaded fixture
     * @throws IllegalArgumentException if the fixture files are not found
     */
    public static GroundTruthFixture load(String ruleId) {
        String beforePath = "fixtures/" + ruleId + "/Before.java";
        String afterPath  = "fixtures/" + ruleId + "/After.java";

        String before = loadResource(beforePath);
        String after  = loadResource(afterPath);

        return new GroundTruthFixture(ruleId, before, after);
    }

    private static String loadResource(String path) {
        try (var stream = GroundTruthFixture.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalArgumentException(
                        "Fixture not found on classpath: " + path);
            }
            return new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException(
                    "Failed to read fixture: " + path, e);
        }
    }
}
