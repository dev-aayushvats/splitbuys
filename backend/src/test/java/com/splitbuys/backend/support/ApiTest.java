package com.splitbuys.backend.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.splitbuys.backend.auth.EmailSender;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Full-stack HTTP tests: real security chain, real database (migrated by Flyway), mail captured instead of sent. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:target/it.db?foreign_keys=on",
        "app.jwt.secret=integration-test-secret-integration-test-secret",
        "app.rate-limit.auth-per-minute=100000"})
@AutoConfigureMockMvc
@Import(ApiTest.CapturingMail.class)
public abstract class ApiTest {

    /** Stands in for SMTP: remembers the last code sent to each address and can be told to fail. */
    @TestConfiguration
    public static class CapturingMail {
        public final Map<String, String> codes = new ConcurrentHashMap<>();
        public final AtomicBoolean failNext = new AtomicBoolean();

        @Bean
        @Primary
        EmailSender capturingSender() {
            return (email, code) -> {
                if (failNext.getAndSet(false)) {
                    throw new com.splitbuys.backend.common.error.DependencyFailureException(
                            "EMAIL_DELIVERY_FAILED", "Could not send the email. Try again in a moment.", null);
                }
                codes.put(email, code);
            };
        }
    }

    public record Session(String email, long userId, String token) {
    }

    @Autowired protected MockMvc mvc;
    @Autowired protected CapturingMail mail;

    protected static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@test.dev";
    }

    protected ResultActions send(HttpMethod method, String url, Session as, String json) throws Exception {
        MockHttpServletRequestBuilder req = request(method, url);
        if (as != null) {
            req.header("Authorization", "Bearer " + as.token());
        }
        if (json != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mvc.perform(req);
    }

    protected ResultActions get(String url, Session as) throws Exception {
        return send(HttpMethod.GET, url, as, null);
    }

    protected ResultActions post(String url, Session as, String json) throws Exception {
        return send(HttpMethod.POST, url, as, json);
    }

    protected ResultActions put(String url, Session as, String json) throws Exception {
        return send(HttpMethod.PUT, url, as, json);
    }

    protected ResultActions delete(String url, Session as) throws Exception {
        return send(HttpMethod.DELETE, url, as, null);
    }

    /** Signs a brand-new user in through the real OTP flow and gives them a name. */
    protected Session signIn(String name) throws Exception {
        String email = uniqueEmail();
        post("/api/v1/auth/request-otp", null, "{\"email\":\"" + email + "\"}").andExpect(status().isOk());
        MvcResult verified = post("/api/v1/auth/verify-otp", null,
                "{\"email\":\"" + email + "\",\"code\":\"" + mail.codes.get(email) + "\"}")
                .andExpect(status().isOk()).andReturn();
        String body = verified.getResponse().getContentAsString();
        Session s = new Session(email, ((Number) JsonPath.read(body, "$.user.id")).longValue(), JsonPath.read(body, "$.token"));
        put("/api/v1/me", s, "{\"name\":\"" + name + "\"}").andExpect(status().isOk());
        return s;
    }

    protected static <T> T json(ResultActions result, String path) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), path);
    }

    protected static long id(ResultActions result, String path) throws Exception {
        Number n = json(result, path);
        return n.longValue();
    }

    /** A group created by {@code owner}, with {@code others} joined through the invite link. Returns the group id. */
    protected long groupOf(Session owner, Session... others) throws Exception {
        ResultActions created = post("/api/v1/groups", owner, "{\"name\":\"Trip\"}").andExpect(status().isCreated());
        long id = id(created, "$.id");
        String token = json(created, "$.inviteToken");
        for (Session other : others) {
            post("/api/v1/invites/" + token + "/join", other, null).andExpect(status().isOk());
        }
        return id;
    }

    protected static String equalExpense(String description, long paise, long payerId, long... participants) {
        StringBuilder splits = new StringBuilder();
        for (long p : participants) {
            splits.append(splits.length() == 0 ? "" : ",").append("{\"userId\":").append(p).append("}");
        }
        return "{\"description\":\"" + description + "\",\"amountPaise\":" + paise + ",\"payerId\":" + payerId
                + ",\"splitType\":\"EQUAL\",\"splits\":[" + splits + "]}";
    }
}
