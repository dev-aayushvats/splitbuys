package com.splitbuys.backend.ledger;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Builds a UPI payment link (the "intent" that opens GPay, PhonePe, Paytm, ... with the payee and amount filled in).
 * Format: upi://pay?pa=&lt;payee id&gt;&amp;pn=&lt;payee name&gt;&amp;am=&lt;amount in rupees&gt;&amp;cu=INR&amp;tn=&lt;note&gt;.
 * The link only opens a payment app; nothing here can tell whether the payment was actually made.
 */
final class UpiLink {
    private UpiLink() {
    }

    /** @param upiId an already-validated UPI ID, so it contains only characters that are safe in a query string */
    static String build(String upiId, String payeeName, long amountPaise, String note) {
        String amount = BigDecimal.valueOf(amountPaise, 2).toPlainString(); // paise -> "123.45", never exponent notation
        return "upi://pay?pa=" + upiId + "&pn=" + encode(payeeName) + "&am=" + amount + "&cu=INR&tn=" + encode(note.length() > MAX_NOTE ? note.substring(0, MAX_NOTE) : note);
    }

    private static final int MAX_NOTE = 50; // UPI apps truncate or reject long notes

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20"); // UPI apps expect %20, not "+"
    }
}
