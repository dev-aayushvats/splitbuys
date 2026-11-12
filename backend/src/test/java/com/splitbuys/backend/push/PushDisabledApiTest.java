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

    @Test
    void aReminderIsAcceptedButNothingCanBeSentWhenNotificationsAreOff() throws Exception {
        Session asha = signIn("Asha"), ben = signIn("Ben");
        long g = groupOf(asha, ben);
        post("/api/v1/groups/" + g + "/expenses", asha, equalExpense("Dinner", 10000, asha.userId(), ben.userId())).andExpect(status().isCreated());
        post("/api/v1/groups/" + g + "/reminders", asha, "{\"toUserId\":" + ben.userId() + "}").andExpect(status().isOk())
                .andExpect(jsonPath("$.delivered", is(false))).andExpect(jsonPath("$.amountPaise", is(10000)));
    }
}
