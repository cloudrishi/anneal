package fixtures.jpms;

import sun.misc.BASE64Encoder;

/**
 * Ground truth fixture — BEFORE migration.
 * Rule: JPMS_SUN_IMPORT
 * Issue: sun.misc.BASE64Encoder is an internal JDK API encapsulated by JPMS in Java 9+.
 */
public class Base64Before {

    public String encode(byte[] data) {
        return new BASE64Encoder().encode(data);
    }
}
