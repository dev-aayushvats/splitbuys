package com.splitbuys.backend;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.splitbuys.backend.support.ApiTest;
import org.junit.jupiter.api.Test;

class LedgerApiTest extends ApiTest {

    @Test
    void equalSplitRoundsByLargestRemainderAndSharesSumToTheTotal() throws Exception {
        Session a = signIn("Asha"), b = signIn("Bo"), c = signIn("Chen");
        long g = groupOf(a, b, c);
        var created = post("/api/v1/groups/" + g + "/expenses", a,
                equalExpense("Tea", 1000, a.userId(), a.userId(), b.userId(), c.userId())).andExpect(status().isCreated());
        // 1000 / 3 = 333.33…: one person carries the extra paisa
        long sum = ((java.util.List<Number>) json(created, "$.shares[*].amountPaise")).stream().mapToLong(Number::longValue).sum();
        org.junit.jupiter.api.Assertions.assertEquals(1000, sum);
    }

    @Test
    void expensesCanBeEditedAndDeleted() throws Exception {
        Session a = signIn("Asha"), b = signIn("Bo");
        long g = groupOf(a, b);
        long e = id(post("/api/v1/groups/" + g + "/expenses", a, equalExpense("Lunch", 600, a.userId(), a.userId(), b.userId()))
                .andExpect(status().isCreated()), "$.id");

        put("/api/v1/groups/" + g + "/expenses/" + e, b, equalExpense("Big lunch", 800, b.userId(), a.userId(), b.userId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.description").value("Big lunch"))
                .andExpect(jsonPath("$.payer.id").value(b.userId()));
        get("/api/v1/groups/" + g + "/expenses", a).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].shares.length()").value(2));

        delete("/api/v1/groups/" + g + "/expenses/" + e, a).andExpect(status().isNoContent());
        get("/api/v1/groups/" + g + "/expenses", a).andExpect(jsonPath("$.length()").value(0));
        delete("/api/v1/groups/" + g + "/expenses/" + e, a).andExpect(status().isNotFound());
    }

    @Test
    void anExpenseCannotBeReachedThroughAnotherGroup() throws Exception {
        Session a = signIn("Asha"), z = signIn("Zed");
        long mine = groupOf(a), theirs = groupOf(z);
        long e = id(post("/api/v1/groups/" + mine + "/expenses", a, equalExpense("Mine", 100, a.userId(), a.userId()))
                .andExpect(status().isCreated()), "$.id");
        delete("/api/v1/groups/" + theirs + "/expenses/" + e, z).andExpect(status().isNotFound());
        put("/api/v1/groups/" + theirs + "/expenses/" + e, z, equalExpense("Hijack", 100, z.userId(), z.userId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void splitRulesAreEnforced() throws Exception {
        Session a = signIn("Asha"), b = signIn("Bo"), outsider = signIn("Omar");
        long g = groupOf(a, b);
        String url = "/api/v1/groups/" + g + "/expenses";

        post(url, a, "{\"description\":\"x\",\"amountPaise\":1000,\"payerId\":" + a.userId()
                + ",\"splitType\":\"PERCENT\",\"splits\":[{\"userId\":" + a.userId() + ",\"value\":60},{\"userId\":" + b.userId() + ",\"value\":30}]}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("Percentages must add up to 100"));
        post(url, a, "{\"description\":\"x\",\"amountPaise\":1000,\"payerId\":" + a.userId()
                + ",\"splitType\":\"EXACT\",\"splits\":[{\"userId\":" + a.userId() + ",\"value\":400},{\"userId\":" + b.userId() + ",\"value\":500}]}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("Amounts must add up to the total"));
        post(url, a, "{\"description\":\"x\",\"amountPaise\":1000,\"payerId\":" + a.userId()
                + ",\"splitType\":\"SHARES\",\"splits\":[{\"userId\":" + a.userId() + ",\"value\":1.5}]}")
                .andExpect(status().isBadRequest());
        post(url, a, equalExpense("x", 1000, outsider.userId(), a.userId())).andExpect(status().isBadRequest());
        post(url, a, equalExpense("x", 1000, a.userId(), a.userId(), a.userId())).andExpect(status().isBadRequest());
        post(url, a, equalExpense("x", 0, a.userId(), a.userId())).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        post(url, a, "not json").andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("Malformed request body"));
    }

    @Test
    void balancesAndSettleUpReflectExpensesAndPayments() throws Exception {
        Session a = signIn("Asha"), b = signIn("Bo");
        long g = groupOf(a, b);
        post("/api/v1/groups/" + g + "/expenses", a, equalExpense("Hotel", 2000, a.userId(), a.userId(), b.userId()))
                .andExpect(status().isCreated());

        get("/api/v1/groups/" + g + "/balances", a).andExpect(status().isOk())
                .andExpect(jsonPath("$.balances[?(@.user.id==" + a.userId() + ")].netPaise").value(1000))
                .andExpect(jsonPath("$.balances[?(@.user.id==" + b.userId() + ")].netPaise").value(-1000))
                .andExpect(jsonPath("$.settleUp.length()").value(1))
                .andExpect(jsonPath("$.settleUp[0].from.id").value(b.userId()))
                .andExpect(jsonPath("$.settleUp[0].amountPaise").value(1000));

        long s = id(post("/api/v1/groups/" + g + "/settlements", b,
                "{\"fromUserId\":" + b.userId() + ",\"toUserId\":" + a.userId() + ",\"amountPaise\":400}")
                .andExpect(status().isCreated()), "$.id");
        get("/api/v1/groups/" + g + "/balances", a).andExpect(jsonPath("$.settleUp[0].amountPaise").value(600));

        delete("/api/v1/groups/" + g + "/settlements/" + s, a).andExpect(status().isNoContent());
        get("/api/v1/groups/" + g + "/balances", a).andExpect(jsonPath("$.settleUp[0].amountPaise").value(1000));
    }

    @Test
    void settlementsValidatePeople() throws Exception {
        Session a = signIn("Asha"), b = signIn("Bo"), outsider = signIn("Omar");
        long g = groupOf(a, b);
        String url = "/api/v1/groups/" + g + "/settlements";
        post(url, a, "{\"fromUserId\":" + a.userId() + ",\"toUserId\":" + a.userId() + ",\"amountPaise\":10}")
                .andExpect(status().isBadRequest());
        post(url, a, "{\"fromUserId\":" + a.userId() + ",\"toUserId\":" + outsider.userId() + ",\"amountPaise\":10}")
                .andExpect(status().isBadRequest());
        post(url, a, "{\"fromUserId\":" + a.userId() + ",\"toUserId\":" + b.userId() + ",\"amountPaise\":-5}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void expensesListNewestFirst() throws Exception {
        Session a = signIn("Asha");
        long g = groupOf(a);
        for (String d : new String[] {"first", "second", "third"}) {
            post("/api/v1/groups/" + g + "/expenses", a, equalExpense(d, 100, a.userId(), a.userId())).andExpect(status().isCreated());
        }
        get("/api/v1/groups/" + g + "/expenses", a).andExpect(jsonPath("$[0].description").value("third"))
                .andExpect(jsonPath("$[2].description").value("first"));
    }

    @Test
    void unknownRoutesAndBadIdsGetProblemResponsesNotStackTraces() throws Exception {
        Session a = signIn("Asha");
        get("/api/v1/groups/abc", a).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").exists());
        get("/api/v1/nope", a).andExpect(status().isNotFound());
    }
}
