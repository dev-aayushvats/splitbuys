package com.splitbuys.backend.ledger;

import com.splitbuys.backend.common.error.InvalidRequestException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/**
 * Keyset position in the merged feed, ordered by (time, type, id) all descending. Two tables feed it, so the position has
 * to say which table the last row came from: rows with the same time are then ordered by type, and neither table can
 * repeat or skip a row at a page boundary. Opaque to clients.
 */
record ActivityCursor(Instant at, ActivityItem.Type type, long id) {

    String encode() {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((at + "|" + type.ordinal() + "|" + id).getBytes(StandardCharsets.UTF_8));
    }

    /** @return null for a missing cursor (the first page); a malformed one is a client error */
    static ActivityCursor decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String[] p = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split("\\|");
            return new ActivityCursor(Instant.parse(p[0]), ActivityItem.Type.values()[Integer.parseInt(p[1])], Long.parseLong(p[2]));
        } catch (RuntimeException e) {
            throw new InvalidRequestException("Invalid cursor");
        }
    }

    /**
     * The id bound to use with "time < at or (time = at and id < bound)" on the table for {@code table}. A table that sorts
     * after the cursor's own type still has all its rows at the same instant to give (bound: everything); one that sorts
     * before has none left (bound: nothing, ids start at 1); the cursor's own table continues after its id.
     */
    long idBoundFor(ActivityItem.Type table) {
        int c = table.compareTo(type);
        return c < 0 ? Long.MAX_VALUE : c == 0 ? id : 0;
    }
}
