package com.splitbuys.backend.split;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a total and per-person inputs into per-person owed amounts, all in paise.
 * Leftover paise are handed out by largest remainder, so shares always sum to the total.
 */
public final class SplitCalculator {

    public static final long FULL_PERCENT_BP = 10_000;
    /** Far more than any real split needs, and small enough that weight x amount can never overflow a long. */
    public static final long MAX_SHARE_WEIGHT = 1_000_000;

    private SplitCalculator() {
    }

    /**
     * @param values keyed by user id (insertion order breaks ties). Meaning depends on type:
     *               EQUAL: ignored (only the keys matter), EXACT: paise,
     *               PERCENT: basis points (1% = 100), SHARES: weights.
     * @return owed paise per user id; users who owe nothing are omitted
     */
    public static Map<Long, Long> calculate(SplitType type, long total, Map<Long, Long> values) {
        if (total <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        if (values.isEmpty()) {
            throw new IllegalArgumentException("Select at least one person to split with");
        }
        try {
            return split(type, total, values);
        } catch (ArithmeticException e) {
            // defence in depth: the per-value limits below should already make this unreachable
            throw new IllegalArgumentException("The amount or split values are too large");
        }
    }

    private static Map<Long, Long> split(SplitType type, long total, Map<Long, Long> values) {
        // Every value is bounded BEFORE anything is added or multiplied. Unbounded client input would otherwise let three
        // huge values add up (by wrapping around) to exactly the total and slip past the "must add up" checks.
        return switch (type) {
            case EQUAL -> proportional(total, equalWeights(values));
            case SHARES -> proportional(total, requireInRange(values, "Shares", MAX_SHARE_WEIGHT, "Shares cannot be more than 1,000,000 each"));
            case PERCENT -> {
                requireInRange(values, "Percentages", FULL_PERCENT_BP, "A percentage cannot be more than 100");
                if (sum(values) != FULL_PERCENT_BP) {
                    throw new IllegalArgumentException("Percentages must add up to 100");
                }
                yield proportional(total, values);
            }
            case EXACT -> {
                requireInRange(values, "Amounts", total, "An amount cannot be more than the total");
                if (sum(values) != total) {
                    throw new IllegalArgumentException("Amounts must add up to the total");
                }
                Map<Long, Long> result = new LinkedHashMap<>();
                values.forEach((id, v) -> {
                    if (v > 0) {
                        result.put(id, v);
                    }
                });
                yield result;
            }
        };
    }

    private static Map<Long, Long> equalWeights(Map<Long, Long> values) {
        Map<Long, Long> weights = new LinkedHashMap<>();
        values.keySet().forEach(id -> weights.put(id, 1L));
        return weights;
    }

    private static Map<Long, Long> requireInRange(Map<Long, Long> values, String what, long max, String tooBig) {
        for (Long v : values.values()) {
            if (v == null || v < 0) {
                throw new IllegalArgumentException(what + " cannot be negative");
            }
            if (v > max) {
                throw new IllegalArgumentException(tooBig);
            }
        }
        return values;
    }

    private static long sum(Map<Long, Long> values) {
        long total = 0;
        for (long v : values.values()) {
            total = Math.addExact(total, v); // never wrap silently
        }
        return total;
    }

    private static Map<Long, Long> proportional(long total, Map<Long, Long> weights) {
        long weightSum = sum(weights);
        if (weightSum <= 0) {
            throw new IllegalArgumentException("Split weights must add up to more than zero");
        }
        record Part(Long id, long floor, long remainder) {
        }
        List<Part> parts = new ArrayList<>();
        long assigned = 0;
        for (Map.Entry<Long, Long> e : weights.entrySet()) {
            long product = Math.multiplyExact(total, e.getValue());
            long floor = product / weightSum;
            assigned += floor;
            parts.add(new Part(e.getKey(), floor, product % weightSum));
        }
        long leftover = total - assigned;
        // stable sort: equal remainders keep input order
        List<Part> byRemainder = new ArrayList<>(parts);
        byRemainder.sort(Comparator.comparingLong(Part::remainder).reversed());
        Map<Long, Long> bonus = new LinkedHashMap<>();
        for (int i = 0; i < leftover; i++) {
            bonus.put(byRemainder.get(i).id(), 1L);
        }
        Map<Long, Long> result = new LinkedHashMap<>();
        for (Part p : parts) {
            long owed = p.floor() + bonus.getOrDefault(p.id(), 0L);
            if (owed > 0) {
                result.put(p.id(), owed);
            }
        }
        return result;
    }
}
