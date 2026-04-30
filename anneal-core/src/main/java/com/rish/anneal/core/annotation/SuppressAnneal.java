package com.rish.anneal.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Suppresses one or more anneal migration findings on the annotated element.
 *
 * <p>Suppressed findings still appear in the scan report with
 * {@code status: SUPPRESSED} — they are not erased. They are excluded from
 * the risk score calculation but remain visible in the history view so the
 * decision is auditable.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Suppress all anneal findings on this element
 * @SuppressAnneal
 * public class LegacyBridge { ... }
 *
 * // Suppress a specific rule on a method
 * @SuppressAnneal("JPMS_ILLEGAL_REFLECTIVE_ACCESS")
 * public void reflect() throws Exception {
 *     field.setAccessible(true);
 * }
 *
 * // Suppress multiple rules
 * @SuppressAnneal({"JPMS_SUN_IMPORT", "JPMS_UNSAFE_USAGE"})
 * public class LegacyUnsafeWrapper { ... }
 * }</pre>
 *
 * <h2>Scope</h2>
 * The rule engine checks the annotated element in this order:
 * <ol>
 *   <li>The enclosing method or constructor of the matched node</li>
 *   <li>The enclosing type (class, interface, or enum)</li>
 * </ol>
 * If either has {@code @SuppressAnneal} with a matching ruleId (or no ruleId,
 * meaning suppress all), the finding is recorded as {@code SUPPRESSED} rather
 * than {@code OPEN}.
 *
 * <h2>Why SOURCE retention is wrong here</h2>
 * The annotation is read by anneal's AST scanner via JavaParser — not by the
 * Java compiler or runtime. {@code RetentionPolicy.SOURCE} would cause the
 * annotation to be stripped before the compiled class is emitted, but anneal
 * reads the .java source file directly, so retention policy has no effect on
 * anneal's detection. {@code RUNTIME} retention is used so the annotation is
 * also available on compiled classes if needed by future tooling.
 */
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR,
         ElementType.FIELD, ElementType.LOCAL_VARIABLE})
@Retention(RetentionPolicy.RUNTIME)
public @interface SuppressAnneal {

    /**
     * The ruleId(s) to suppress. If empty, all anneal findings on the annotated
     * element are suppressed.
     *
     * <p>Examples: {@code "JPMS_SUN_IMPORT"}, {@code {"JPMS_SUN_IMPORT", "JPMS_UNSAFE_USAGE"}}
     */
    String[] value() default {};
}
