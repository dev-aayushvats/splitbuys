package com.splitbuys.backend.common.event;

/** Published (inside the transaction that saved it) when a payment between two members is recorded. */
public record PaymentRecorded(long groupId, String groupName, long actorId, String actorName, long fromUserId, String fromName,
                              long toUserId, String toName, long amountPaise) {
}
