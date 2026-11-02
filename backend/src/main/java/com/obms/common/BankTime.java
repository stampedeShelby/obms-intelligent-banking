package com.obms.common;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * The single source of truth for "what day is it" in this bank.
 *
 * <p>Every business date in OBMS is a date in the bank's own operating zone,
 * not in whatever zone the server happens to be configured with. That
 * distinction is not academic: the application runs in a Docker container set
 * to UTC, so between 18:30 and 00:00 UTC the server's calendar date is already
 * tomorrow in IST - or, read the other way, a transaction made at 00:30 IST
 * falls on the previous UTC day.</p>
 *
 * <p>Using {@code LocalDate.now()} anywhere in business logic therefore
 * produces a date that is wrong for part of every day. It caused two real
 * defects before this class existed: customer-visible transaction references
 * were stamped with the previous day's date for transactions made after
 * midnight IST, and the upcoming-bills window was off by one day over the same
 * period. A statement test was also passing only because CI happened to run
 * before 18:30 UTC.</p>
 */
public final class BankTime {

    /** The bank operates in India; all business dates are IST. */
    public static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private BankTime() {
        // utility class
    }

    /**
     * @return today's date as the bank reckons it
     */
    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    /**
     * @return the instant at which the bank's current day began
     */
    public static Instant startOfToday() {
        return today().atStartOfDay(ZONE).toInstant();
    }
}
