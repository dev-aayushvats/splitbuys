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
        return switch (type) {
            case EQUAL -> proportional(total, equalWeights(values));
            case SHARES -> proportional(total, requireNonNegative(values, "Shares"));
            case PERCENT -> {
                requireNonNegative(values, "Percentages");
                if (sum(values) != FULL_PERCENT_BP) {
                    throw new IllegalArgumentException("Percentages must add up to 100");
                }
                yield proportional(total, values);
            }
            case EXACT -> {
                requireNonNegative(values, "Amounts");
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

    private static Map<Long, Long> requireNonNegative(Map<Long, Long> values, String what) {
        for (Long v : values.values()) {
            if (v == null || v < 0) {
                throw new IllegalArgumentException(what + " cannot be negative");
            }
        }
        return values;
    }

    private static long sum(Map<Long, Long> values) {
        return values.values().stream().mapToLong(Long::longValue).sum();
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
