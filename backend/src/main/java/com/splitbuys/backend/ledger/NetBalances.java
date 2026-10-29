package com.splitbuys.backend.ledger;

import com.splitbuys.backend.group.LedgerPort;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Who is owed or owes what, computed in the database with aggregate queries (four, however many groups are asked
 * about) rather than by loading every expense. Implements the port the group feature depends on.
 */
@Component
class NetBalances implements LedgerPort {
    private final ExpenseRepository expenses;
    private final SettlementRepository settlements;

    NetBalances(ExpenseRepository expenses, SettlementRepository settlements) {
        this.expenses = expenses;
        this.settlements = settlements;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, Map<Long, Long>> netBalances(Collection<Long> groupIds) {
        Map<Long, Map<Long, Long>> net = new HashMap<>();
        if (groupIds.isEmpty()) {
            return net;
        }
        add(net, expenses.paidTotals(groupIds), 1);          // paid for the group: owed back
        add(net, expenses.owedTotals(groupIds), -1);         // own share of expenses: owes
        add(net, settlements.paidOut(groupIds), 1);          // paid someone: debt reduced
        add(net, settlements.receivedTotals(groupIds), -1);  // received a payment: credit reduced
        return net;
    }

    @Override
    @Transactional
    public void purge(long groupId) {
        expenses.deleteAll(expenses.findByGroup_Id(groupId));
        settlements.deleteAll(settlements.findByGroup_Id(groupId));
    }

    private static void add(Map<Long, Map<Long, Long>> net, List<AmountByUser> rows, int sign) {
        for (AmountByUser r : rows) {
            net.computeIfAbsent(r.groupId(), g -> new HashMap<>()).merge(r.userId(), sign * r.total(), Long::sum);
        }
    }
}
