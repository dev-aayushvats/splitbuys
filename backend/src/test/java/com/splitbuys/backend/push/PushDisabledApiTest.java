package com.splitbuys.backend.push;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.splitbuys.backend.support.ApiTest;
import org.junit.jupiter.api.Test;

/** Without VAPID keys the feature is off: the app is told so, and nothing can be subscribed. */
class PushDisabledApiTest extends ApiTest {
    @Test
    void reportsItselfOffAndRefusesSubscriptions() throws Exception {
        Session a = signIn("Asha");
        get("/api/v1/push/config", a).andExpect(status().isOk()).andExpect(jsonPath("$.enabled", is(false)))
                .andExpect(jsonPath("$.publicKey", is(nullValue())));
        post("/api/v1/push/subscriptions", a, "{\"endpoint\":\"https://fcm.googleapis.com/fcm/send/x\",\"keys\":{\"p256dh\":\"" + "A".repeat(87) + "\",\"auth\":\"" + "A".repeat(22) + "\"}}")
                .andExpect(status().isNotFound());
    }
}
