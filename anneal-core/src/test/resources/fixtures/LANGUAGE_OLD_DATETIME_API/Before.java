package fixtures.language;

import java.util.Date;
import java.util.Calendar;

/**
 * Ground truth fixture — BEFORE migration.
 * Rule: LANGUAGE_OLD_DATETIME_API
 * Issue: java.util.Date and Calendar are error-prone legacy APIs.
 */
public class DateTimeBefore {

    public Date now() {
        return new Date();
    }

    public long currentTimeMillis() {
        return new Date().getTime();
    }

    public Calendar calendar() {
        return Calendar.getInstance();
    }
}
