package com.splitbuys.backend.ledger;

import jakarta.validation.constraints.NotNull;

/** @param toUserId the member being reminded (who owes the caller money) */
public record ReminderRequest(@NotNull Long toUserId) {
}
