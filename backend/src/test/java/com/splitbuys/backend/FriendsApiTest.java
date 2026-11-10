package com.splitbuys.backend;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.splitbuys.backend.support.ApiTest;
import org.junit.jupiter.api.Test;

class FriendsApiTest extends ApiTest {

    @Test
    void listsPeopleWhoShareAGroupWithWhatEachOwesTheOther() throws Exception {
        Session a = signIn("Asha"), b = signIn("Bo"), c = signIn("Chen"), outsider = signIn("Omar");
        long withBo = groupOf(a, b);
        groupOf(a, c);
        groupOf(outsider);
        post("/api/v1/groups/" + withBo + "/expenses", a, equalExpense("Hotel", 2000, a.userId(), a.userId(), b.userId()))
                .andExpect(status().isCreated());

        // Bo owes Asha 10.00; Chen shares a group but nothing is owed; Omar shares nothing and does not appear
        get("/api/v1/me/friends", a).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].user.id").value(b.userId()))
                .andExpect(jsonPath("$[0].balancePaise").value(1000))
                .andExpect(jsonPath("$[0].groups.length()").value(1))
                .andExpect(jsonPath("$[0].groups[0].id").value(withBo))
                .andExpect(jsonPath("$[0].groups[0].balancePaise").value(1000))
                .andExpect(jsonPath("$[1].user.id").value(c.userId()))
                .andExpect(jsonPath("$[1].balancePaise").value(0));

        // the same debt from Bo's side has the opposite sign
        get("/api/v1/me/friends", b).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].user.id").value(a.userId()))
                .andExpect(jsonPath("$[0].balancePaise").value(-1000));
    }

    @Test
    void balancesAddUpAcrossGroupsAndReactToPayments() throws Exception {
        Session a = signIn("Asha"), b = signIn("Bo");
        long g1 = groupOf(a, b), g2 = groupOf(a, b);
        post("/api/v1/groups/" + g1 + "/expenses", a, equalExpense("Hotel", 2000, a.userId(), a.userId(), b.userId()))
                .andExpect(status().isCreated());                                   // Bo owes 1000 here
        post("/api/v1/groups/" + g2 + "/expenses", b, equalExpense("Dinner", 600, b.userId(), a.userId(), b.userId()))
                .andExpect(status().isCreated());                                   // Asha owes 300 here

        get("/api/v1/me/friends", a).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].balancePaise").value(700))
                .andExpect(jsonPath("$[0].groups.length()").value(2))
                .andExpect(jsonPath("$[0].groups[0].id").value(g1))                 // biggest amount first
                .andExpect(jsonPath("$[0].groups[1].balancePaise").value(-300));

        post("/api/v1/groups/" + g1 + "/settlements", b,
                "{\"fromUserId\":" + b.userId() + ",\"toUserId\":" + a.userId() + ",\"amountPaise\":1000}")
                .andExpect(status().isCreated());
        get("/api/v1/me/friends", a).andExpect(jsonPath("$[0].balancePaise").value(-300));
    }

    @Test
    void aFriendIsOnlyShownThroughGroupsTheViewerIsIn() throws Exception {
        Session a = signIn("Asha"), b = signIn("Bo"), z = signIn("Zed");
        long shared = groupOf(a, b);
        long bosOther = groupOf(b, z);
        post("/api/v1/groups/" + bosOther + "/expenses", z, equalExpense("Secret", 900, z.userId(), z.userId(), b.userId()))
                .andExpect(status().isCreated());

        get("/api/v1/me/friends", a).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].user.id").value(b.userId()))
                .andExpect(jsonPath("$[0].groups.length()").value(1))
                .andExpect(jsonPath("$[0].groups[0].id").value(shared))
                .andExpect(jsonPath("$[0].balancePaise").value(0));
    }

    @Test
    void leavingTheLastSharedGroupEndsTheFriendship() throws Exception {
        Session a = signIn("Asha"), b = signIn("Bo");
        long g = groupOf(a, b);
        get("/api/v1/me/friends", a).andExpect(jsonPath("$.length()").value(1));
        delete("/api/v1/groups/" + g + "/members/me", b).andExpect(status().isNoContent());
        get("/api/v1/me/friends", a).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void aUserWithNoGroupsHasNoFriends() throws Exception {
        Session a = signIn("Asha");
        get("/api/v1/me/friends", a).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void requiresSignIn() throws Exception {
        get("/api/v1/me/friends", null).andExpect(status().isUnauthorized());
    }
}
