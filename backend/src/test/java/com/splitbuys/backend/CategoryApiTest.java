package com.splitbuys.backend;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.splitbuys.backend.support.ApiTest;
import org.junit.jupiter.api.Test;

class CategoryApiTest extends ApiTest {
    private String url(long g) { return "/api/v1/groups/" + g; }

    /** An equal-split expense JSON with a category added (null leaves the field out). */
    private static String withCategory(String json, String category) {
        return category == null ? json : json.replaceFirst("^\\{", "{\"category\":\"" + category + "\",");
    }

    private long add(long g, Session by, String what, long paise, String category, Session payer, Session... sharers) throws Exception {
        long[] ids = new long[sharers.length];
        for (int i = 0; i < sharers.length; i++) ids[i] = sharers[i].userId();
        return id(post(url(g) + "/expenses", by, withCategory(equalExpense(what, paise, payer.userId(), ids), category))
                .andExpect(status().isCreated()), "$.id");
    }

    @Test
    void anExpenseWithoutACategoryIsOtherAndAnEditThatOmitsItKeepsTheCurrentOne() throws Exception {
        Session a = signIn("Asha"), b = signIn("Bo");
        long g = groupOf(a, b);
        long plain = add(g, a, "Misc", 100, null, a, a, b);
        get(url(g) + "/expenses/" + plain, a).andExpect(jsonPath("$.category").value("OTHER"));

        long taxi = add(g, a, "Taxi", 500, "TRAVEL", a, a, b);
        get(url(g) + "/expenses/" + taxi, a).andExpect(jsonPath("$.category").value("TRAVEL"));
        put(url(g) + "/expenses/" + taxi, a, equalExpense("Taxi home", 600, a.userId(), a.userId(), b.userId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.category").value("TRAVEL"));
        put(url(g) + "/expenses/" + taxi, a, withCategory(equalExpense("Taxi home", 600, a.userId(), a.userId(), b.userId()), "FOOD"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.category").value("FOOD"));
    }

    @Test
    void anUnknownCategoryIsRejected() throws Exception {
        Session a = signIn("Asha");
        long g = groupOf(a);
        post(url(g) + "/expenses", a, withCategory(equalExpense("x", 100, a.userId(), a.userId()), "PARTIES"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void theSummaryAddsUpEachCategoryAndTheCallersShareOfIt() throws Exception {
        Session a = signIn("Asha"), b = signIn("Bo");
        long g = groupOf(a, b);
        add(g, a, "Dinner", 1000, "FOOD", a, a, b);       // 500 each
        add(g, b, "Lunch", 600, "FOOD", b, a, b);         // 300 each
        add(g, a, "Cab", 400, "TRAVEL", a, a);             // all on Asha
        add(g, a, "Stuff", 50, null, a, a, b);             // OTHER, 25 each

        get(url(g) + "/summary", a).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPaise").value(2050))
                .andExpect(jsonPath("$.mySharePaise").value(500 + 300 + 400 + 25))
                .andExpect(jsonPath("$.categories.length()").value(3))
                .andExpect(jsonPath("$.categories[0].category").value("FOOD"))
                .andExpect(jsonPath("$.categories[0].totalPaise").value(1600))
                .andExpect(jsonPath("$.categories[0].count").value(2))
                .andExpect(jsonPath("$.categories[0].mySharePaise").value(800))
                .andExpect(jsonPath("$.categories[1].category").value("TRAVEL"))
                .andExpect(jsonPath("$.categories[1].mySharePaise").value(400))
                .andExpect(jsonPath("$.categories[2].category").value("OTHER"));
        // the same numbers from Bo's side: his share differs, the totals do not
        get(url(g) + "/summary", b).andExpect(jsonPath("$.totalPaise").value(2050))
                .andExpect(jsonPath("$.mySharePaise").value(500 + 300 + 25));
    }

    @Test
    void anEmptyGroupHasAnEmptySummaryAndOutsidersCannotSeeIt() throws Exception {
        Session a = signIn("Asha"), outsider = signIn("Omar");
        long g = groupOf(a);
        get(url(g) + "/summary", a).andExpect(jsonPath("$.totalPaise").value(0)).andExpect(jsonPath("$.categories.length()").value(0));
        get(url(g) + "/summary", outsider).andExpect(status().isNotFound());
    }

    @Test
    void changingOnlyTheCategoryIsRecordedInTheHistoryAndCanBeUndone() throws Exception {
        Session a = signIn("Asha"), b = signIn("Bo");
        long g = groupOf(a, b);
        long e = add(g, a, "Dinner", 1000, "FOOD", a, a, b);
        put(url(g) + "/expenses/" + e, a, withCategory(equalExpense("Dinner", 1000, a.userId(), a.userId(), b.userId()), "ENTERTAINMENT"))
                .andExpect(status().isOk());
        get(url(g) + "/history", a).andExpect(jsonPath("$.items[0].action").value("EDITED"))
                .andExpect(jsonPath("$.items[0].before.category").value("FOOD"))
                .andExpect(jsonPath("$.items[0].after.category").value("ENTERTAINMENT"));
        long edit = id(get(url(g) + "/history", a), "$.items[0].id");
        post(url(g) + "/history/" + edit + "/undo", a, "").andExpect(status().isOk());
        get(url(g) + "/expenses/" + e, a).andExpect(jsonPath("$.category").value("FOOD"));
    }

    @Test
    void aRestoredExpenseKeepsItsCategory() throws Exception {
        Session a = signIn("Asha"), b = signIn("Bo");
        long g = groupOf(a, b);
        long e = add(g, a, "Hotel", 9000, "STAY", a, a, b);
        delete(url(g) + "/expenses/" + e, a).andExpect(status().isNoContent());
        long del = id(get(url(g) + "/history", a), "$.items[0].id");
        post(url(g) + "/history/" + del + "/undo", a, "").andExpect(status().isOk());
        get(url(g) + "/expenses", a).andExpect(jsonPath("$.items[0].category").value("STAY"));
    }
}
