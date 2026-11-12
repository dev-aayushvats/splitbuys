package com.splitbuys.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.splitbuys.backend.support.ApiTest;
import org.junit.jupiter.api.Test;

class HistoryApiTest extends ApiTest {
    private String url(long g) { return "/api/v1/groups/" + g; }

    private long add(long g, Session by, String what, long paise, Session payer, Session... sharers) throws Exception {
        long[] ids = new long[sharers.length];
        for (int i = 0; i < sharers.length; i++) ids[i] = sharers[i].userId();
        return id(post(url(g) + "/expenses", by, equalExpense(what, paise, payer.userId(), ids)).andExpect(status().isCreated()), "$.id");
    }

    @Test
    void creatingEditingAndDeletingAreAllRecordedNewestFirst() throws Exception {
        Session a = signIn("Asha"), b = signIn("Bo");
        long g = groupOf(a, b);
        long e = add(g, a, "Lunch", 600, a, a, b);
        put(url(g) + "/expenses/" + e, b, equalExpense("Big lunch", 800, b.userId(), a.userId(), b.userId())).andExpect(status().isOk());
        delete(url(g) + "/expenses/" + e, a).andExpect(status().isNoContent());

        get(url(g) + "/history", a).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].action").value("DELETED"))
                .andExpect(jsonPath("$.items[0].before.description").value("Big lunch"))
                .andExpect(jsonPath("$.items[0].after").doesNotExist())
                .andExpect(jsonPath("$.items[1].action").value("EDITED"))
                .andExpect(jsonPath("$.items[1].actor.id").value(b.userId()))
                .andExpect(jsonPath("$.items[1].before.amountPaise").value(600))
                .andExpect(jsonPath("$.items[1].after.amountPaise").value(800))
                .andExpect(jsonPath("$.items[1].after.payer.id").value(b.userId()))
                .andExpect(jsonPath("$.items[2].action").value("CREATED"))
                .andExpect(jsonPath("$.items[2].before").doesNotExist());
        // the history of a deleted expense is still reachable
        get(url(g) + "/expenses/" + e + "/history", b).andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void savingWithoutChangingAnythingLeavesNoEntry() throws Exception {
        Session a = signIn("Asha"), b = signIn("Bo");
        long g = groupOf(a, b);
        long e = add(g, a, "Tea", 100, a, a, b);
        put(url(g) + "/expenses/" + e, a, equalExpense("Tea", 100, a.userId(), a.userId(), b.userId())).andExpect(status().isOk());
        get(url(g) + "/history", a).andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void anEditCanBeUndoneAndBalancesGoBack() throws Exception {
        Session a = signIn("Asha"), b = signIn("Bo");
        long g = groupOf(a, b);
        long e = add(g, a, "Cab", 1000, a, a, b);
        put(url(g) + "/expenses/" + e, b, equalExpense("Cab", 2000, a.userId(), a.userId(), b.userId())).andExpect(status().isOk());
        long edit = id(get(url(g) + "/history", a), "$.items[0].id");
        get(url(g) + "/history", a).andExpect(jsonPath("$.items[0].undoable").value(true));

        post(url(g) + "/history/" + edit + "/undo", a, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("REVERTED"))
                .andExpect(jsonPath("$.after.amountPaise").value(1000));
        get(url(g) + "/expenses/" + e, a).andExpect(jsonPath("$.amountPaise").value(1000));
        // the edit can no longer be undone: it is not the latest change
        get(url(g) + "/history", a).andExpect(jsonPath("$.items[1].action").value("EDITED"))
                .andExpect(jsonPath("$.items[1].undoable").value(false));
        post(url(g) + "/history/" + edit + "/undo", a, "").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHANGED_SINCE"));
    }

    @Test
    void anEarlierEditCannotBeUndoneOverALaterOne() throws Exception {
        Session a = signIn("Asha"), b = signIn("Bo");
        long g = groupOf(a, b);
        long e = add(g, a, "Cab", 1000, a, a, b);
        put(url(g) + "/expenses/" + e, a, equalExpense("Cab", 1200, a.userId(), a.userId(), b.userId())).andExpect(status().isOk());
        long first = id(get(url(g) + "/history", a), "$.items[0].id");
        put(url(g) + "/expenses/" + e, b, equalExpense("Cab", 1500, a.userId(), a.userId(), b.userId())).andExpect(status().isOk());
        post(url(g) + "/history/" + first + "/undo", a, "").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHANGED_SINCE"));
        get(url(g) + "/expenses/" + e, a).andExpect(jsonPath("$.amountPaise").value(1500));
    }

    @Test
    void aDeletedExpenseCanBeRestoredExactlyOnce() throws Exception {
        Session a = signIn("Asha"), b = signIn("Bo");
        long g = groupOf(a, b);
        long e = add(g, a, "Dinner", 900, b, a, b);
        delete(url(g) + "/expenses/" + e, a).andExpect(status().isNoContent());
        get(url(g) + "/expenses", a).andExpect(jsonPath("$.items.length()").value(0));
        long del = id(get(url(g) + "/history", b), "$.items[0].id");

        var restored = post(url(g) + "/history/" + del + "/undo", b, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("RESTORED")).andExpect(jsonPath("$.after.description").value("Dinner"));
        long newId = json(restored, "$.expenseId") instanceof Number n ? n.longValue() : -1;
        assertTrue(newId > 0);
        get(url(g) + "/expenses", a).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].description").value("Dinner"))
                .andExpect(jsonPath("$.items[0].payer.id").value(b.userId()))
                .andExpect(jsonPath("$.items[0].shares.length()").value(2));
        get(url(g) + "/balances", a).andExpect(status().isOk());

        post(url(g) + "/history/" + del + "/undo", a, "").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_RESTORED"));
        get(url(g) + "/expenses", a).andExpect(jsonPath("$.items.length()").value(1));
        get(url(g) + "/history", a).andExpect(jsonPath("$.items[1].undoable").value(false));
    }

    @Test
    void anExpenseWithSomeoneWhoLeftCannotBeRestored() throws Exception {
        Session a = signIn("Asha"), b = signIn("Bo");
        long g = groupOf(a, b);
        long e = add(g, a, "Snacks", 400, a, a, b);
        delete(url(g) + "/expenses/" + e, a).andExpect(status().isNoContent());
        delete(url(g) + "/members/" + b.userId(), b).andExpect(status().is2xxSuccessful());
        long del = id(get(url(g) + "/history", a), "$.items[0].id");
        post(url(g) + "/history/" + del + "/undo", a, "").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBER_LEFT"));
    }

    @Test
    void creationsCannotBeUndoneAndOutsidersSeeNothing() throws Exception {
        Session a = signIn("Asha"), b = signIn("Bo"), outsider = signIn("Omar");
        long g = groupOf(a, b);
        add(g, a, "Tea", 100, a, a, b);
        long created = id(get(url(g) + "/history", a), "$.items[0].id");
        post(url(g) + "/history/" + created + "/undo", a, "").andExpect(status().isBadRequest());
        get(url(g) + "/history", outsider).andExpect(status().isNotFound());
        post(url(g) + "/history/" + created + "/undo", outsider, "").andExpect(status().isNotFound());
        // another group cannot reach this group's entries by id
        long other = groupOf(outsider);
        post(url(other) + "/history/" + created + "/undo", outsider, "").andExpect(status().isNotFound());
    }

    @Test
    void historyPagesWithoutRepeatsOrGaps() throws Exception {
        Session a = signIn("Asha"), b = signIn("Bo");
        long g = groupOf(a, b);
        for (int i = 0; i < 7; i++) add(g, a, "E" + i, 100 + i, a, a, b);
        java.util.List<Number> seen = new java.util.ArrayList<>();
        String cursor = null;
        for (int page = 0; page < 10; page++) {
            var r = get(url(g) + "/history?limit=3" + (cursor == null ? "" : "&cursor=" + cursor), a).andExpect(status().isOk());
            seen.addAll(json(r, "$.items[*].id"));
            cursor = json(r, "$.nextCursor");
            if (cursor == null) break;
        }
        assertEquals(7, seen.size());
        assertEquals(7, seen.stream().map(Number::longValue).distinct().count());
    }

    @Test
    void deletingTheGroupRemovesItsHistory() throws Exception {
        Session a = signIn("Asha");
        long g = groupOf(a);
        add(g, a, "Solo", 100, a, a);
        delete(url(g), a).andExpect(status().isNoContent());
        get(url(g) + "/history", a).andExpect(status().is4xxClientError());
    }
}
