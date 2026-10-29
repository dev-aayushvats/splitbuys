package com.splitbuys.backend.split;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.splitbuys.backend.split.DebtSimplifier.Transfer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DebtSimplifierTest {

    @Test
    void emptyWhenEveryoneIsSettled() {
        assertTrue(DebtSimplifier.simplify(Map.of(1L, 0L, 2L, 0L)).isEmpty());
    }

    @Test
    void singleDebt() {
        List<Transfer> t = DebtSimplifier.simplify(Map.of(1L, 500L, 2L, -500L));
        assertEquals(List.of(new Transfer(2, 1, 500)), t);
    }

    @Test
    void chainCollapses() {
        // A owes B 10, B owes C 10 -> A pays C 10
        List<Transfer> t = DebtSimplifier.simplify(Map.of(1L, -10L, 2L, 0L, 3L, 10L));
        assertEquals(List.of(new Transfer(1, 3, 10)), t);
    }

    @Test
    void usesAtMostPeopleMinusOneTransfersAndSettlesEveryone() {
        Map<Long, Long> net = Map.of(1L, 700L, 2L, 300L, 3L, -400L, 4L, -250L, 5L, -350L);
        List<Transfer> t = DebtSimplifier.simplify(net);
        assertTrue(t.size() <= net.size() - 1);

        Map<Long, Long> after = new HashMap<>(net);
        for (Transfer x : t) {
            after.merge(x.fromUserId(), x.amountPaise(), Long::sum);
            after.merge(x.toUserId(), -x.amountPaise(), Long::sum);
        }
        after.values().forEach(v -> assertEquals(0L, v));
    }
}
