package fixtures.language;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Ground truth fixture — BEFORE migration.
 * Rule: LANGUAGE_ANONYMOUS_CLASS_LAMBDA
 * Issue: Anonymous classes implementing functional interfaces should be lambdas.
 */
public class LambdaBefore {

    public void examples() {
        Runnable r = new Runnable() {
            @Override
            public void run() {
                System.out.println("running");
            }
        };

        List<String> list = Arrays.asList("banana", "apple", "cherry");
        list.sort(new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                return a.compareTo(b);
            }
        });
    }
}
