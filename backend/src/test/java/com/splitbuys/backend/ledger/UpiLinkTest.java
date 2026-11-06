package com.splitbuys.backend.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UpiLinkTest {
    @Test
    void amountIsInRupeesWithTwoDecimals() {
        assertTrue(UpiLink.build("a@b", "A", 1, "n").contains("&am=0.01&"));
        assertTrue(UpiLink.build("a@b", "A", 1000, "n").contains("&am=10.00&"));
        assertTrue(UpiLink.build("a@b", "A", 123456, "n").contains("&am=1234.56&"));
        assertTrue(UpiLink.build("a@b", "A", 100_000_000_00L, "n").contains("&am=100000000.00&"));
    }

    @Test
    void namesAndNotesAreEncodedWithPercentTwentyForSpaces() {
        assertEquals("upi://pay?pa=asha@okbank&pn=Asha%20K%20%26%20Co&am=5.50&cu=INR&tn=Trip%20to%20Goa",
                UpiLink.build("asha@okbank", "Asha K & Co", 550, "Trip to Goa"));
    }

    @Test
    void injectionAttemptsInNameOrNoteCannotAddParameters() {
        String link = UpiLink.build("a@b", "x&am=1", 100, "y&pa=evil@bank");
        assertEquals(1, link.split("&am=", -1).length - 1);
        assertEquals(1, link.split("pa=", -1).length - 1);
    }

    @Test
    void longNotesAreTruncated() {
        String link = UpiLink.build("a@b", "A", 100, "x".repeat(200));
        assertTrue(link.endsWith("tn=" + "x".repeat(50)));
    }
}
