package com.splitbuys.backend.ledger;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Calendar months in Indian Standard Time, as the instants they start at. Months are cut in IST, not UTC, so something added at
 * 1 a.m. on the 1st counts in the new month for the people who use the app (UTC would file it under the old one).
 */
final class IstMonths {
    static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private IstMonths() {
    }

    /** The {@code count} months ending with the one that contains {@code now}, oldest first. */
    static List<YearMonth> lastMonths(Instant now, int count) {
        YearMonth current = YearMonth.from(now.atZone(IST));
        List<YearMonth> months = new ArrayList<>();
        for (int i = count - 1; i >= 0; i--) {
            months.add(current.minusMonths(i));
        }
        return months;
    }

    /** {@code months.size() + 1} instants: the start of each month, then the start of the month after the last. */
    static List<Instant> boundaries(List<YearMonth> months) {
        List<Instant> bounds = new ArrayList<>();
        for (YearMonth m : months) {
            bounds.add(m.atDay(1).atStartOfDay(IST).toInstant());
        }
        bounds.add(months.get(months.size() - 1).plusMonths(1).atDay(1).atStartOfDay(IST).toInstant());
        return bounds;
    }
}
