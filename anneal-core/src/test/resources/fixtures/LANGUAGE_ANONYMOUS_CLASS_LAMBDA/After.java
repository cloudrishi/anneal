package fixtures.language;

import java.util.Arrays;
import java.util.List;

/**
 * Ground truth fixture — AFTER migration.
 * Rule: LANGUAGE_ANONYMOUS_CLASS_LAMBDA
 * Fix: Replace anonymous classes with lambda expressions.
 * AutoApplicable: true
 */
public class LambdaAfter {

    public void examples() {
        Runnable r = () -> System.out.println("running");

        List<String> list = Arrays.asList("banana", "apple", "cherry");
        list.sort((a, b) -> a.compareTo(b));
    }
}
