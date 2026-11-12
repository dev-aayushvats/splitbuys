package com.splitbuys.backend.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

class IstMonthsTest {
    @Test
    void theMonthIsTheOneItIsInIndiaNotInUtc() {
        // 30 Nov 19:00 UTC is 1 Dec 00:30 in India: already December there
        assertEquals(YearMonth.of(2026, 12), IstMonths.lastMonths(Instant.parse("2026-11-30T19:00:00Z"), 1).get(0));
        // 30 Nov 18:29 UTC is still 30 Nov 23:59 in India
        assertEquals(YearMonth.of(2026, 11), IstMonths.lastMonths(Instant.parse("2026-11-30T18:29:59Z"), 1).get(0));
    }

    @Test
    void sixMonthsEndWithTheCurrentOneAndCrossTheYear() {
        assertEquals(List.of(YearMonth.of(2026, 8), YearMonth.of(2026, 9), YearMonth.of(2026, 10), YearMonth.of(2026, 11),
                YearMonth.of(2026, 12), YearMonth.of(2027, 1)), IstMonths.lastMonths(Instant.parse("2027-01-15T06:00:00Z"), 6));
    }

    @Test
    void boundariesAreIstMidnightsOneMoreThanMonths() {
        List<Instant> b = IstMonths.boundaries(List.of(YearMonth.of(2026, 11), YearMonth.of(2026, 12)));
        assertEquals(List.of(Instant.parse("2026-10-31T18:30:00Z"), Instant.parse("2026-11-30T18:30:00Z"), Instant.parse("2026-12-31T18:30:00Z")), b);
    }
}
