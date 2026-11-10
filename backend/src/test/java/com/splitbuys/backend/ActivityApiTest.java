package com.splitbuys.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.splitbuys.backend.support.ApiTest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class ActivityApiTest extends ApiTest {
    @Autowired JdbcTemplate jdbc;

    private void expense(long group, Session by, String what, long paise, Session payer, Session... sharers) throws Exception {
        long[] ids = new long[sharers.length];
        for (int i = 0; i < sharers.length; i++) {
            ids[i] = sharers[i].userId();
        }
        post("/api/v1/groups/" + group + "/expenses", by, equalExpense(what, paise, payer.userId(), ids)).andExpect(status().isCreated());
    }

    private void payment(long group, Session by, Session from, Session to, long paise) throws Exception {
        post("/api/v1/groups/" + group + "/settlements", by,
                "{\"fromUserId\":" + from.userId() + ",\"toUserId\":" + to.userId() + ",\"amountPaise\":" + paise + "}")
                .andExpect(status().isCreated());
    }

    /** Follows nextCursor to the end; returns one label per item ("E:<description>" or "P:<amount>") in the order served. */
    private List<String> walk(Session as, int limit) throws Exception {
        List<String> seen = new ArrayList<>();
        String cursor = null;
        for (int pages = 0; pages < 100; pages++) {
            String body = get("/api/v1/me/activity?limit=" + limit + (cursor == null ? "" : "&cursor=" + cursor), as)
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            List<String> types = JsonPath.read(body, "$.items[*].type");
            for (int i = 0; i < types.size(); i++) {
                seen.add(types.get(i).equals("EXPENSE")
                        ? "E:" + JsonPath.read(body, "$.items[" + i + "].expense.description")
                        : "P:" + JsonPath.read(body, "$.items[" + i + "].payment.amountPaise"));
            }
            cursor = JsonPath.read(body, "$.nextCursor");
            if (cursor == null) {
                return seen;
            }
        }
        throw new AssertionError("paging did not end: " + seen);
    }

    @Test
    void showsWhatInvolvesMeAcrossGroupsNewestFirst() throws Exception {
        Session a = signIn("Asha"), b = signIn("Bo"), c = signIn("Chen"), z = signIn("Zed");
        long g1 = groupOf(a, b, c), g2 = groupOf(a, b), other = groupOf(z);
        expense(g1, a, "Hotel", 3000, a, a, b, c);        // I paid: owed 2000
        expense(g1, b, "Snacks", 600, b, b, c);           // in my group, but I am neither payer nor sharer
        expense(g2, b, "Dinner", 800, b, a, b);           // I owe 400
        payment(g1, b, b, a, 500);                        // paid to me
        payment(g1, b, b, c, 100);                        // between others
        expense(other, z, "Secret", 999, z, z);

        get("/api/v1/me/activity", a).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.nextCursor").doesNotExist())
                .andExpect(jsonPath("$.items[0].type").value("PAYMENT"))
                .andExpect(jsonPath("$.items[0].payment.amountPaise").value(500))
                .andExpect(jsonPath("$.items[0].groupId").value(g1))
                .andExpect(jsonPath("$.items[0].myNetPaise").doesNotExist())
                .andExpect(jsonPath("$.items[1].type").value("EXPENSE"))
                .andExpect(jsonPath("$.items[1].expense.description").value("Dinner"))
                .andExpect(jsonPath("$.items[1].groupName").value("Trip"))
                .andExpect(jsonPath("$.items[1].myNetPaise").value(-400))
                .andExpect(jsonPath("$.items[2].expense.description").value("Hotel"))
                .andExpect(jsonPath("$.items[2].myNetPaise").value(2000));

        // Chen sees the group-1 items that involve Chen, never the other group's
        get("/api/v1/me/activity", c).andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].payment.amountPaise").value(100))
                .andExpect(jsonPath("$.items[1].expense.description").value("Snacks"))
                .andExpect(jsonPath("$.items[1].myNetPaise").value(-300));
        get("/api/v1/me/activity", z).andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void pagesMergeBothKindsWithoutRepeatsOrGaps() throws Exception {
        Session a = signIn("Asha"), b = signIn("Bo");
        long g1 = groupOf(a, b), g2 = groupOf(a, b);
        List<String> expected = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            expense(i % 2 == 0 ? g1 : g2, a, "e" + i, 100, a, a, b);
            expected.add(0, "E:e" + i);
            payment(i % 2 == 0 ? g2 : g1, b, b, a, 10 + i);
            expected.add(0, "P:" + (10 + i));
        }
        for (int limit : new int[] {1, 2, 3, 5, 100}) {
            assertEquals(expected, walk(a, limit), "limit " + limit);
        }
    }

    @Test
    void rowsWithTheSameTimestampAreNeitherRepeatedNorDropped() throws Exception {
        Session a = signIn("Asha"), b = signIn("Bo");
        long g = groupOf(a, b);
        for (int i = 1; i <= 4; i++) {
            expense(g, a, "e" + i, 100, a, a, b);
            payment(g, b, b, a, 10 + i);
        }
        // one identical created_at on every expense and payment of the group, copied from a stored value so it works on any database
        jdbc.update("update settlement set created_at = (select min(created_at) from (select created_at from settlement where group_id = ?) t) where group_id = ?", g, g);
        jdbc.update("update expense set created_at = (select min(created_at) from (select created_at from settlement where group_id = ?) t) where group_id = ?", g, g);

        for (int limit : new int[] {1, 2, 3, 7}) {
            List<String> seen = walk(a, limit);
            assertEquals(8, seen.size(), "limit " + limit + ": " + seen);
            assertEquals(8, new HashSet<>(seen).size(), "limit " + limit + " repeated a row: " + seen);
        }
        // and the order is stable: payments before expenses at the same instant, newest id first within each
        assertEquals(List.of("P:14", "P:13", "P:12", "P:11", "E:e4", "E:e3", "E:e2", "E:e1"), walk(a, 3));
    }

    @Test
    void leavingAGroupTakesItsHistoryOutOfTheFeed() throws Exception {
        Session a = signIn("Asha"), b = signIn("Bo");
        long g1 = groupOf(a, b), g2 = groupOf(a, b);
        expense(g1, a, "Gone", 100, a, a, b);           // Bo owes 50 here
        expense(g2, a, "Stays", 100, a, a, b);
        payment(g1, b, b, a, 50);                       // settled, so Bo may leave g1
        get("/api/v1/me/activity", b).andExpect(jsonPath("$.items.length()").value(3));

        delete("/api/v1/groups/" + g1 + "/members/me", b).andExpect(status().isNoContent());

        get("/api/v1/me/activity", b).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].expense.description").value("Stays"));
    }

    @Test
    void emptyForAUserWithNoGroups() throws Exception {
        Session a = signIn("Asha");
        get("/api/v1/me/activity", a).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
        assertNull(JsonPath.read(get("/api/v1/me/activity", a).andReturn().getResponse().getContentAsString(), "$.nextCursor"));
    }

    @Test
    void rejectsBadInputAndNeedsSignIn() throws Exception {
        Session a = signIn("Asha");
        get("/api/v1/me/activity?limit=0", a).andExpect(status().isBadRequest());
        get("/api/v1/me/activity?limit=101", a).andExpect(status().isBadRequest());
        get("/api/v1/me/activity?cursor=not-a-cursor", a).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Invalid cursor"));
        get("/api/v1/me/activity", null).andExpect(status().isUnauthorized());
    }
}
