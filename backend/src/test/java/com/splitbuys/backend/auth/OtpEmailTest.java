package com.splitbuys.backend.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OtpEmailTest {
    private final OtpEmail email = new OtpEmail("482915", 10);

    @Test
    void theCodeLeadsTheSubjectSoANotificationShowsIt() {
        assertEquals("482915 is your SplitBuys code", email.subject());
    }

    @Test
    void plainTextCarriesTheCodeAndTheExpiry() {
        assertTrue(email.text().contains("482915"));
        assertTrue(email.text().contains("10 minutes"));
        assertTrue(email.text().contains("ignore"));
    }

    @Test
    void htmlShowsTheCodeLargeAndMentionsExpiry() {
        String html = email.html();
        assertTrue(html.contains(">482915</div>"), "the code in its own box");
        assertTrue(html.contains("expires in 10 minutes"));
        assertTrue(html.startsWith("<!doctype html>"));
        assertTrue(html.contains("prefers-color-scheme: dark"));
    }

    @Test
    void htmlLoadsNothingFromTheInternet() {
        String html = email.html().toLowerCase();
        assertFalse(html.contains("<img"));
        assertFalse(html.contains("http://") || html.contains("https://"), "no links or remote resources");
        assertFalse(html.contains("<script"));
    }

    @Test
    void aSingleMinuteIsSingular() {
        assertTrue(new OtpEmail("111111", 1).text().contains("1 minute."));
    }

    @Test
    void anythingInTheCodeIsEscapedEvenThoughCodesAreDigits() {
        assertFalse(new OtpEmail("<b>\"&", 5).html().contains("<b>\"&"));
        assertTrue(new OtpEmail("<b>", 5).html().contains("&lt;b&gt;"));
    }
}
