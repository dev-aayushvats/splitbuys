package com.splitbuys.backend.split;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/** Greedy debt simplification: at most (people - 1) transfers. */
public final class DebtSimplifier {

    public record Transfer(long fromUserId, long toUserId, long amountPaise) {
    }

    private record Entry(long userId, long amount) {
    }

    private DebtSimplifier() {
    }

    /** @param net paise per user id; positive means the user is owed money, negative means they owe */
    public static List<Transfer> simplify(Map<Long, Long> net) {
        Comparator<Entry> largestFirst = Comparator.comparingLong(Entry::amount).reversed()
                .thenComparingLong(Entry::userId);
        PriorityQueue<Entry> creditors = new PriorityQueue<>(largestFirst);
        PriorityQueue<Entry> debtors = new PriorityQueue<>(largestFirst);
        net.forEach((id, amount) -> {
            if (amount > 0) {
                creditors.add(new Entry(id, amount));
            } else if (amount < 0) {
                debtors.add(new Entry(id, -amount));
            }
        });

        List<Transfer> transfers = new ArrayList<>();
        while (!creditors.isEmpty() && !debtors.isEmpty()) {
            Entry credit = creditors.poll();
            Entry debt = debtors.poll();
            long settled = Math.min(credit.amount(), debt.amount());
            transfers.add(new Transfer(debt.userId(), credit.userId(), settled));
            if (credit.amount() > settled) {
                creditors.add(new Entry(credit.userId(), credit.amount() - settled));
            }
            if (debt.amount() > settled) {
                debtors.add(new Entry(debt.userId(), debt.amount() - settled));
            }
        }
        return transfers;
    }
}
