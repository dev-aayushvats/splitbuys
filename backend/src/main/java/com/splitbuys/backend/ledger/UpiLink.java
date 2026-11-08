package com.splitbuys.backend.ledger;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Builds a UPI payment link (the "intent" that opens GPay, PhonePe, Paytm, ... with the payee and amount filled in).
 * Format: upi://pay?pa=&lt;payee id&gt;&amp;pn=&lt;payee name&gt;&amp;am=&lt;amount in rupees&gt;&amp;cu=INR&amp;tn=&lt;note&gt;.
 * The link only opens a payment app; nothing here can tell whether the payment was actually made.
 *
 * <p>UPI apps are far less forgiving than browsers: an emoji, an accent, a stray "&amp;" or a double space in the name or
 * note can make an app reject the payment or show garbage. So the two free-text fields (pn, tn) are reduced to plain
 * letters, digits, spaces, dots, hyphens and underscores before encoding. The money fields (pa, am) are never altered:
 * if they are not safe, no link is made at all, rather than a link that pays the wrong person or amount.
 */
final class UpiLink {
    private UpiLink() {
    }

    private static final int MAX_NAME = 40;
    private static final int MAX_NOTE = 50; // UPI apps truncate or reject long notes
    private static final Pattern SAFE_ID = Pattern.compile("^[a-z0-9][a-z0-9._-]{1,63}@[a-z][a-z0-9]{1,31}$"); // same rule as UpiId
    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NOT_PLAIN = Pattern.compile("[^A-Za-z0-9._ -]+");
    private static final Pattern SPACES = Pattern.compile(" {2,}");

    /**
     * @return the link, or null when it cannot be made safely: an unusable UPI ID or a non-positive amount.
     *         The caller then shows the UPI ID without a Pay button.
     */
    static String build(String upiId, String payeeName, long amountPaise, String note) {
        if (upiId == null || !SAFE_ID.matcher(upiId).matches() || amountPaise <= 0) return null;
        String amount = BigDecimal.valueOf(amountPaise, 2).toPlainString(); // paise -> "123.45", never exponent notation
        String name = plain(payeeName, MAX_NAME, upiId); // an unnameable payee is shown by UPI ID, never left blank
        String text = plain(note, MAX_NOTE, "SplitBuys");
        return "upi://pay?pa=" + upiId + "&pn=" + encode(name) + "&am=" + amount + "&cu=INR&tn=" + encode(text);
    }

    /** Accents are folded (é to e); anything else outside the safe set (emoji, other scripts, symbols) becomes a space. */
    static String plain(String value, int max, String fallback) {
        if (value == null) return fallback;
        String folded = COMBINING_MARKS.matcher(Normalizer.normalize(value, Normalizer.Form.NFD)).replaceAll("");
        String cleaned = SPACES.matcher(NOT_PLAIN.matcher(folded).replaceAll(" ")).replaceAll(" ").strip();
        if (cleaned.length() > max) cleaned = cleaned.substring(0, max).strip(); // ASCII only by now, so no character is cut in half
        return cleaned.isEmpty() ? fallback : cleaned;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20"); // UPI apps expect %20, not "+"
    }
}
