package com.splitbuys.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.splitbuys.backend.support.ApiTest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class PaginationApiTest extends ApiTest {
    @Autowired JdbcTemplate jdbc;

    private String page(String url, Session as) throws Exception {
        return get(url, as).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private void addExpenses(Session a, long g, int count) throws Exception {
        for (int i = 1; i <= count; i++) {
            post("/api/v1/groups/" + g + "/expenses", a, equalExpense("e" + i, 100, a.userId(), a.userId())).andExpect(status().isCreated());
        }
    }

    /** Follows nextCursor to the end and returns every description in the order served. */
    private List<String> walk(String base, Session as, int limit) throws Exception {
        List<String> seen = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        do {
            String body = page(base + "?limit=" + limit + (cursor == null ? "" : "&cursor=" + cursor), as);
            List<String> items = JsonPath.read(body, "$.items[*].description");
            assertTrue(items.size() <= limit);
            seen.addAll(items);
            cursor = JsonPath.read(body, "$.nextCursor");
            assertTrue(++pages < 100, "paging never ended");
        } while (cursor != null);
        return seen;
    }

    @Test
    void pagesCoverEverythingOnceNewestFirst() throws Exception {
        Session a = signIn("Asha");
        long g = groupOf(a);
        addExpenses(a, g, 7);
        assertEquals(List.of("e7", "e6", "e5", "e4", "e3", "e2", "e1"), walk("/api/v1/groups/" + g + "/expenses", a, 3));
    }

    @Test
    void lastPageHasNoCursorAndExactMultiplesDoNotInventOne() throws Exception {
        Session a = signIn("Asha");
        long g = groupOf(a);
        addExpenses(a, g, 4);
        String body = page("/api/v1/groups/" + g + "/expenses?limit=4", a);
        assertNull(JsonPath.read(body, "$.nextCursor"));
        assertEquals(4, ((List<?>) JsonPath.read(body, "$.items")).size());
        assertNotNull(JsonPath.read(page("/api/v1/groups/" + g + "/expenses?limit=3", a), "$.nextCursor"));
    }

    @Test
    void rowsAddedWhilePagingNeitherRepeatNorSkipAnything() throws Exception {
        Session a = signIn("Asha");
        long g = groupOf(a);
        addExpenses(a, g, 5);
        String cursor = JsonPath.read(page("/api/v1/groups/" + g + "/expenses?limit=2", a), "$.nextCursor");
        post("/api/v1/groups/" + g + "/expenses", a, equalExpense("late", 100, a.userId(), a.userId())).andExpect(status().isCreated());
        String second = page("/api/v1/groups/" + g + "/expenses?limit=10&cursor=" + cursor, a);
        // an offset would now repeat e4; the cursor continues exactly after e4
        assertEquals(List.of("e3", "e2", "e1"), JsonPath.<List<String>>read(second, "$.items[*].description"));
    }

    @Test
    void rowsWithTheSameTimestampAreStillServedExactlyOnce() throws Exception {
        Session a = signIn("Asha");
        long g = groupOf(a);
        addExpenses(a, g, 5);
        // force an identical created_at on every row, copying a stored value so this works on any database
        jdbc.update("update expense set created_at = (select min(created_at) from (select created_at from expense where group_id = ?) t) "
                + "where group_id = ?", g, g);
        List<String> seen = walk("/api/v1/groups/" + g + "/expenses", a, 2);
        assertEquals(5, seen.size());
        assertEquals(5, new HashSet<>(seen).size(), "a tie on created_at must not repeat or drop rows: " + seen);
    }

    @Test
    void settlementsPageToo() throws Exception {
        Session a = signIn("Asha");
        Session b = signIn("Ben");
        long g = groupOf(a, b);
        for (int i = 1; i <= 5; i++) {
            post("/api/v1/groups/" + g + "/settlements", b,
                    "{\"fromUserId\":" + b.userId() + ",\"toUserId\":" + a.userId() + ",\"amountPaise\":" + (100 + i) + "}")
                    .andExpect(status().isCreated());
        }
        String p1 = get("/api/v1/groups/" + g + "/settlements?limit=2", b).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2)).andExpect(jsonPath("$.items[0].amountPaise").value(105))
                .andReturn().getResponse().getContentAsString();
        String cursor = JsonPath.read(p1, "$.nextCursor");
        String p2 = page("/api/v1/groups/" + g + "/settlements?limit=10&cursor=" + cursor, b);
        assertEquals(List.of(103, 102, 101), JsonPath.<List<Integer>>read(p2, "$.items[*].amountPaise"));
        assertNull(JsonPath.read(p2, "$.nextCursor"));
    }

    @Test
    void badLimitsAndCursorsAreClientErrors() throws Exception {
        Session a = signIn("Asha");
        long g = groupOf(a);
        String url = "/api/v1/groups/" + g + "/expenses";
        for (String bad : new String[] {"?limit=0", "?limit=-1", "?limit=101", "?limit=abc", "?cursor=garbage", "?cursor=bm90LWEtY3Vyc29y"}) {
            get(url + bad, a).andExpect(status().isBadRequest());
        }
        get(url + "?limit=100", a).andExpect(status().isOk());
    }

    @Test
    void aCursorOnlyEverReadsTheGroupInThePathAndOnlyForMembers() throws Exception {
        Session a = signIn("Asha");
        Session z = signIn("Zed");
        long mine = groupOf(a);
        long theirs = groupOf(z);
        addExpenses(a, mine, 3);
        String cursor = JsonPath.read(page("/api/v1/groups/" + mine + "/expenses?limit=1", a), "$.nextCursor");
        get("/api/v1/groups/" + mine + "/expenses?cursor=" + cursor, z).andExpect(status().isNotFound());
        assertEquals(0, ((List<?>) JsonPath.read(page("/api/v1/groups/" + theirs + "/expenses?cursor=" + cursor, z), "$.items")).size());
    }
}
