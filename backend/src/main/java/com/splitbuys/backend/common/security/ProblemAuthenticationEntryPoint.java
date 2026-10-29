package com.splitbuys.backend.common.security;

import com.splitbuys.backend.common.web.ProblemJson;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

/** 401/403 responses in the same problem format as every other error. */
class ProblemAuthenticationEntryPoint implements AuthenticationEntryPoint, AccessDeniedHandler {
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException e)
            throws IOException {
        boolean hadToken = request.getHeader(HttpHeaders.AUTHORIZATION) != null;
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        ProblemJson.write(response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                hadToken ? "Session expired, sign in again" : "Sign in required");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException e)
            throws IOException {
        ProblemJson.write(response, HttpStatus.FORBIDDEN, "FORBIDDEN", "You are not allowed to do that");
    }
}
