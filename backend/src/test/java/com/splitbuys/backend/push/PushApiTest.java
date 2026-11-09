package com.splitbuys.backend.push;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.splitbuys.backend.support.ApiTest;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@TestPropertySource(properties = {
        "app.push.vapid-public-key=" + PushSettingsAndVapidTest.PUBLIC,
        "app.push.vapid-private-key=" + PushSettingsAndVapidTest.PRIVATE,
        "app.push.vapid-subject=mailto:test@splitbuys.dev"})
@Import(PushApiTest.CapturingGateway.class)
class PushApiTest extends ApiTest {

    record Sent(String endpoint, String message) {
    }

    /** Stands in for the browser vendors' push services: records what would have been sent, and can declare a device dead. */
    @TestConfiguration
    static class CapturingGateway {
        final List<Sent> sent = new CopyOnWriteArrayList<>();
        final Map<String, Boolean> dead = new ConcurrentHashMap<>();

        @Bean
        @Primary
        PushGateway capturing() {
            return (endpoint, p256dh, auth, payload) -> {
                sent.add(new Sent(endpoint, new String(payload, StandardCharsets.UTF_8)));
                return dead.containsKey(endpoint) ? PushGateway.Outcome.GONE : PushGateway.Outcome.SENT;
            };
        }
    }

    @Autowired CapturingGateway gateway;
    @Autowired PushSubscriptionRepository subscriptions;

    @BeforeEach
    void forgetEarlierTests() {
        gateway.sent.clear();
        gateway.dead.clear();
    }

    private static final SecureRandom RANDOM = new SecureRandom();

    private static String newEndpoint() {
        return "https://fcm.googleapis.com/fcm/send/" + UUID.randomUUID();
    }

    private static String subscriptionJson(String endpoint) throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
        gen.initialize(new ECGenParameterSpec("secp256r1"));
        byte[] point = WebPushCrypto.uncompressed((java.security.interfaces.ECPublicKey) gen.generateKeyPair().getPublic());
        byte[] auth = new byte[16];
        RANDOM.nextBytes(auth);
        Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
        return "{\"endpoint\":\"" + endpoint + "\",\"keys\":{\"p256dh\":\"" + b64.encodeToString(point) + "\",\"auth\":\"" + b64.encodeToString(auth) + "\"}}";
    }

    private void subscribe(Session as, String endpoint) throws Exception {
        post("/api/v1/push/subscriptions", as, subscriptionJson(endpoint)).andExpect(status().isNoContent());
    }

    /** Sending is asynchronous: wait (briefly) until the expected number of messages has gone out. */
    private List<Sent> awaitSent(int count) throws Exception {
        for (int i = 0; i < 100 && gateway.sent.size() < count; i++) {
            Thread.sleep(50);
        }
        Thread.sleep(100); // and a moment more, so an extra message that should NOT exist has time to show up
        assertEquals(count, gateway.sent.size(), () -> "messages sent: " + gateway.sent);
        return gateway.sent;
    }

    private void expense(Session as, long group, String description, long paise, String key, long payer, long... people) throws Exception {
        var req = MockMvcRequestBuilders.post("/api/v1/groups/" + group + "/expenses").header("Authorization", "Bearer " + as.token())
                .contentType(MediaType.APPLICATION_JSON).content(equalExpense(description, paise, payer, people));
        if (key != null) req.header("Idempotency-Key", key);
        mvc.perform(req).andExpect(status().isCreated());
    }

    // ---- the API ----

    @Test
    void theAppIsToldWhetherNotificationsAreAvailableAndWhichKeyToUse() throws Exception {
        Session a = signIn("Asha");
        get("/api/v1/push/config", a).andExpect(status().isOk()).andExpect(jsonPath("$.enabled", is(true)))
                .andExpect(jsonPath("$.publicKey", is(PushSettingsAndVapidTest.PUBLIC)));
        get("/api/v1/push/config", null).andExpect(status().isUnauthorized());
    }

    @Test
    void subscribingNeedsSignInAndAValidSubscription() throws Exception {
        Session a = signIn("Asha");
        post("/api/v1/push/subscriptions", null, subscriptionJson(newEndpoint())).andExpect(status().isUnauthorized());
        post("/api/v1/push/subscriptions", a, "{}").andExpect(status().isBadRequest());
        post("/api/v1/push/subscriptions", a, "{\"endpoint\":\"" + newEndpoint() + "\"}").andExpect(status().isBadRequest());
        post("/api/v1/push/subscriptions", a, "{\"endpoint\":\"" + newEndpoint() + "\",\"keys\":{\"p256dh\":\"abc\",\"auth\":\"def\"}}").andExpect(status().isBadRequest());
        assertEquals(0, subscriptions.findByUserIdIn(List.of(a.userId())).size());
    }

    @Test
    void endpointsThatAreNotRealPushServicesAreRefused() throws Exception {
        Session a = signIn("Asha");
        for (String bad : new String[] {"http://fcm.googleapis.com/x", "https://localhost/x", "https://169.254.169.254/latest",
                "https://fcm.googleapis.com.evil.com/x", "https://evil.example/x"}) {
            post("/api/v1/push/subscriptions", a, subscriptionJson(bad)).andExpect(status().isBadRequest());
        }
        assertEquals(0, subscriptions.findByUserIdIn(List.of(a.userId())).size());
    }

    @Test
    void subscribingTwiceKeepsOneRowAndADeviceMovesToWhoeverSignedInLast() throws Exception {
        Session ben = signIn("Ben");
        Session chi = signIn("Chi");
        String endpoint = newEndpoint();
        subscribe(ben, endpoint);
        subscribe(ben, endpoint);
        assertEquals(1, subscriptions.findByUserIdIn(List.of(ben.userId())).size());

        subscribe(chi, endpoint); // same phone, different person
        assertEquals(0, subscriptions.findByUserIdIn(List.of(ben.userId())).size());
        assertEquals(1, subscriptions.findByUserIdIn(List.of(chi.userId())).size());
    }

    @Test
    void aUserCannotRemoveSomeoneElsesDeviceButCanRemoveTheirOwnAndRepeatIt() throws Exception {
        Session ben = signIn("Ben");
        Session chi = signIn("Chi");
        String endpoint = newEndpoint();
        subscribe(ben, endpoint);

        delete("/api/v1/push/subscriptions", chi, "{\"endpoint\":\"" + endpoint + "\"}").andExpect(status().isNoContent());
        assertEquals(1, subscriptions.findByUserIdIn(List.of(ben.userId())).size());

        delete("/api/v1/push/subscriptions", ben, "{\"endpoint\":\"" + endpoint + "\"}").andExpect(status().isNoContent());
        delete("/api/v1/push/subscriptions", ben, "{\"endpoint\":\"" + endpoint + "\"}").andExpect(status().isNoContent());
        assertEquals(0, subscriptions.findByUserIdIn(List.of(ben.userId())).size());
    }

    @Test
    void aUserKeepsAtMostTenDevices() throws Exception {
        Session a = signIn("Asha");
        for (int i = 0; i < 13; i++) subscribe(a, newEndpoint());
        assertEquals(10, subscriptions.findByUserIdIn(List.of(a.userId())).size());
    }

    // ---- who gets told what ----

    @Test
    void anExpenseNotifiesTheOtherMembersButNotTheOneWhoAddedIt() throws Exception {
        Session asha = signIn("Asha");
        Session ben = signIn("Ben");
        Session chi = signIn("Chi");
        long g = groupOf(asha, ben, chi);
        String ashaPhone = newEndpoint(), benPhone = newEndpoint(), benLaptop = newEndpoint(), chiPhone = newEndpoint();
        subscribe(asha, ashaPhone);
        subscribe(ben, benPhone);
        subscribe(ben, benLaptop);
        subscribe(chi, chiPhone);

        expense(asha, g, "Dinner", 3000, null, asha.userId(), asha.userId(), ben.userId(), chi.userId());

        List<Sent> sent = awaitSent(3); // Ben's two devices and Chi's phone; not Asha
        assertTrue(sent.stream().noneMatch(s -> s.endpoint().equals(ashaPhone)));
        assertEquals(2, sent.stream().filter(s -> s.endpoint().equals(benPhone) || s.endpoint().equals(benLaptop)).count());
        Sent toChi = sent.stream().filter(s -> s.endpoint().equals(chiPhone)).findFirst().orElseThrow();
        assertTrue(toChi.message().contains("Asha added Dinner · ₹30.00 · you owe ₹10.00"), toChi.message());
        assertTrue(toChi.message().contains("\"title\":\"Trip\""), toChi.message());
        assertTrue(toChi.message().contains("\"url\":\"/groups/" + g + "\""), toChi.message());
    }

    @Test
    void aRepeatedRequestWithTheSameIdempotencyKeyDoesNotNotifyTwice() throws Exception {
        Session asha = signIn("Asha");
        Session ben = signIn("Ben");
        long g = groupOf(asha, ben);
        subscribe(ben, newEndpoint());

        expense(asha, g, "Cab", 1000, "same-key-0001", asha.userId(), asha.userId(), ben.userId());
        awaitSent(1);
        expense(asha, g, "Cab", 1000, "same-key-0001", asha.userId(), asha.userId(), ben.userId());
        awaitSent(1);
    }

    @Test
    void aRejectedExpenseNotifiesNobody() throws Exception {
        Session asha = signIn("Asha");
        Session ben = signIn("Ben");
        long g = groupOf(asha, ben);
        subscribe(ben, newEndpoint());

        // 10.00 split by exact amounts that add up to 9.00: refused by the ledger
        post("/api/v1/groups/" + g + "/expenses", asha, "{\"description\":\"Bad\",\"amountPaise\":1000,\"payerId\":" + asha.userId()
                + ",\"splitType\":\"EXACT\",\"splits\":[{\"userId\":" + asha.userId() + ",\"value\":400},{\"userId\":" + ben.userId() + ",\"value\":500}]}")
                .andExpect(status().isBadRequest());
        awaitSent(0);
    }

    @Test
    void aPaymentNotifiesTheReceiverAndNobodyElse() throws Exception {
        Session asha = signIn("Asha");
        Session ben = signIn("Ben");
        Session chi = signIn("Chi");
        long g = groupOf(asha, ben, chi);
        String ashaPhone = newEndpoint();
        subscribe(asha, ashaPhone);
        subscribe(ben, newEndpoint());
        subscribe(chi, newEndpoint());

        post("/api/v1/groups/" + g + "/settlements", ben, "{\"fromUserId\":" + ben.userId() + ",\"toUserId\":" + asha.userId() + ",\"amountPaise\":40000}")
                .andExpect(status().isCreated());

        List<Sent> sent = awaitSent(1);
        assertEquals(ashaPhone, sent.get(0).endpoint());
        assertTrue(sent.get(0).message().contains("Ben paid you ₹400.00"), sent.get(0).message());
    }

    @Test
    void aDeviceThePushServiceReportsGoneIsForgotten() throws Exception {
        Session asha = signIn("Asha");
        Session ben = signIn("Ben");
        long g = groupOf(asha, ben);
        String dead = newEndpoint();
        subscribe(ben, dead);
        gateway.dead.put(dead, true);

        expense(asha, g, "One", 1000, null, asha.userId(), asha.userId(), ben.userId());
        awaitSent(1);
        for (int i = 0; i < 40 && !subscriptions.findByUserIdIn(List.of(ben.userId())).isEmpty(); i++) Thread.sleep(50);
        assertTrue(subscriptions.findByUserIdIn(List.of(ben.userId())).isEmpty(), "the dead subscription should have been removed");

        expense(asha, g, "Two", 1000, null, asha.userId(), asha.userId(), ben.userId());
        awaitSent(1); // nothing further goes to it
    }

    @Test
    void deletingAnAccountRemovesItsDevices() throws Exception {
        Session a = signIn("Asha");
        subscribe(a, newEndpoint());
        subscribe(a, newEndpoint());
        assertEquals(2, subscriptions.findByUserIdIn(List.of(a.userId())).size());

        delete("/api/v1/me", a, "{\"email\":\"" + a.email() + "\"}").andExpect(status().isNoContent());

        assertEquals(0, subscriptions.findByUserIdIn(List.of(a.userId())).size());
    }

    private org.springframework.test.web.servlet.ResultActions delete(String url, Session as, String json) throws Exception {
        return send(org.springframework.http.HttpMethod.DELETE, url, as, json);
    }
}
