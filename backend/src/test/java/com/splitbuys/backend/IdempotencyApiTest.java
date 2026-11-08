package com.splitbuys.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.splitbuys.backend.common.idempotency.IdempotencyService;
import com.splitbuys.backend.support.ApiTest;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

class IdempotencyApiTest extends ApiTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired IdempotencyService idempotency;

    private ResultActions postKeyed(String url, Session as, String key, String json) throws Exception {
        var req = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url).header("Authorization", "Bearer " + as.token()).contentType(MediaType.APPLICATION_JSON).content(json);
        if (key != null) {
            req.header("Idempotency-Key", key);
        }
        return mvc.perform(req);
    }

    private int expenseCount(long groupId) {
        return jdbc.queryForObject("select count(*) from expense where group_id = ?", Integer.class, groupId);
    }

    private static String newKey() {
        return UUID.randomUUID().toString();
    }

    @Test
    void repeatingAnExpenseWithTheSameKeyReturnsTheOriginalAndCreatesNothingNew() throws Exception {
        Session a = signIn("Asha");
        Session b = signIn("Ben");
        long g = groupOf(a, b);
        String url = "/api/v1/groups/" + g + "/expenses";
        String body = equalExpense("Dinner", 1000, a.userId(), a.userId(), b.userId());
        String key = newKey();

        long first = id(postKeyed(url, a, key, body).andExpect(status().isCreated()), "$.id");
        postKeyed(url, a, key, body).andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(first))
                .andExpect(jsonPath("$.description").value("Dinner"));

        assertEquals(1, expenseCount(g));
    }

    @Test
    void withoutAKeyEveryRequestStillCreatesAnExpense() throws Exception {
        Session a = signIn("Asha");
        long g = groupOf(a);
        String body = equalExpense("Tea", 500, a.userId(), a.userId());
        postKeyed("/api/v1/groups/" + g + "/expenses", a, null, body).andExpect(status().isCreated());
        postKeyed("/api/v1/groups/" + g + "/expenses", a, null, body).andExpect(status().isCreated());
        assertEquals(2, expenseCount(g));
    }

    @Test
    void reusingAKeyForADifferentRequestIsRejectedNotReplayed() throws Exception {
        Session a = signIn("Asha");
        long g = groupOf(a);
        String url = "/api/v1/groups/" + g + "/expenses";
        String key = newKey();
        postKeyed(url, a, key, equalExpense("Lunch", 1000, a.userId(), a.userId())).andExpect(status().isCreated());
        postKeyed(url, a, key, equalExpense("Lunch", 9999, a.userId(), a.userId())).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        assertEquals(1, expenseCount(g));
    }

    @Test
    void aKeyCannotBeReplayedAgainstAnotherGroup() throws Exception {
        Session a = signIn("Asha");
        long g1 = groupOf(a);
        long g2 = groupOf(a);
        String body = equalExpense("Cab", 700, a.userId(), a.userId());
        String key = newKey();
        postKeyed("/api/v1/groups/" + g1 + "/expenses", a, key, body).andExpect(status().isCreated());
        postKeyed("/api/v1/groups/" + g2 + "/expenses", a, key, body).andExpect(status().isConflict());
        assertEquals(0, expenseCount(g2));
    }

    @Test
    void keysAreScopedPerUser() throws Exception {
        Session a = signIn("Asha");
        Session b = signIn("Ben");
        long g = groupOf(a, b);
        String url = "/api/v1/groups/" + g + "/expenses";
        String key = newKey();
        postKeyed(url, a, key, equalExpense("A's", 100, a.userId(), a.userId())).andExpect(status().isCreated());
        postKeyed(url, b, key, equalExpense("B's", 100, b.userId(), b.userId())).andExpect(status().isCreated());
        assertEquals(2, expenseCount(g));
    }

    @Test
    void aFailedRequestLeavesNoRecordSoTheCorrectedRetryCanUseTheSameKey() throws Exception {
        Session a = signIn("Asha");
        Session outsider = signIn("Out");
        long g = groupOf(a);
        String url = "/api/v1/groups/" + g + "/expenses";
        String key = newKey();
        postKeyed(url, outsider, key, equalExpense("x", 100, outsider.userId(), outsider.userId())).andExpect(status().isNotFound());
        assertEquals(0, jdbc.queryForObject("select count(*) from idempotency_key where key_value = ?", Integer.class, key));
        postKeyed(url, a, key, equalExpense("x", 100, a.userId(), a.userId())).andExpect(status().isCreated());
    }

    @Test
    void malformedKeysAreRejected() throws Exception {
        Session a = signIn("Asha");
        long g = groupOf(a);
        String url = "/api/v1/groups/" + g + "/expenses";
        String body = equalExpense("x", 100, a.userId(), a.userId());
        postKeyed(url, a, "short", body).andExpect(status().isBadRequest());
        postKeyed(url, a, "has spaces in it!!", body).andExpect(status().isBadRequest());
        postKeyed(url, a, "x".repeat(65), body).andExpect(status().isBadRequest());
        assertEquals(0, expenseCount(g));
    }

    @Test
    void settlementsAndGroupCreationAreIdempotentToo() throws Exception {
        Session a = signIn("Asha");
        Session b = signIn("Ben");
        long g = groupOf(a, b);
        String pay = "{\"fromUserId\":" + b.userId() + ",\"toUserId\":" + a.userId() + ",\"amountPaise\":500}";
        String key = newKey();
        long first = id(postKeyed("/api/v1/groups/" + g + "/settlements", b, key, pay).andExpect(status().isCreated()), "$.id");
        postKeyed("/api/v1/groups/" + g + "/settlements", b, key, pay).andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(first));
        assertEquals(1, jdbc.queryForObject("select count(*) from settlement where group_id = ?", Integer.class, g));

        String gkey = newKey();
        String name = "Once-" + newKey(); // unique, because the test database outlives a single run
        String createGroup = "{\"name\":\"" + name + "\"}";
        long created = id(postKeyed("/api/v1/groups", a, gkey, createGroup).andExpect(status().isCreated()), "$.id");
        postKeyed("/api/v1/groups", a, gkey, createGroup).andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(created));
        assertEquals(1, jdbc.queryForObject("select count(*) from expense_group where name = ?", Integer.class, name));
    }

    /** The case a client retry really produces: the first request is still running when the repeat arrives. */
    @Test
    void identicalRequestsRacingEachOtherCreateExactlyOneExpense() throws Exception {
        Session a = signIn("Asha");
        long g = groupOf(a);
        String url = "/api/v1/groups/" + g + "/expenses";
        String body = equalExpense("Race", 1234, a.userId(), a.userId());
        String key = newKey();
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Callable<Integer> call = () -> {
                go.await();
                return postKeyed(url, a, key, body).andReturn().getResponse().getStatus();
            };
            results.add(pool.submit(call));
        }
        go.countDown();
        for (Future<Integer> f : results) {
            int s = f.get();
            assertEquals(true, s == 201 || s == 409, "unexpected status " + s);
        }
        pool.shutdown();
        assertEquals(1, expenseCount(g), "a duplicate expense was created");
    }

    @Test
    void oldRecordsArePurgedAndRecentOnesKept() throws Exception {
        Session a = signIn("Asha");
        long g = groupOf(a);
        String url = "/api/v1/groups/" + g + "/expenses";
        String oldKey = newKey();
        String freshKey = newKey();
        postKeyed(url, a, oldKey, equalExpense("o", 100, a.userId(), a.userId())).andExpect(status().isCreated());
        postKeyed(url, a, freshKey, equalExpense("f", 100, a.userId(), a.userId())).andExpect(status().isCreated());
        jdbc.update("update idempotency_key set created_at = ? where key_value = ?",
                java.sql.Timestamp.from(java.time.Instant.now().minus(java.time.Duration.ofHours(25))), oldKey);
        idempotency.purgeOld();
        assertEquals(0, jdbc.queryForObject("select count(*) from idempotency_key where key_value = ?", Integer.class, oldKey));
        assertEquals(1, jdbc.queryForObject("select count(*) from idempotency_key where key_value = ?", Integer.class, freshKey));
    }
}
