package com.splitbuys.backend;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.splitbuys.backend.support.ApiTest;
import org.junit.jupiter.api.Test;

class CommentApiTest extends ApiTest {
    private String url(long g) { return "/api/v1/groups/" + g; }

    private long expense(long g, Session by, String what, Session... people) throws Exception {
        long[] ids = new long[people.length];
        for (int i = 0; i < people.length; i++) ids[i] = people[i].userId();
        return id(post(url(g) + "/expenses", by, equalExpense(what, 1000, by.userId(), ids)).andExpect(status().isCreated()), "$.id");
    }

    private long comment(long g, long e, Session by, String text) throws Exception {
        return id(post(url(g) + "/expenses/" + e + "/comments", by, "{\"text\":\"" + text + "\"}")
                .andExpect(status().isCreated()), "$.id");
    }

    @Test
    void membersTalkOnAnExpenseOldestFirstAndTheTextIsTrimmed() throws Exception {
        Session a = signIn("Asha"), b = signIn("Bo");
        long g = groupOf(a, b);
        long e = expense(g, a, "Dinner", a, b);
        comment(g, e, b, "  why is this so high?  ");
        comment(g, e, a, "We had the tasting menu");
        get(url(g) + "/expenses/" + e + "/comments", a).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].text").value("why is this so high?"))
                .andExpect(jsonPath("$[0].author.name").value("Bo"))
                .andExpect(jsonPath("$[1].text").value("We had the tasting menu"));
    }

    @Test
    void emptyAndTooLongTextIsRefused() throws Exception {
        Session a = signIn("Asha");
        long g = groupOf(a);
        long e = expense(g, a, "Dinner", a);
        post(url(g) + "/expenses/" + e + "/comments", a, "{\"text\":\"   \"}").andExpect(status().isBadRequest());
        post(url(g) + "/expenses/" + e + "/comments", a, "{}").andExpect(status().isBadRequest());
        post(url(g) + "/expenses/" + e + "/comments", a, "{\"text\":\"" + "x".repeat(501) + "\"}").andExpect(status().isBadRequest());
        post(url(g) + "/expenses/" + e + "/comments", a, "{\"text\":\"" + "x".repeat(500) + "\"}").andExpect(status().isCreated());
    }

    @Test
    void onlyTheAuthorDeletesAndOutsidersSeeNothing() throws Exception {
        Session a = signIn("Asha"), b = signIn("Bo"), outsider = signIn("Omar");
        long g = groupOf(a, b);
        long e = expense(g, a, "Dinner", a, b);
        long c = comment(g, e, b, "mine");
        delete(url(g) + "/comments/" + c, a).andExpect(status().isBadRequest());
        get(url(g) + "/expenses/" + e + "/comments", outsider).andExpect(status().isNotFound());
        post(url(g) + "/expenses/" + e + "/comments", outsider, "{\"text\":\"hi\"}").andExpect(status().isNotFound());
        delete(url(g) + "/comments/" + c, outsider).andExpect(status().isNotFound());
        delete(url(g) + "/comments/" + c, b).andExpect(status().isNoContent());
        get(url(g) + "/expenses/" + e + "/comments", a).andExpect(jsonPath("$.length()").value(0));
        delete(url(g) + "/comments/" + c, b).andExpect(status().isNotFound());
    }

    @Test
    void anExpenseThatIsNotInThisGroupCannotBeCommentedOn() throws Exception {
        Session a = signIn("Asha");
        long g1 = groupOf(a), g2 = groupOf(a);
        long e = expense(g1, a, "Dinner", a);
        post(url(g2) + "/expenses/" + e + "/comments", a, "{\"text\":\"hi\"}").andExpect(status().isNotFound());
        get(url(g2) + "/expenses/9999/comments", a).andExpect(status().isNotFound());
    }

    @Test
    void commentsFollowAnExpenseThatIsDeletedAndThenRestored() throws Exception {
        Session a = signIn("Asha");
        long g = groupOf(a);
        long e = expense(g, a, "Dinner", a);
        comment(g, e, a, "keep me");
        delete(url(g) + "/expenses/" + e, a).andExpect(status().isNoContent());
        get(url(g) + "/expenses/" + e + "/comments", a).andExpect(status().isNotFound()); // not listable while the expense is gone

        long historyId = id(get(url(g) + "/history", a).andExpect(status().isOk()), "$.items[0].id");
        long restored = id(post(url(g) + "/history/" + historyId + "/undo", a, "").andExpect(status().isOk()), "$.expenseId");
        get(url(g) + "/expenses/" + restored + "/comments", a).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].text").value("keep me"));
    }

    @Test
    void anExpenseAcceptsAtMostTwoHundredComments() throws Exception {
        Session a = signIn("Asha"), b = signIn("Bo"), c = signIn("Cy");
        long g = groupOf(a, b, c);
        long e = expense(g, a, "Dinner", a, b, c);
        Session[] people = {a, b, c};
        for (int i = 0; i < 200; i++) comment(g, e, people[i % 3], "n" + i); // 67 each: under everyone's daily limit
        post(url(g) + "/expenses/" + e + "/comments", a, "{\"text\":\"one more\"}").andExpect(status().isConflict());
    }

    @Test
    void aPersonCanPostAtMostAHundredCommentsADay() throws Exception {
        Session a = signIn("Asha");
        long g = groupOf(a);
        long e = expense(g, a, "Dinner", a);
        for (int i = 0; i < 100; i++) comment(g, e, a, "n" + i);
        post(url(g) + "/expenses/" + e + "/comments", a, "{\"text\":\"too many\"}").andExpect(status().isTooManyRequests());
    }
}
