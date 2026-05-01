package fixtures.jpms;

import java.util.Base64;

/**
 * Ground truth fixture — AFTER migration.
 * Rule: JPMS_SUN_IMPORT
 * Fix: Replace sun.misc.BASE64Encoder with java.util.Base64 (available since Java 8).
 * AutoApplicable: true — import replacement is deterministic.
 */
public class Base64After {

    public String encode(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }
}
