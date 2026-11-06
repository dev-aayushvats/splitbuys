package com.splitbuys.backend.split;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SplitCalculatorTest {

    private static Map<Long, Long> of(long... idValuePairs) {
        Map<Long, Long> m = new LinkedHashMap<>();
        for (int i = 0; i < idValuePairs.length; i += 2) {
            m.put(idValuePairs[i], idValuePairs[i + 1]);
        }
        return m;
    }

    private static long total(Map<Long, Long> m) {
        return m.values().stream().mapToLong(Long::longValue).sum();
    }

    @Test
    void equalSplitGivesLeftoverPaiseToEarliestPeople() {
        Map<Long, Long> r = SplitCalculator.calculate(SplitType.EQUAL, 100, of(1, 0, 2, 0, 3, 0));
        assertEquals(of(1, 34, 2, 33, 3, 33), r);
    }

    @Test
    void equalSplitAlwaysSumsToTotal() {
        for (long total = 1; total < 500; total++) {
            for (int people = 1; people <= 7; people++) {
                Map<Long, Long> in = new LinkedHashMap<>();
                for (long p = 1; p <= people; p++) {
                    in.put(p, 0L);
                }
                assertEquals(total, total(SplitCalculator.calculate(SplitType.EQUAL, total, in)));
            }
        }
    }

    @Test
    void percentSplit() {
        Map<Long, Long> r = SplitCalculator.calculate(SplitType.PERCENT, 1000, of(1, 5000, 2, 3000, 3, 2000));
        assertEquals(of(1, 500, 2, 300, 3, 200), r);
    }

    @Test
    void percentSplitWithAwkwardTotalStillSums() {
        Map<Long, Long> r = SplitCalculator.calculate(SplitType.PERCENT, 101, of(1, 3333, 2, 3333, 3, 3334));
        assertEquals(101, total(r));
    }

    @Test
    void percentMustAddUpToHundred() {
        assertThrows(IllegalArgumentException.class,
                () -> SplitCalculator.calculate(SplitType.PERCENT, 1000, of(1, 5000, 2, 4000)));
    }

    @Test
    void exactMustMatchTotal() {
        assertEquals(of(1, 700, 2, 300), SplitCalculator.calculate(SplitType.EXACT, 1000, of(1, 700, 2, 300)));
        assertThrows(IllegalArgumentException.class,
                () -> SplitCalculator.calculate(SplitType.EXACT, 1000, of(1, 700, 2, 200)));
    }

    @Test
    void sharesSplitProportionally() {
        Map<Long, Long> r = SplitCalculator.calculate(SplitType.SHARES, 1000, of(1, 2, 2, 1, 3, 1));
        assertEquals(of(1, 500, 2, 250, 3, 250), r);
    }

    @Test
    void rejectsBadInput() {
        assertThrows(IllegalArgumentException.class, () -> SplitCalculator.calculate(SplitType.EQUAL, 0, of(1, 0)));
        assertThrows(IllegalArgumentException.class, () -> SplitCalculator.calculate(SplitType.EQUAL, 100, of()));
        assertThrows(IllegalArgumentException.class, () -> SplitCalculator.calculate(SplitType.SHARES, 100, of(1, -1, 2, 2)));
        assertThrows(IllegalArgumentException.class, () -> SplitCalculator.calculate(SplitType.SHARES, 100, of(1, 0, 2, 0)));
    }

    @Test
    void valuesThatWouldWrapAroundToTheTotalAreRejected() {
        // three values summing to 2^64 + 100 wrap to exactly 100 in a long: the old "amounts add up" check accepted this
        long big = Long.MAX_VALUE;
        assertThrows(IllegalArgumentException.class,
                () -> SplitCalculator.calculate(SplitType.EXACT, 100, of(1, big, 2, big, 3, 102)));
    }

    @Test
    void everyValueIsBoundedPerSplitType() {
        assertThrows(IllegalArgumentException.class, () -> SplitCalculator.calculate(SplitType.EXACT, 100, of(1, 101, 2, 0)));
        assertThrows(IllegalArgumentException.class, () -> SplitCalculator.calculate(SplitType.PERCENT, 100, of(1, 10_001, 2, 0)));
        assertThrows(IllegalArgumentException.class,
                () -> SplitCalculator.calculate(SplitType.SHARES, 100, of(1, SplitCalculator.MAX_SHARE_WEIGHT + 1, 2, 1)));
        // the limits themselves are fine
        assertEquals(of(1, 100), SplitCalculator.calculate(SplitType.EXACT, 100, of(1, 100)));
        assertEquals(of(1, 100), SplitCalculator.calculate(SplitType.PERCENT, 100, of(1, 10_000)));
        assertEquals(100, total(SplitCalculator.calculate(SplitType.SHARES, 100, of(1, SplitCalculator.MAX_SHARE_WEIGHT, 2, 1))));
    }

    @Test
    void anAbsurdTotalIsAClientErrorNotAnArithmeticException() {
        assertThrows(IllegalArgumentException.class,
                () -> SplitCalculator.calculate(SplitType.SHARES, Long.MAX_VALUE, of(1, 1_000_000, 2, 999_999)));
    }

    @Test
    void whateverIsAcceptedAlwaysSumsExactlyToTheTotal() {
        // seeded, so a failure is reproducible; hostile and boundary values are mixed in with ordinary ones
        java.util.Random random = new java.util.Random(42);
        long[] edge = {0, 1, 2, 99, 100, 101, 9_999, 10_000, 10_001, 999_999, 1_000_000, 1_000_001, Long.MAX_VALUE};
        int accepted = 0;
        for (int round = 0; round < 20_000; round++) {
            SplitType type = SplitType.values()[random.nextInt(SplitType.values().length)];
            long total = random.nextBoolean() ? 1 + random.nextInt(1_000_000) : edge[random.nextInt(edge.length)];
            int people = 1 + random.nextInt(6);
            Map<Long, Long> in = new LinkedHashMap<>();
            for (long id = 1; id <= people; id++) {
                in.put(id, random.nextInt(4) == 0 ? edge[random.nextInt(edge.length)] : random.nextInt(11_000));
            }
            try {
                Map<Long, Long> out = SplitCalculator.calculate(type, total, in);
                accepted++;
                assertEquals(total, out.values().stream().mapToLong(Long::longValue).sum(), type + " " + total + " " + in);
                out.values().forEach(v -> org.junit.jupiter.api.Assertions.assertTrue(v > 0, "no zero or negative shares: " + out));
            } catch (IllegalArgumentException expected) {
                // rejected cleanly: the only acceptable way to refuse (any other exception type fails the test)
            }
        }
        org.junit.jupiter.api.Assertions.assertTrue(accepted > 1_000, "the property was actually exercised: " + accepted);
    }
}
