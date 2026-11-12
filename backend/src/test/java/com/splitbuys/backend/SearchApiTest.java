package com.splitbuys.backend;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.splitbuys.backend.support.ApiTest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SearchApiTest extends ApiTest {
    private String url(long g) { return "/api/v1/groups/" + g; }

    private void add(long g, Session by, String what, String category, Session... sharers) throws Exception {
        long[] ids = new long[sharers.length];
        for (int i = 0; i < sharers.length; i++) ids[i] = sharers[i].userId();
        String json = equalExpense(what, 1000, by.userId(), ids);
        if (category != null) json = json.replaceFirst("^\\{", "{\"category\":\"" + category + "\",");
        post(url(g) + "/expenses", by, json).andExpect(status().isCreated());
    }

    /** Every description served for a query, following the cursor to the end. */
    private List<String> walk(String path, Session as, int limit) throws Exception {
        List<String> seen = new ArrayList<>();
        String cursor = null;
        for (int i = 0; i < 50; i++) {
            String sep = path.contains("?") ? "&" : "?";
            String body = get(path + sep + "limit=" + limit + (cursor == null ? "" : "&cursor=" + cursor), as)
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            List<Object> items = JsonPath.read(body, "$.items");
            for (int j = 0; j < items.size(); j++) {
                seen.add(path.startsWith("/api/v1/me") ? describe(body, j) : JsonPath.read(body, "$.items[" + j + "].description"));
            }
            cursor = JsonPath.read(body, "$.nextCursor");
            if (cursor == null) return seen;
        }
        throw new AssertionError("the cursor never ended");
    }

    private static String describe(String body, int j) {
        String type = JsonPath.read(body, "$.items[" + j + "].type");
        return type + ":" + (type.equals("EXPENSE") ? JsonPath.read(body, "$.items[" + j + "].expense.description") : "payment");
    }

    @Test
    void textMatchesAnyPartOfTheNameWithoutCaseAndPagesThroughMatchesOnly() throws Exception {
        Session a = signIn("Asha"), b = signIn("Bo");
        long g = groupOf(a, b);
        add(g, a, "Dinner at Fisherman", null, a, b);
        add(g, a, "Taxi", null, a, b);
        add(g, a, "dinner again", null, a, b);
        add(g, a, "Groceries", null, a, b);
        add(g, a, "DINNER 3", null, a, b);

        // limit 1 proves the filter is applied before the page is cut, not after
        org.junit.jupiter.api.Assertions.assertEquals(List.of("DINNER 3", "dinner again", "Dinner at Fisherman"), walk(url(g) + "/expenses?q=dinner", a, 1));
        org.junit.jupiter.api.Assertions.assertEquals(List.of("Dinner at Fisherman"), walk(url(g) + "/expenses?q= FISHER", a, 10));
        org.junit.jupiter.api.Assertions.assertEquals(5, walk(url(g) + "/expenses?q=", a, 10).size()); // blank means no filter
        org.junit.jupiter.api.Assertions.assertEquals(List.of(), walk(url(g) + "/expenses?q=zzz", a, 10));
    }

    @Test
    void percentAndUnderscoreInTheTextMeanThemselves() throws Exception {
        Session a = signIn("Asha");
        long g = groupOf(a);
        add(g, a, "100% juice", null, a);
        add(g, a, "snack_bar", null, a);
        add(g, a, "Cab", null, a);
        org.junit.jupiter.api.Assertions.assertEquals(List.of("100% juice"), walk(url(g) + "/expenses?q=%", a, 10));
        org.junit.jupiter.api.Assertions.assertEquals(List.of("snack_bar"), walk(url(g) + "/expenses?q=k_b", a, 10));
        org.junit.jupiter.api.Assertions.assertEquals(List.of(), walk(url(g) + "/expenses?q=s_ack", a, 10)); // `_` is not "any one character": "snack" is not "s_ack"
        org.junit.jupiter.api.Assertions.assertEquals(List.of(), walk(url(g) + "/expenses?q=ca_", a, 10)); // `_` is not "any one character": "Cab" does not match "ca_"
    }

    @Test
    void categoryFiltersAndCombinesWithText() throws Exception {
        Session a = signIn("Asha");
        long g = groupOf(a);
        add(g, a, "Dinner", "FOOD", a);
        add(g, a, "Dinner cruise", "ENTERTAINMENT", a);
        add(g, a, "Cab", "TRAVEL", a);
        add(g, a, "Misc", null, a);
        org.junit.jupiter.api.Assertions.assertEquals(List.of("Cab"), walk(url(g) + "/expenses?category=TRAVEL", a, 10));
        org.junit.jupiter.api.Assertions.assertEquals(List.of("Misc"), walk(url(g) + "/expenses?category=OTHER", a, 10));
        org.junit.jupiter.api.Assertions.assertEquals(List.of("Dinner cruise"), walk(url(g) + "/expenses?q=dinner&category=ENTERTAINMENT", a, 10));
        org.junit.jupiter.api.Assertions.assertEquals(List.of("Dinner cruise", "Dinner"), walk(url(g) + "/expenses?q=dinner", a, 10));
    }

    @Test
    void badInputAndOutsidersAreRefused() throws Exception {
        Session a = signIn("Asha"), outsider = signIn("Omar");
        long g = groupOf(a);
        get(url(g) + "/expenses?category=PARTIES", a).andExpect(status().isBadRequest());
        get(url(g) + "/expenses?q=" + "x".repeat(101), a).andExpect(status().isBadRequest());
        get(url(g) + "/expenses?q=" + "x".repeat(100), a).andExpect(status().isOk());
        get(url(g) + "/expenses?q=anything", outsider).andExpect(status().isNotFound());
        get("/api/v1/me/activity?category=PARTIES", a).andExpect(status().isBadRequest());
    }

    @Test
    void activitySearchSpansGroupsKeepsToWhatInvolvesTheUserAndSkipsPayments() throws Exception {
        Session a = signIn("Asha"), b = signIn("Bo"), c = signIn("Cy");
        long g1 = groupOf(a, b), g2 = groupOf(a, c);
        add(g1, a, "Pizza night", "FOOD", a, b);
        add(g2, c, "Pizza lunch", "FOOD", c);             // Asha is not in this expense
        add(g2, c, "Pizza again", "FOOD", a, c);
        add(g1, b, "Cab", "TRAVEL", a, b);
        post(url(g1) + "/settlements", a, "{\"fromUserId\":" + b.userId() + ",\"toUserId\":" + a.userId() + ",\"amountPaise\":100}")
                .andExpect(status().isCreated());

        org.junit.jupiter.api.Assertions.assertEquals(List.of("EXPENSE:Pizza again", "EXPENSE:Pizza night"), walk("/api/v1/me/activity?q=pizza", a, 1));
        org.junit.jupiter.api.Assertions.assertEquals(List.of("EXPENSE:Cab"), walk("/api/v1/me/activity?category=TRAVEL", a, 10));
        // without a filter the payment is there as before
        org.junit.jupiter.api.Assertions.assertTrue(walk("/api/v1/me/activity", a, 10).contains("PAYMENT:payment"));
        get("/api/v1/me/activity?q=pizza", a).andExpect(jsonPath("$.items[?(@.type=='PAYMENT')]").isEmpty());
    }
}
