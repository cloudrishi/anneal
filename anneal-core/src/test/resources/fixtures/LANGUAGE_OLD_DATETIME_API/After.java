package fixtures.language;

import java.time.Instant;
import java.time.ZonedDateTime;

/**
 * Ground truth fixture — AFTER migration.
 * Rule: LANGUAGE_OLD_DATETIME_API
 * Fix:
 *   new Date()             -> Instant.now()
 *   new Date().getTime()   -> Instant.now().toEpochMilli()
 *   Calendar.getInstance() -> ZonedDateTime.now()
 * AutoApplicable: true
 */
public class DateTimeAfter {

    public Instant now() {
        return Instant.now();
    }

    public long currentTimeMillis() {
        return Instant.now().toEpochMilli();
    }

    public ZonedDateTime calendar() {
        return ZonedDateTime.now();
    }
}
