package com.splitbuys.backend.ledger;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record SettlementRequest(
        @NotNull Long fromUserId,
        @NotNull Long toUserId,
        @Positive @Max(Money.MAX_PAISE) long amountPaise) {
}
