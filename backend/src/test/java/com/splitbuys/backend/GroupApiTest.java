package com.splitbuys.backend;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.splitbuys.backend.support.ApiTest;
import org.junit.jupiter.api.Test;

class GroupApiTest extends ApiTest {

    @Test
    void createListAndRename() throws Exception {
        Session a = signIn("Asha");
        long id = groupOf(a);
        get("/api/v1/groups", a).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id==" + id + ")].name").value("Trip"))
                .andExpect(jsonPath("$[?(@.id==" + id + ")].memberCount").value(1));
        put("/api/v1/groups/" + id, a, "{\"name\":\"  Goa  \"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Goa"));
    }

    @Test
    void blankAndOverlongNamesAreRejectedWithAValidationProblem() throws Exception {
        Session a = signIn("Asha");
        post("/api/v1/groups", a, "{\"name\":\" \"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        post("/api/v1/groups", a, "{\"name\":\"" + "x".repeat(81) + "\"}").andExpect(status().isBadRequest());
    }

    @Test
    void nonMembersCannotTellAGroupExists() throws Exception {
        Session a = signIn("Asha"), outsider = signIn("Omar");
        long id = groupOf(a);
        get("/api/v1/groups/" + id, outsider).andExpect(status().isNotFound());
        put("/api/v1/groups/" + id, outsider, "{\"name\":\"Mine\"}").andExpect(status().isNotFound());
        get("/api/v1/groups/" + id + "/expenses", outsider).andExpect(status().isNotFound());
        get("/api/v1/groups/999999", a).andExpect(status().isNotFound());
    }

    @Test
    void inviteLinkPreviewJoinAndReset() throws Exception {
        Session a = signIn("Asha"), b = signIn("Bo");
        var created = post("/api/v1/groups", a, "{\"name\":\"Flat\"}").andExpect(status().isCreated());
        String token = json(created, "$.inviteToken");
        long id = id(created, "$.id");

        get("/api/v1/invites/" + token, b).andExpect(status().isOk())
                .andExpect(jsonPath("$.groupName").value("Flat"))
                .andExpect(jsonPath("$.alreadyMember").value(false));
        post("/api/v1/invites/" + token + "/join", b, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.members.length()").value(2));
        post("/api/v1/invites/" + token + "/join", b, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.members.length()").value(2)); // joining twice is harmless

        post("/api/v1/groups/" + id + "/invite/regenerate", a, null).andExpect(status().isOk());
        get("/api/v1/invites/" + token, b).andExpect(status().isNotFound());
    }

    @Test
    void settledMemberCanLeaveButSomeoneWhoOwesCannot() throws Exception {
        Session a = signIn("Asha"), b = signIn("Bo");
        long id = groupOf(a, b);
        post("/api/v1/groups/" + id + "/expenses", a, equalExpense("Dinner", 1000, a.userId(), a.userId(), b.userId()))
                .andExpect(status().isCreated());

        delete("/api/v1/groups/" + id + "/members/me", b).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BALANCE_NOT_SETTLED"));
        delete("/api/v1/groups/" + id + "/members/" + b.userId(), a).andExpect(status().isConflict());

        post("/api/v1/groups/" + id + "/settlements", b,
                "{\"fromUserId\":" + b.userId() + ",\"toUserId\":" + a.userId() + ",\"amountPaise\":500}")
                .andExpect(status().isCreated());
        delete("/api/v1/groups/" + id + "/members/me", b).andExpect(status().isNoContent());
        get("/api/v1/groups/" + id, b).andExpect(status().isNotFound());
        get("/api/v1/groups/" + id, a).andExpect(jsonPath("$.members.length()").value(1));
    }

    @Test
    void deleteIsOnlyForTheSoleMemberAndErasesHistory() throws Exception {
        Session a = signIn("Asha"), b = signIn("Bo");
        long id = groupOf(a, b);
        delete("/api/v1/groups/" + id, a).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_HAS_MEMBERS"));
        delete("/api/v1/groups/" + id + "/members/" + b.userId(), a).andExpect(status().isNoContent());

        post("/api/v1/groups/" + id + "/expenses", a, equalExpense("Snack", 500, a.userId(), a.userId()))
                .andExpect(status().isCreated());
        delete("/api/v1/groups/" + id, a).andExpect(status().isNoContent());
        get("/api/v1/groups/" + id, a).andExpect(status().isNotFound());
    }

    @Test
    void lastMemberLeavingRemovesTheGroup() throws Exception {
        Session a = signIn("Asha");
        long id = groupOf(a);
        delete("/api/v1/groups/" + id + "/members/me", a).andExpect(status().isNoContent());
        get("/api/v1/groups", a).andExpect(jsonPath("$[?(@.id==" + id + ")]").isEmpty());
    }

    @Test
    void groupSummaryShowsMyBalance() throws Exception {
        Session a = signIn("Asha"), b = signIn("Bo");
        long id = groupOf(a, b);
        post("/api/v1/groups/" + id + "/expenses", a, equalExpense("Cab", 900, a.userId(), a.userId(), b.userId()))
                .andExpect(status().isCreated());
        get("/api/v1/groups", a).andExpect(jsonPath("$[?(@.id==" + id + ")].myBalancePaise").value(450));
        get("/api/v1/groups", b).andExpect(jsonPath("$[?(@.id==" + id + ")].myBalancePaise").value(-450));
    }

    @Test
    void renamingYourselfUpdatesWhatOthersSee() throws Exception {
        Session a = signIn("Asha"), b = signIn("Bo");
        long id = groupOf(a, b);
        put("/api/v1/me", b, "{\"name\":\"Bobby\"}").andExpect(status().isOk());
        get("/api/v1/groups/" + id, a).andExpect(jsonPath("$.members[?(@.id==" + b.userId() + ")].name").value("Bobby"));
    }
}
