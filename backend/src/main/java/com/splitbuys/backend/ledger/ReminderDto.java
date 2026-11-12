package com.splitbuys.backend.ledger;

import java.time.Instant;

/**
 * @param delivered false when the person cannot be notified (notifications are off, or they have no device): nothing was sent
 *                  and no cooldown was used, so the client can offer another way to say it
 * @param amountPaise what the plan currently asks them to pay the caller
 * @param nextAllowedAt when the caller may remind the same person in the same group again; null if nothing was sent
 */
public record ReminderDto(boolean delivered, long amountPaise, Instant nextAllowedAt) {
}
