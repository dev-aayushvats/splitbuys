package com.splitbuys.backend.auth;

import com.splitbuys.backend.web.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** Requires a valid Bearer token and exposes the user id as request attribute "userId". */
@Component
public class AuthInterceptor implements HandlerInterceptor {
    public static final String USER_ID = "userId";

    private final JwtService jwt;

    public AuthInterceptor(JwtService jwt) {
        this.jwt = jwt;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Sign in required");
        }
        long userId = jwt.verify(header.substring(7))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Session expired, sign in again"));
        request.setAttribute(USER_ID, userId);
        return true;
    }
}
