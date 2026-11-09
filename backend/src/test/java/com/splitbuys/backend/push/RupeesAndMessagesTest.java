package com.splitbuys.backend.push;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.splitbuys.backend.common.event.ExpenseAdded;
import com.splitbuys.backend.common.event.PaymentRecorded;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RupeesAndMessagesTest {
    @Test
    void rupeesUseIndianGrouping() {
        assertEquals("₹0.00", Rupees.format(0));
        assertEquals("₹0.05", Rupees.format(5));
        assertEquals("₹10.00", Rupees.format(1000));
        assertEquals("₹999.99", Rupees.format(99999));
        assertEquals("₹1,000.00", Rupees.format(100000));
        assertEquals("₹12,345.67", Rupees.format(1234567));
        assertEquals("₹1,00,000.00", Rupees.format(10000000));
        assertEquals("₹1,23,456.78", Rupees.format(12345678));
        assertEquals("₹12,34,56,789.00", Rupees.format(12_345_678_900L));
    }

    // Asha (1) paid 1000 for Asha, Ben (2) and Chi (3), split 500 / 250 / 250; Dev (4) is in the group but not involved
    private static final ExpenseAdded DINNER = new ExpenseAdded(7, "Goa Trip", 1, "Asha", 9, "Dinner", 100000, 1,
            Map.of(1L, 50000L, 2L, 25000L, 3L, 25000L), List.of(1L, 2L, 3L, 4L));

    @Test
    void everyoneButTheActorIsToldAndTheWordsFitTheirShare() {
        assertTrue(PushMessages.forExpense(DINNER, 1).isEmpty()); // the actor knows already
        assertEquals("Asha added Dinner · ₹1,000.00 · you owe ₹250.00", PushMessages.forExpense(DINNER, 2).orElseThrow().body());
        assertEquals("Goa Trip", PushMessages.forExpense(DINNER, 2).orElseThrow().title());
        assertEquals("Asha added Dinner · ₹1,000.00", PushMessages.forExpense(DINNER, 4).orElseThrow().body()); // not involved
        assertTrue(PushMessages.forExpense(DINNER, 99).isEmpty()); // not in the group
    }

    @Test
    void whenSomeoneAddsAnExpenseForAPayerThePayerHearsWhatTheyAreOwed() {
        ExpenseAdded byBen = new ExpenseAdded(7, "Goa Trip", 2, "Ben", 9, "Taxi", 30000, 1, Map.of(1L, 10000L, 2L, 20000L), List.of(1L, 2L));
        assertEquals("Ben added Taxi · ₹300.00 · you're owed ₹200.00", PushMessages.forExpense(byBen, 1).orElseThrow().body());
    }

    @Test
    void longDescriptionsAreShortened() {
        ExpenseAdded e = new ExpenseAdded(7, "G", 1, "Asha", 9, "x".repeat(200), 100, 1, Map.of(1L, 50L, 2L, 50L), List.of(1L, 2L));
        assertTrue(PushMessages.forExpense(e, 2).orElseThrow().body().length() < 120);
    }

    @Test
    void paymentsTellOnlyThePeopleInvolvedAndNeverTheActor() {
        PaymentRecorded byBen = new PaymentRecorded(7, "Goa Trip", 2, "Ben", 2, "Ben", 1, "Asha", 40000);
        assertEquals("Ben paid you ₹400.00", PushMessages.forPayment(byBen, 1).orElseThrow().body());
        assertTrue(PushMessages.forPayment(byBen, 2).isEmpty());
        assertTrue(PushMessages.forPayment(byBen, 3).isEmpty());

        PaymentRecorded byAshaForBen = new PaymentRecorded(7, "Goa Trip", 1, "Asha", 2, "Ben", 1, "Asha", 40000);
        assertEquals("Asha recorded your payment of ₹400.00 to Asha", PushMessages.forPayment(byAshaForBen, 2).orElseThrow().body());

        PaymentRecorded byChiForBen = new PaymentRecorded(7, "Goa Trip", 3, "Chi", 2, "Ben", 1, "Asha", 40000);
        assertEquals("Chi recorded that Ben paid you ₹400.00", PushMessages.forPayment(byChiForBen, 1).orElseThrow().body());
    }

    @Test
    void thePayloadCarriesWhereTappingShouldGo() {
        String json = new String(PushMessages.forExpense(DINNER, 2).orElseThrow().toJson(), StandardCharsets.UTF_8);
        assertTrue(json.contains("\"url\":\"/groups/7\""), json);
        assertTrue(json.contains("\"tag\":\"group-7\""), json);
        assertTrue(json.contains("\"groupId\":7"), json);
        assertFalse(json.contains("password"));
    }
}
