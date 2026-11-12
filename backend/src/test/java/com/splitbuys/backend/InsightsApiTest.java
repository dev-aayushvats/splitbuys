package com.splitbuys.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.splitbuys.backend.support.ApiTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class InsightsApiTest extends ApiTest {
    @Autowired JdbcTemplate jdbc;

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private long add(long g, Session by, String what, long paise, String category, Session payer, Session... sharers) throws Exception {
        long[] ids = new long[sharers.length];
        for (int i = 0; i < sharers.length; i++) ids[i] = sharers[i].userId();
        String json = equalExpense(what, paise, payer.userId(), ids);
        if (category != null) json = json.replaceFirst("^\\{", "{\"category\":\"" + category + "\",");
        return id(post("/api/v1/groups/" + g + "/expenses", by, json).andExpect(status().isCreated()), "$.id");
    }

    private static String month(int monthsAgo) {
        return YearMonth.now(IST).minusMonths(monthsAgo).toString();
    }

    @Test
    void sixMonthsEndingNowEvenWhenThereIsNothing() throws Exception {
        Session a = signIn("Asha");
        get("/api/v1/me/spending", a).andExpect(status().isOk())
                .andExpect(jsonPath("$.months.length()").value(6))
                .andExpect(jsonPath("$.months[5].month").value(month(0)))
                .andExpect(jsonPath("$.months[0].month").value(month(5)))
                .andExpect(jsonPath("$.months[5].totalPaise").value(0))
                .andExpect(jsonPath("$.months[5].categories.length()").value(0));
        get("/api/v1/me/spending", null).andExpect(status().isUnauthorized());
    }

    @Test
    void itAddsUpOnlyTheCallersOwnShareAcrossGroupsByCategory() throws Exception {
        Session a = signIn("Asha"), b = signIn("Bo"), c = signIn("Chi");
        long one = groupOf(a, b), two = groupOf(a, c);
        add(one, a, "Dinner", 1000, "FOOD", a, a, b);        // Asha's share 500
        add(two, c, "Lunch", 600, "FOOD", c, a, c);          // Asha's share 300
        add(two, a, "Cab", 400, "TRAVEL", a, a, c);          // Asha's share 200
        add(one, b, "Bo only", 900, "SHOPPING", b, b);       // not Asha's

        get("/api/v1/me/spending", a)
                .andExpect(jsonPath("$.months[5].totalPaise").value(1000))
                .andExpect(jsonPath("$.months[5].categories.length()").value(2))
                .andExpect(jsonPath("$.months[5].categories[0].category").value("FOOD"))
                .andExpect(jsonPath("$.months[5].categories[0].paise").value(800))
                .andExpect(jsonPath("$.months[5].categories[1].category").value("TRAVEL"))
                .andExpect(jsonPath("$.months[5].categories[1].paise").value(200));
        get("/api/v1/me/spending", b).andExpect(jsonPath("$.months[5].totalPaise").value(500 + 900));
    }

    @Test
    void anExpenseGoesIntoTheMonthItWasAddedInAndOneOutsideTheWindowIsIgnored() throws Exception {
        Session a = signIn("Asha"), b = signIn("Bo");
        long g = groupOf(a, b);
        long lastMonth = add(g, a, "Old", 1000, "FOOD", a, a, b);
        long ancient = add(g, a, "Ancient", 2000, "FOOD", a, a, b);
        add(g, a, "Now", 400, "FOOD", a, a, b);
        Instant mid = YearMonth.now(IST).minusMonths(1).atDay(15).atStartOfDay(IST).toInstant();
        Instant old = YearMonth.now(IST).minusMonths(8).atDay(15).atStartOfDay(IST).toInstant();
        jdbc.update("update expense set created_at = ? where id = ?", Timestamp.from(mid), lastMonth);
        jdbc.update("update expense set created_at = ? where id = ?", Timestamp.from(old), ancient);

        var r = get("/api/v1/me/spending", a).andExpect(status().isOk());
        r.andExpect(jsonPath("$.months[4].month").value(month(1))).andExpect(jsonPath("$.months[4].totalPaise").value(500))
                .andExpect(jsonPath("$.months[5].totalPaise").value(200));
        long all = 0;
        for (Number n : this.<java.util.List<Number>>json(r, "$.months[*].totalPaise")) all += n.longValue();
        assertEquals(700, all, "the expense from eight months ago must not appear");
    }

    @Test
    void leavingAGroupTakesItsSpendingOutOfTheView() throws Exception {
        Session a = signIn("Asha"), b = signIn("Bo");
        long g = groupOf(a, b);
        add(g, a, "Dinner", 1000, "FOOD", a, a, b);
        post("/api/v1/groups/" + g + "/settlements", b, "{\"fromUserId\":" + b.userId() + ",\"toUserId\":" + a.userId() + ",\"amountPaise\":500}")
                .andExpect(status().isCreated());
        delete("/api/v1/groups/" + g + "/members/me", b).andExpect(status().is2xxSuccessful());
        get("/api/v1/me/spending", b).andExpect(jsonPath("$.months[5].totalPaise").value(0));
        get("/api/v1/me/spending", a).andExpect(jsonPath("$.months[5].totalPaise").value(500));
    }

    @Test
    void aMonthInDetailSplitsPaidFromShareAndRanksGroupsAndPeople() throws Exception {
        Session a = signIn("Asha"), b = signIn("Bo"), c = signIn("Chi");
        long one = groupOf(a, b), two = groupOf(a, b, c);
        add(one, a, "Dinner", 1000, "FOOD", a, a, b);          // Asha paid 1000, her share 500, with Bo
        add(two, a, "Hotel", 3000, "STAY", a, a, b, c);        // paid 3000, share 1000, with Bo and Chi
        add(two, b, "Cab", 600, "TRAVEL", b, a, b);            // Bo paid, Asha's share 300, with Bo
        add(one, b, "Bo only", 900, "SHOPPING", b, b);         // not Asha's

        get("/api/v1/me/spending/" + month(0), a).andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value(month(0)))
                .andExpect(jsonPath("$.sharePaise").value(500 + 1000 + 300))
                .andExpect(jsonPath("$.paidPaise").value(1000 + 3000))
                .andExpect(jsonPath("$.expenseCount").value(3))
                .andExpect(jsonPath("$.groups.length()").value(2))
                .andExpect(jsonPath("$.groups[0].groupId").value((int) two))
                .andExpect(jsonPath("$.groups[0].paise").value(1300))
                .andExpect(jsonPath("$.groups[1].paise").value(500))
                .andExpect(jsonPath("$.people.length()").value(2))
                .andExpect(jsonPath("$.people[0].user.name").value("Bo"))
                .andExpect(jsonPath("$.people[0].paise").value(1800))
                .andExpect(jsonPath("$.people[1].user.name").value("Chi"))
                .andExpect(jsonPath("$.people[1].paise").value(1000));
        // the same expenses from Chi's side: only the hotel
        get("/api/v1/me/spending/" + month(0), c).andExpect(jsonPath("$.sharePaise").value(1000))
                .andExpect(jsonPath("$.paidPaise").value(0)).andExpect(jsonPath("$.groups.length()").value(1));
    }

    @Test
    void aMonthWithNothingHasZerosAndDaysCountedFollowsTheCalendar() throws Exception {
        Session a = signIn("Asha");
        get("/api/v1/me/spending/" + month(0), a).andExpect(jsonPath("$.sharePaise").value(0)).andExpect(jsonPath("$.groups.length()").value(0))
                .andExpect(jsonPath("$.people.length()").value(0))
                .andExpect(jsonPath("$.daysCounted").value(java.time.LocalDate.now(IST).getDayOfMonth()));
        get("/api/v1/me/spending/" + month(2), a)
                .andExpect(jsonPath("$.daysCounted").value(YearMonth.now(IST).minusMonths(2).lengthOfMonth()));
    }

    @Test
    void anExpenseIsFiledUnderItsOwnMonthInDetail() throws Exception {
        Session a = signIn("Asha"), b = signIn("Bo");
        long g = groupOf(a, b);
        long old = add(g, a, "Old", 1000, "FOOD", a, a, b);
        jdbc.update("update expense set created_at = ? where id = ?",
                Timestamp.from(YearMonth.now(IST).minusMonths(1).atDay(15).atStartOfDay(IST).toInstant()), old);
        get("/api/v1/me/spending/" + month(1), a).andExpect(jsonPath("$.sharePaise").value(500)).andExpect(jsonPath("$.expenseCount").value(1));
        get("/api/v1/me/spending/" + month(0), a).andExpect(jsonPath("$.sharePaise").value(0));
    }

    @Test
    void badMonthsAreRefused() throws Exception {
        Session a = signIn("Asha");
        get("/api/v1/me/spending/banana", a).andExpect(status().isBadRequest());
        get("/api/v1/me/spending/2026-13", a).andExpect(status().isBadRequest());
        get("/api/v1/me/spending/" + YearMonth.now(IST).plusMonths(1), a).andExpect(status().isBadRequest());
        get("/api/v1/me/spending/" + YearMonth.now(IST).minusMonths(25), a).andExpect(status().isBadRequest());
        get("/api/v1/me/spending/" + month(24), a).andExpect(status().isOk());
        get("/api/v1/me/spending/" + month(0), null).andExpect(status().isUnauthorized());
    }
}
