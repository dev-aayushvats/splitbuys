package com.splitbuys.backend;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.splitbuys.backend.support.ApiTest;
import org.junit.jupiter.api.Test;

class UpiApiTest extends ApiTest {

    @Test
    void upiIdCanBeSetUpdatedAndRemovedAndIsNormalised() throws Exception {
        Session a = signIn("Asha");
        get("/api/v1/me", a).andExpect(jsonPath("$.upiId").value(nullValue()));

        put("/api/v1/me/upi", a, "{\"upiId\":\"  Asha.K@OkBank \"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.upiId").value("asha.k@okbank"));
        get("/api/v1/me", a).andExpect(jsonPath("$.upiId").value("asha.k@okbank"));

        put("/api/v1/me/upi", a, "{\"upiId\":\"9876543210@ybl\"}").andExpect(jsonPath("$.upiId").value("9876543210@ybl"));

        delete("/api/v1/me/upi", a).andExpect(status().isOk()).andExpect(jsonPath("$.upiId").value(nullValue()));
        get("/api/v1/me", a).andExpect(jsonPath("$.upiId").value(nullValue()));
    }

    @Test
    void malformedUpiIdsAreRejected() throws Exception {
        Session a = signIn("Asha");
        for (String bad : new String[] {"asha", "asha@", "@bank", "asha@ba nk", "asha@bank@x", "a@bank", "asha@1bank",
                "asha&am=1@bank", "asha?x=1@bank", "ash a@bank", "http://evil.example"}) {
            put("/api/v1/me/upi", a, "{\"upiId\":\"" + bad + "\"}").andExpect(status().isBadRequest());
        }
        put("/api/v1/me/upi", a, "{\"upiId\":\"\"}").andExpect(status().isBadRequest());
        put("/api/v1/me/upi", a, "{}").andExpect(status().isBadRequest());
        get("/api/v1/me", a).andExpect(jsonPath("$.upiId").value(nullValue()));
    }

    @Test
    void onlyTheOneWhoMustPayGetsTheUpiIdAndLink() throws Exception {
        Session asha = signIn("Asha K");
        Session ben = signIn("Ben");
        Session chi = signIn("Chi");
        long g = groupOf(asha, ben, chi);
        put("/api/v1/me/upi", asha, "{\"upiId\":\"asha.k@okbank\"}").andExpect(status().isOk());
        // Asha pays 30.00 for all three: Ben and Chi each owe her 10.00
        post("/api/v1/groups/" + g + "/expenses", asha,
                equalExpense("Dinner", 3000, asha.userId(), asha.userId(), ben.userId(), chi.userId())).andExpect(status().isCreated());

        // Ben sees the UPI details on his own payment to Asha, and a link carrying receiver, name, rupee amount and note
        get("/api/v1/groups/" + g + "/balances", ben)
                .andExpect(jsonPath("$.settleUp[?(@.from.id == " + ben.userId() + ")].upiId").value(hasItem("asha.k@okbank")))
                .andExpect(jsonPath("$.settleUp[?(@.from.id == " + ben.userId() + ")].upiLink")
                        .value(hasItem(startsWith("upi://pay?pa=asha.k@okbank&pn=Asha%20K&am=10.00&cu=INR&tn="))))
                // ...but not on Chi's payment, which is none of his business
                .andExpect(jsonPath("$.settleUp[?(@.from.id == " + chi.userId() + ")].upiId").value(hasItem(nullValue())))
                .andExpect(jsonPath("$.totalSpentPaise").value(3000));
        // the receiver herself gets neither
        get("/api/v1/groups/" + g + "/balances", asha).andExpect(jsonPath("$.settleUp[0].upiId").value(nullValue()))
                .andExpect(jsonPath("$.settleUp[0].upiLink").value(nullValue()));
    }

    @Test
    void noUpiIdMeansNoLinkAndOtherEndpointsNeverExposeIt() throws Exception {
        Session asha = signIn("Asha");
        Session ben = signIn("Ben");
        long g = groupOf(asha, ben);
        post("/api/v1/groups/" + g + "/expenses", asha, equalExpense("Cab", 1000, asha.userId(), asha.userId(), ben.userId()))
                .andExpect(status().isCreated());
        get("/api/v1/groups/" + g + "/balances", ben).andExpect(jsonPath("$.settleUp[0].upiLink").value(nullValue()));

        put("/api/v1/me/upi", asha, "{\"upiId\":\"asha@okbank\"}").andExpect(status().isOk());
        // her ID must not leak through group, member or expense payloads that others can read
        for (String url : new String[] {"/api/v1/groups/" + g, "/api/v1/groups/" + g + "/expenses", "/api/v1/groups"}) {
            org.junit.jupiter.api.Assertions.assertFalse(
                    get(url, ben).andReturn().getResponse().getContentAsString().contains("asha@okbank"), url + " leaked a UPI ID");
        }
    }

    @Test
    void upiEndpointsRequireSignIn() throws Exception {
        put("/api/v1/me/upi", null, "{\"upiId\":\"asha@okbank\"}").andExpect(status().isUnauthorized());
        delete("/api/v1/me/upi", null).andExpect(status().isUnauthorized());
    }
}
