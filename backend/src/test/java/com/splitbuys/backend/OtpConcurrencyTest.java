package com.splitbuys.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.splitbuys.backend.support.ApiTest;
import com.splitbuys.backend.support.UsesPostgres;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/**
 * Parallel guesses against one sign-in code. Runs on PostgreSQL because SQLite's single connection would serialise
 * the requests and hide the race this guards against.
 */
@UsesPostgres
class OtpConcurrencyTest extends ApiTest {
    private static final int MAX_ATTEMPTS = 5; // app.otp.max-attempts default
    private static final int PARALLEL_GUESSES = 40;

    @Test
    void parallelGuessesCannotExceedTheAttemptLimit() throws Exception {
        String email = uniqueEmail();
        post("/api/v1/auth/request-otp", null, "{\"email\":\"" + email + "\"}").andExpect(status().isOk());
        String real = mail.codes.get(email);
        String wrong = real.equals("000000") ? "000001" : "000000";
        String body = "{\"email\":\"" + email + "\",\"code\":\"" + wrong + "\"}";

        ExecutorService pool = Executors.newFixedThreadPool(PARALLEL_GUESSES);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < PARALLEL_GUESSES; i++) {
            Callable<Integer> guess = () -> {
                start.await();
                return post("/api/v1/auth/verify-otp", null, body).andReturn().getResponse().getStatus();
            };
            results.add(pool.submit(guess));
        }
        start.countDown();
        int rejected = 0;
        int locked = 0;
        for (Future<Integer> f : results) {
            int code = f.get();
            if (code == 400) {
                rejected++;
            } else if (code == 429) {
                locked++;
            }
        }
        pool.shutdown();

        assertEquals(MAX_ATTEMPTS, rejected, "exactly the allowed number of guesses may be evaluated");
        assertEquals(PARALLEL_GUESSES - MAX_ATTEMPTS, locked, "every other guess must be refused as rate limited");

        // the real code no longer works once the limit is spent
        post("/api/v1/auth/verify-otp", null, "{\"email\":\"" + email + "\",\"code\":\"" + real + "\"}")
                .andExpect(status().isTooManyRequests());
    }
}
