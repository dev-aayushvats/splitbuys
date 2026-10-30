package com.splitbuys.backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "APP_ENV_FILE=target/no-such-local.env", // tests never read the developer's real .env (it may point at a real database)
        "spring.datasource.url=jdbc:sqlite:target/it-ratelimit.db?foreign_keys=on",
        "app.jwt.secret=integration-test-secret-integration-test-secret",
        "app.rate-limit.auth-per-minute=3"})
@AutoConfigureMockMvc
class RateLimitApiTest {
    @Autowired MockMvc mvc;

    @Test
    void authEndpointsAreLimitedPerClientAndNonAuthEndpointsAreNot() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/v1/auth/verify-otp").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"a@b.co\",\"code\":\"1\"}")).andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/v1/auth/verify-otp").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.co\",\"code\":\"1\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
    }
}
