package com.splitbuys.backend.common.event;

/** Published (inside the transaction that saved it) when a member reminds another to pay what they owe. */
public record ReminderSent(long groupId, String groupName, long senderId, String senderName, long debtorId, long amountPaise) {
}
