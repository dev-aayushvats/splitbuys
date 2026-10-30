package com.splitbuys.backend.common.security;

import com.splitbuys.backend.common.web.ProblemJson;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Guards {@code /api/v1/admin/**} with a shared secret in the {@code X-Admin-Token} header. Operations endpoints are
 * not for app users, so this is separate from sign-in. With no token configured the endpoints do not exist (404).
 */
class AdminTokenFilter extends OncePerRequestFilter {
    static final String HEADER = "X-Admin-Token";
    static final String PREFIX = "/api/v1/admin/";

    private final byte[] token;

    AdminTokenFilter(String token) {
        this.token = token.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (token.length == 0) {
            ProblemJson.write(response, HttpStatus.NOT_FOUND, "NOT_FOUND", "Not found");
            return;
        }
        String given = request.getHeader(HEADER);
        if (given == null || !MessageDigest.isEqual(given.getBytes(StandardCharsets.UTF_8), token)) {
            ProblemJson.write(response, HttpStatus.FORBIDDEN, "FORBIDDEN", "Admin access required");
            return;
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        SecurityContextHolder.setContext(context);
        chain.doFilter(request, response);
    }
}
