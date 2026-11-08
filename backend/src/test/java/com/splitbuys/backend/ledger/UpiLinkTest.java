package com.splitbuys.backend.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class UpiLinkTest {
    private static final String ID = "asha@okbank";

    /** The query parameters of a link, as the receiving app would read them (percent-decoding aside). */
    private static Map<String, String> params(String link) {
        return Stream.of(link.substring(link.indexOf('?') + 1).split("&"))
                .collect(Collectors.toMap(p -> p.split("=", 2)[0], p -> p.split("=", 2)[1]));
    }

    @Test
    void amountIsInRupeesWithTwoDecimals() {
        assertTrue(UpiLink.build(ID, "A", 1, "n").contains("&am=0.01&"));
        assertTrue(UpiLink.build(ID, "A", 5, "n").contains("&am=0.05&"));
        assertTrue(UpiLink.build(ID, "A", 1000, "n").contains("&am=10.00&"));
        assertTrue(UpiLink.build(ID, "A", 123456, "n").contains("&am=1234.56&"));
        assertTrue(UpiLink.build(ID, "A", UpiLink.MAX_PAISE, "n").contains("&am=100000.00&"));
    }

    @Test
    void aWellFormedLinkHasEveryRequiredField() {
        assertEquals("upi://pay?pa=asha@okbank&pn=Asha%20K&am=5.50&cu=INR&tn=Trip%20to%20Goa",
                UpiLink.build(ID, "Asha K", 550, "Trip to Goa"));
    }

    @Test
    void noLinkWhenThePayeeIdOrTheAmountIsNotUsable() {
        assertNull(UpiLink.build(null, "A", 100, "n"));
        assertNull(UpiLink.build("", "A", 100, "n"));
        assertNull(UpiLink.build("no-at-sign", "A", 100, "n"));
        assertNull(UpiLink.build("a b@okbank", "A", 100, "n"));
        assertNull(UpiLink.build("a@b&am=1", "A", 100, "n"));
        assertNull(UpiLink.build("evil@bank\n", "A", 100, "n"));
        assertNull(UpiLink.build(ID, "A", 0, "n"));
        assertNull(UpiLink.build(ID, "A", -50, "n"));
    }

    @Test
    void noLinkAboveTheOneLakhUpiLimitButExactlyOneLakhIsFine() {
        assertTrue(UpiLink.build(ID, "A", 10_000_000, "n").contains("&am=100000.00&"));
        assertNull(UpiLink.build(ID, "A", 10_000_001, "n"));
        assertNull(UpiLink.build(ID, "A", 50_000_000, "n"));
    }

    @Test
    void phoneNumberAndPunctuatedIdsAreKeptExactly() {
        assertTrue(UpiLink.build("9876543210@ybl", "A", 100, "n").startsWith("upi://pay?pa=9876543210@ybl&"));
        assertTrue(UpiLink.build("a.b_c-d@okhdfcbank", "A", 100, "n").startsWith("upi://pay?pa=a.b_c-d@okhdfcbank&"));
    }

    @Test
    void specialCharactersInNamesAreRemovedNotEscaped() {
        assertEquals("Asha K Co", UpiLink.plain("Asha K & Co", 40, "x"));
        assertEquals("OBrien", UpiLink.plain("O'Brien", 40, "x").replace(" ", ""));
        assertEquals("Ravi Kumar", UpiLink.plain("  Ravi   Kumar ", 40, "x")); // trimmed, runs of spaces collapsed
        assertEquals("Ravi Kumar", UpiLink.plain("Ravi\tKumar", 40, "x"));
        assertEquals("Ravi Kumar", UpiLink.plain("Ravi\nKumar", 40, "x"));
        assertEquals("Ravi Kumar", UpiLink.plain("Ravi+Kumar#?=%", 40, "x"));
        assertEquals("A B", UpiLink.plain("A​B‮".replace("​", " "), 40, "x"));
        assertEquals("Mr. A-B_C", UpiLink.plain("Mr. A-B_C", 40, "x")); // dot, hyphen and underscore are allowed
    }

    @Test
    void accentsAreFoldedAndOtherScriptsAndEmojiAreDropped() {
        assertEquals("Jose Garcia", UpiLink.plain("José García", 40, "x"));
        assertEquals("Zoe", UpiLink.plain("Zoë", 40, "x"));
        assertEquals("Goa Trip", UpiLink.plain("Goa Trip 🏖️", 40, "x"));
        assertEquals("x", UpiLink.plain("अआइ", 40, "x")); // Devanagari only: nothing safe left, so the fallback
        assertEquals("Asha", UpiLink.plain("अआ Asha", 40, "x"));
    }

    @Test
    void blankOrMissingNameFallsBackToTheUpiId() {
        assertEquals("upi://pay?pa=asha@okbank&pn=asha%40okbank&am=1.00&cu=INR&tn=n", UpiLink.build(ID, null, 100, "n"));
        assertTrue(UpiLink.build(ID, "", 100, "n").contains("&pn=asha%40okbank&"));
        assertTrue(UpiLink.build(ID, "   ", 100, "n").contains("&pn=asha%40okbank&"));
        assertTrue(UpiLink.build(ID, "😀", 100, "n").contains("&pn=asha%40okbank&"));
    }

    @Test
    void theNoteLosesItsColonAndFallsBackWhenEmpty() {
        assertTrue(UpiLink.build(ID, "A", 100, "SplitBuys: Goa Trip").endsWith("tn=SplitBuys%20Goa%20Trip"));
        assertTrue(UpiLink.build(ID, "A", 100, "SplitBuys: गोवा").endsWith("tn=SplitBuys")); // group name in Hindi
        assertTrue(UpiLink.build(ID, "A", 100, "").endsWith("tn=SplitBuys"));
        assertTrue(UpiLink.build(ID, "A", 100, null).endsWith("tn=SplitBuys"));
    }

    @Test
    void longTextIsTruncatedWithoutSplittingAWordIntoNothingOrLeavingATrailingSpace() {
        String note = params(UpiLink.build(ID, "A", 100, "x".repeat(200))).get("tn");
        assertEquals("x".repeat(50), note);
        String name = UpiLink.plain("word ".repeat(20), 40, "x");
        assertTrue(name.length() <= 40);
        assertFalse(name.endsWith(" "));
        assertEquals(40, UpiLink.plain("a".repeat(100), 40, "x").length());
    }

    @Test
    void injectionAttemptsInNameOrNoteCannotAddParametersOrChangePayee() {
        String link = UpiLink.build(ID, "x&am=1&pa=evil@bank", 100, "y&pa=evil@bank#frag?x=1");
        Map<String, String> p = params(link);
        assertEquals(ID, p.get("pa"));
        assertEquals("1.00", p.get("am"));
        assertEquals(5, p.size()); // exactly pa, pn, am, cu, tn: nothing extra got in
        assertFalse(link.contains("evil@bank&"));
        assertFalse(link.contains("#"));
    }

    @Test
    void theLinkIsPureAsciiWithNoRawSpacesOrControlCharacters() {
        String link = UpiLink.build(ID, "Ünï 😀 Name\n", 12345, "Trip – café\t");
        assertTrue(link.chars().allMatch(c -> c > 32 && c < 127), link);
    }
}
