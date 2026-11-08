package com.splitbuys.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.splitbuys.backend.support.ApiTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

class AccountDeletionApiTest extends ApiTest {
    @Autowired JdbcTemplate jdbc;

    private ResultActions deleteAccount(Session as, String typedEmail) throws Exception {
        return send(HttpMethod.DELETE, "/api/v1/me", as, "{\"email\":\"" + typedEmail + "\"}");
    }

    private Map<String, Object> userRow(long id) {
        return jdbc.queryForMap("select * from app_user where id = ?", id);
    }

    @Test
    void deletingAnAccountErasesWhoTheyWereAndEndsTheirSession() throws Exception {
        Session a = signIn("Asha");
        put("/api/v1/me/upi", a, "{\"upiId\":\"asha@okbank\"}").andExpect(status().isOk());
        get("/api/v1/me", a).andExpect(status().isOk()); // the server now trusts this token

        deleteAccount(a, a.email()).andExpect(status().isNoContent());

        Map<String, Object> row = userRow(a.userId());
        assertEquals("deleted-" + a.userId() + "@deleted.invalid", row.get("email"));
        assertEquals("Deleted user", row.get("name"));
        assertNull(row.get("upi_id"));
        assertNotNull(row.get("deleted_at"));
        // the token still verifies cryptographically; it must be refused because the account is gone, immediately
        get("/api/v1/me", a).andExpect(status().isUnauthorized());
        get("/api/v1/groups", a).andExpect(status().isUnauthorized());
        post("/api/v1/groups", a, "{\"name\":\"ghost\"}").andExpect(status().isUnauthorized());
    }

    @Test
    void theSameEmailCanSignUpAgainAsABrandNewAccount() throws Exception {
        Session a = signIn("Asha");
        deleteAccount(a, a.email()).andExpect(status().isNoContent());

        post("/api/v1/auth/request-otp", null, "{\"email\":\"" + a.email() + "\"}").andExpect(status().isOk());
        var verified = post("/api/v1/auth/verify-otp", null,
                "{\"email\":\"" + a.email() + "\",\"code\":\"" + mail.codes.get(a.email()) + "\"}").andExpect(status().isOk());
        long newId = id(verified, "$.user.id");
        assertNotEquals(a.userId(), newId);
        assertEquals(true, json(verified, "$.newUser"));
    }

    @Test
    void anUnsettledBalanceBlocksDeletionAndChangesNothing() throws Exception {
        Session a = signIn("Asha");
        Session b = signIn("Ben");
        long g = groupOf(a, b);
        post("/api/v1/groups/" + g + "/expenses", a, equalExpense("Dinner", 1000, a.userId(), a.userId(), b.userId()))
                .andExpect(status().isCreated());

        deleteAccount(b, b.email()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BALANCE_NOT_SETTLED"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Trip")));
        deleteAccount(a, a.email()).andExpect(status().isConflict());

        get("/api/v1/me", b).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Ben"));
        get("/api/v1/groups/" + g, b).andExpect(status().isOk());
        assertTrue(userRow(b.userId()).get("deleted_at") == null);
    }

    @Test
    void theEmailMustBeTypedExactly() throws Exception {
        Session a = signIn("Asha");
        deleteAccount(a, "someone-else@test.dev").andExpect(status().isBadRequest());
        send(HttpMethod.DELETE, "/api/v1/me", a, "{}").andExpect(status().isBadRequest());
        get("/api/v1/me", a).andExpect(status().isOk());
        // case and surrounding spaces are forgiven
        deleteAccount(a, "  " + a.email().toUpperCase() + " ").andExpect(status().isNoContent());
    }

    @Test
    void youCannotDeleteAnAccountWithoutSigningIn() throws Exception {
        send(HttpMethod.DELETE, "/api/v1/me", null, "{\"email\":\"x@test.dev\"}").andExpect(status().isUnauthorized());
    }

    @Test
    void aSettledMemberCanLeaveAndTheGroupsHistoryStaysConsistentForTheOthers() throws Exception {
        Session a = signIn("Asha");
        Session b = signIn("Ben");
        long g = groupOf(a, b);
        post("/api/v1/groups/" + g + "/expenses", b, equalExpense("Taxi", 1000, b.userId(), a.userId(), b.userId()))
                .andExpect(status().isCreated());
        // Asha owes Ben 500 and pays it
        post("/api/v1/groups/" + g + "/settlements", a,
                "{\"fromUserId\":" + a.userId() + ",\"toUserId\":" + b.userId() + ",\"amountPaise\":500}").andExpect(status().isCreated());

        deleteAccount(a, a.email()).andExpect(status().isNoContent());

        // Ben still sees the group, the expense and the payment, with Asha shown as a deleted user and no trace of her email
        String expenses = get("/api/v1/groups/" + g + "/expenses", b).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(expenses.contains("Taxi"));
        assertTrue(expenses.contains("Deleted user"));
        assertFalse(expenses.contains(a.email()), "the deleted person's email leaked into someone else's view");
        String balances = get("/api/v1/groups/" + g + "/balances", b).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(balances.contains(a.email()));
        get("/api/v1/groups/" + g, b).andExpect(status().isOk()).andExpect(jsonPath("$.members.length()").value(1));
        // Ben paid 1000 and Asha repaid her 500 share, so he is settled, and the group still reports the 1000 that was spent
        get("/api/v1/groups/" + g + "/balances", b).andExpect(status().isOk())
                .andExpect(jsonPath("$.balances[0].netPaise").value(0)).andExpect(jsonPath("$.totalSpentPaise").value(1000));
    }

    @Test
    void aGroupWhoseOnlyMemberDeletesTheirAccountIsErasedWithItsHistory() throws Exception {
        Session a = signIn("Asha");
        long g = groupOf(a);
        post("/api/v1/groups/" + g + "/expenses", a, equalExpense("Solo", 300, a.userId(), a.userId())).andExpect(status().isCreated());

        deleteAccount(a, a.email()).andExpect(status().isNoContent());

        assertEquals(0, jdbc.queryForObject("select count(*) from expense_group where id = ?", Integer.class, g));
        assertEquals(0, jdbc.queryForObject("select count(*) from expense where group_id = ?", Integer.class, g));
    }

    @Test
    void pendingSignInCodesAndRememberedRequestsAreDropped() throws Exception {
        Session a = signIn("Asha");
        long g = groupOf(a);
        var req = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/groups/" + g + "/expenses")
                .header("Authorization", "Bearer " + a.token()).header("Idempotency-Key", "delete-me-1234")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(equalExpense("x", 100, a.userId(), a.userId()));
        mvc.perform(req).andExpect(status().isCreated());
        post("/api/v1/auth/request-otp", null, "{\"email\":\"" + a.email() + "\"}").andExpect(status().isOk());
        assertEquals(1, jdbc.queryForObject("select count(*) from otp_code where email = ?", Integer.class, a.email()));
        assertEquals(1, jdbc.queryForObject("select count(*) from idempotency_key where user_id = ?", Integer.class, a.userId()));

        deleteAccount(a, a.email()).andExpect(status().isNoContent());

        assertEquals(0, jdbc.queryForObject("select count(*) from otp_code where email = ?", Integer.class, a.email()));
        assertEquals(0, jdbc.queryForObject("select count(*) from idempotency_key where user_id = ?", Integer.class, a.userId()));
    }

    @Test
    void otherPeoplesSessionsAreUnaffected() throws Exception {
        Session a = signIn("Asha");
        Session b = signIn("Ben");
        deleteAccount(a, a.email()).andExpect(status().isNoContent());
        get("/api/v1/me", b).andExpect(status().isOk());
    }

    @Test
    void aRefusedDeletionRollsBackCompletely() throws Exception {
        // Asha is settled in her first group but not in the second: the refusal must not leave her half-removed from the first
        Session a = signIn("Asha");
        Session b = signIn("Ben");
        long settled = groupOf(a, b);
        long owed = groupOf(a, b);
        post("/api/v1/groups/" + owed + "/expenses", a, equalExpense("Dinner", 1000, a.userId(), a.userId(), b.userId()))
                .andExpect(status().isCreated());

        deleteAccount(a, a.email()).andExpect(status().isConflict());

        get("/api/v1/groups/" + settled, a).andExpect(status().isOk());
        get("/api/v1/groups/" + owed, a).andExpect(status().isOk());
    }
}
