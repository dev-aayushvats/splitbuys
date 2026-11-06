package com.splitbuys.backend.common.web;

import com.splitbuys.backend.common.error.InvalidRequestException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/**
 * Keyset position in a list ordered by (createdAt, id) descending: "everything older than this row". Unlike an offset it
 * stays correct when rows are added while someone is paging, and the database can seek straight to it through the index
 * instead of counting past all the skipped rows. Opaque to clients.
 */
public record PageCursor(Instant createdAt, long id) {
    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 100;

    public static int checkLimit(int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new InvalidRequestException("limit must be between 1 and " + MAX_LIMIT);
        }
        return limit;
    }

    public String encode() {
        return Base64.getUrlEncoder().withoutPadding().encodeToString((createdAt + "|" + id).getBytes(StandardCharsets.UTF_8));
    }

    /** @return null for a missing cursor (the first page); a malformed one is a client error, not a server error */
    public static PageCursor decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String[] parts = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split("\\|");
            return new PageCursor(Instant.parse(parts[0]), Long.parseLong(parts[1]));
        } catch (RuntimeException e) {
            throw new InvalidRequestException("Invalid cursor");
        }
    }
}
