package com.splitbuys.backend.auth;

/**
 * The sign-in code email: a subject, a plain-text body, and a small HTML body. Pure (no framework), so it is easy to test and to
 * preview. The HTML is deliberately plain: one table, inline styles, no images or remote resources (many clients block them, and
 * a sign-in email should load nothing), with a dark-mode override for clients that support it. Plain text is sent alongside it, so
 * clients that show only text still get the code.
 */
final class OtpEmail {
    private final String code;
    private final long ttlMinutes;

    OtpEmail(String code, long ttlMinutes) {
        this.code = code;
        this.ttlMinutes = ttlMinutes;
    }

    /** The code leads the subject so it shows in a notification without opening the email. */
    String subject() {
        return code + " is your SplitBuys code";
    }

    String text() {
        return "Your SplitBuys sign-in code is " + code + ".\n\n"
                + "It expires in " + minutes() + ". If you didn't ask for it, you can ignore this email: nobody can sign in without it.";
    }

    String html() {
        String c = escape(code);
        return """
                <!doctype html>
                <html lang="en">
                <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <meta name="color-scheme" content="light dark">
                <meta name="supported-color-schemes" content="light dark">
                <title>%s</title>
                <style>
                  @media (prefers-color-scheme: dark) {
                    .page, body { background: #0f0f10 !important; }
                    .card { background: #1b1b1d !important; }
                    .ink { color: #f4f4f5 !important; }
                    .muted { color: #a1a1a6 !important; }
                    .code { background: #2a2118 !important; color: #ffb347 !important; }
                  }
                </style>
                </head>
                <body class="page" style="margin:0;padding:0;background:#f6f4ef;">
                <div style="display:none;max-height:0;overflow:hidden;opacity:0;">Use %s to sign in. It expires in %s.</div>
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" class="page" style="background:#f6f4ef;">
                  <tr><td align="center" style="padding:32px 16px;">
                    <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" class="card"
                           style="max-width:440px;background:#ffffff;border-radius:20px;">
                      <tr><td style="padding:32px 28px 8px 28px;font:700 20px -apple-system,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;color:#f08a00;">SplitBuys</td></tr>
                      <tr><td class="ink" style="padding:8px 28px 0 28px;font:600 22px/1.3 -apple-system,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;color:#1a1a1a;">Your sign-in code</td></tr>
                      <tr><td style="padding:20px 28px 8px 28px;">
                        <div class="code" style="background:#fff3df;color:#b85c00;border-radius:14px;padding:18px 12px;text-align:center;font:700 36px/1 ui-monospace,SFMono-Regular,Menlo,Consolas,monospace;letter-spacing:10px;">%s</div>
                      </td></tr>
                      <tr><td class="muted" style="padding:12px 28px 4px 28px;font:15px/1.5 -apple-system,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;color:#555555;">Enter it in the app to sign in. It expires in %s.</td></tr>
                      <tr><td class="muted" style="padding:4px 28px 32px 28px;font:13px/1.5 -apple-system,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;color:#8a8a8a;">Didn't ask for this? You can ignore this email. Nobody can sign in without the code.</td></tr>
                    </table>
                  </td></tr>
                </table>
                </body>
                </html>
                """.formatted(escape(subject()), c, minutes(), c, minutes());
    }

    private String minutes() {
        return ttlMinutes + (ttlMinutes == 1 ? " minute" : " minutes");
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
