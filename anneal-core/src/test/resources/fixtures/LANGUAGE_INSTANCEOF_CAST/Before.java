package fixtures.language;

/**
 * Ground truth fixture — BEFORE migration.
 * Rule: LANGUAGE_INSTANCEOF_CAST
 * Issue: instanceof check followed by explicit cast — redundant in Java 16+.
 */
public class InstanceofBefore {

    public void process(Object obj) {
        if (obj instanceof String) {
            String s = (String) obj;
            System.out.println(s.toUpperCase());
        }

        if (obj instanceof Integer) {
            Integer i = (Integer) obj;
            System.out.println(i * 2);
        }
    }
}
