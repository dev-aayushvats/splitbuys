package com.splitbuys.backend.user;

import com.splitbuys.backend.common.error.InvalidRequestException;
import java.util.Locale;
import java.util.regex.Pattern;

/** A UPI ID (virtual payment address), e.g. asha.k@okbank or 9876543210@ybl. */
final class UpiId {
    // handle@psp. Letters, digits, dot, hyphen and underscore in the handle; letters and digits in the PSP part.
    private static final Pattern FORMAT = Pattern.compile("^[a-z0-9][a-z0-9._-]{1,63}@[a-z][a-z0-9]{1,31}$");

    private UpiId() {
    }

    /** Trims and lower-cases (UPI IDs are case-insensitive) and rejects anything that is not shaped like one. */
    static String normalize(String raw) {
        String id = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (!FORMAT.matcher(id).matches()) {
            throw new InvalidRequestException("Enter a valid UPI ID, like name@bank");
        }
        return id;
    }
}
