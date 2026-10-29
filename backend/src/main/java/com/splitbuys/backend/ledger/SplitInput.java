package com.splitbuys.backend.ledger;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** value: ignored for EQUAL, paise for EXACT, percent (e.g. 33.5) for PERCENT, weight for SHARES. */
public record SplitInput(@NotNull Long userId, BigDecimal value) {
}
