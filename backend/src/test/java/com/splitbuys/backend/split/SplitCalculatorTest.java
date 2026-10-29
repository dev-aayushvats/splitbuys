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
}
