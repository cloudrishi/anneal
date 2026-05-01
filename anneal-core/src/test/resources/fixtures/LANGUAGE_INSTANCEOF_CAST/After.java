package fixtures.language;

/**
 * Ground truth fixture — AFTER migration.
 * Rule: LANGUAGE_INSTANCEOF_CAST
 * Fix: Replace instanceof + cast with pattern matching instanceof (Java 16+).
 * AutoApplicable: true
 */
public class InstanceofAfter {

    public void process(Object obj) {
        if (obj instanceof String s) {
            System.out.println(s.toUpperCase());
        }

        if (obj instanceof Integer i) {
            System.out.println(i * 2);
        }
    }
}
