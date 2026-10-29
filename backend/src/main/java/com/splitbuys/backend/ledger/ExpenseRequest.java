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

public record ExpenseRequest(
        @NotBlank @Size(max = 120) String description,
        @Positive @Max(Money.MAX_PAISE) long amountPaise,
        @NotNull Long payerId,
        @NotNull SplitType splitType,
        @NotEmpty @Valid List<SplitInput> splits) {
}
