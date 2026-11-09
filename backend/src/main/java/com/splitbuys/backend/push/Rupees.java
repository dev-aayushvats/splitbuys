package com.splitbuys.backend.push;

/** Rupees with Indian digit grouping, e.g. 12345678 paise -> "₹1,23,456.78" (mirrors the web app's formatPaise). */
final class Rupees {
    private Rupees() {
    }

    static String format(long paise) {
        long abs = Math.abs(paise);
        String whole = String.valueOf(abs / 100);
        String grouped = whole.length() <= 3 ? whole
                : group(whole.substring(0, whole.length() - 3)) + "," + whole.substring(whole.length() - 3);
        return (paise < 0 ? "-" : "") + "₹" + grouped + "." + String.format("%02d", abs % 100);
    }

    private static String group(String digits) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && (digits.length() - i) % 2 == 0) out.append(',');
            out.append(digits.charAt(i));
        }
        return out.toString();
    }
}
