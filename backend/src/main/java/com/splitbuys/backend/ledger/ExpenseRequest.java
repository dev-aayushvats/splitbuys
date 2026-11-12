package com.splitbuys.backend.ledger;

import com.splitbuys.backend.split.SplitType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/** @param category optional: a new expense defaults to OTHER, an edit that leaves it out keeps the current one */
public record ExpenseRequest(
        @NotBlank @Size(max = 120) String description,
        @Positive @Max(Money.MAX_PAISE) long amountPaise,
        @NotNull Long payerId,
        @NotNull SplitType splitType,
        ExpenseCategory category,
        @NotEmpty @Valid List<SplitInput> splits) {
}
