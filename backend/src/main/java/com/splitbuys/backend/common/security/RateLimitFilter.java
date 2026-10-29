package com.splitbuys.backend.common.security;

import com.splitbuys.backend.common.web.ProblemJson;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Fixed-window, per-client-IP limit on the unauthenticated auth endpoints. Per-process and in-memory, which is right
 * for a single instance; put it in front (gateway / Redis) when scaling out.
 */
class RateLimitFilter extends OncePerRequestFilter {
    private static final long WINDOW_MILLIS = 60_000;
    private static final int MAX_TRACKED = 10_000;

    private record Window(long start, int count) {
    }

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final int perMinute;
    private final Clock clock;

    RateLimitFilter(int perMinute, Clock clock) {
        this.perMinute = perMinute;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/auth/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long now = clock.millis();
        if (windows.size() > MAX_TRACKED) {
            windows.values().removeIf(w -> now - w.start() >= WINDOW_MILLIS);
        }
        Window w = windows.merge(request.getRemoteAddr(), new Window(now, 1),
                (old, fresh) -> now - old.start() >= WINDOW_MILLIS ? fresh : new Window(old.start(), old.count() + 1));
        if (w.count() > perMinute) {
            response.setHeader("Retry-After", String.valueOf(Math.max(1, (WINDOW_MILLIS - (now - w.start())) / 1000)));
            ProblemJson.write(response, HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS",
                    "Too many requests. Please slow down.");
            return;
        }
        chain.doFilter(request, response);
    }
}
