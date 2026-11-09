package com.splitbuys.backend.push;

import com.splitbuys.backend.common.event.ExpenseAdded;
import com.splitbuys.backend.common.event.PaymentRecorded;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import tools.jackson.databind.json.JsonMapper;

/** What each person is told about a change: who it is for, the words, and where tapping it goes. Pure, so easy to test. */
final class PushMessages {
    private PushMessages() {
    }

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final int MAX_DESCRIPTION = 60;

    /** What one recipient is told: the notification title and body. */
    record Message(String title, String body, long groupId) {
        byte[] toJson() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("title", title);
            payload.put("body", body);
            payload.put("groupId", groupId);
            payload.put("tag", "group-" + groupId); // a newer message for a group replaces the older one on screen
            payload.put("url", "/groups/" + groupId);
            return JSON.writeValueAsBytes(payload);
        }
    }

    /** @return empty for the person who made the change: they already know */
    static Optional<Message> forExpense(ExpenseAdded e, long recipientId) {
        if (recipientId == e.actorId() || !e.memberIds().contains(recipientId)) {
            return Optional.empty();
        }
        long share = e.shares().getOrDefault(recipientId, 0L);
        String effect = "";
        if (recipientId == e.payerId() && share < e.amountPaise()) {
            effect = " · you're owed " + Rupees.format(e.amountPaise() - share);
        } else if (recipientId != e.payerId() && share > 0) {
            effect = " · you owe " + Rupees.format(share);
        }
        String body = e.actorName() + " added " + shorten(e.description()) + " · " + Rupees.format(e.amountPaise()) + effect;
        return Optional.of(new Message(e.groupName(), body, e.groupId()));
    }

    static Optional<Message> forPayment(PaymentRecorded p, long recipientId) {
        if (recipientId == p.actorId()) {
            return Optional.empty();
        }
        String amount = Rupees.format(p.amountPaise());
        if (recipientId == p.toUserId()) {
            String who = p.actorId() == p.fromUserId() ? p.fromName() : p.actorName() + " recorded that " + p.fromName();
            return Optional.of(new Message(p.groupName(), who + " paid you " + amount, p.groupId()));
        }
        if (recipientId == p.fromUserId()) {
            return Optional.of(new Message(p.groupName(), p.actorName() + " recorded your payment of " + amount + " to " + p.toName(), p.groupId()));
        }
        return Optional.empty(); // payments between two other people are not news to you
    }

    private static String shorten(String text) {
        String t = text.strip();
        return t.length() <= MAX_DESCRIPTION ? t : t.substring(0, MAX_DESCRIPTION - 1).strip() + "…";
    }
}
