package com.splitbuys.backend.common.web;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;

/**
 * Writes an RFC 9457 problem document from places that sit outside Spring MVC (servlet filters, the security entry
 * point), so every error the API returns has the same shape.
 */
public final class ProblemJson {
    private ProblemJson() {
    }

    public static void write(HttpServletResponse response, HttpStatus status, String code, String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/problem+json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String requestId = MDC.get(RequestIdFilter.MDC_KEY);
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":" + quote(status.getReasonPhrase())
                + ",\"status\":" + status.value()
                + ",\"detail\":" + quote(detail)
                + ",\"code\":" + quote(code)
                + ",\"error\":" + quote(detail)
                + (requestId == null ? "" : ",\"requestId\":" + quote(requestId)) + "}");
    }

    private static String quote(String s) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> out.append(c < 0x20 ? String.format("\\u%04x", (int) c) : String.valueOf(c));
            }
        }
        return out.append('"').toString();
    }
}
