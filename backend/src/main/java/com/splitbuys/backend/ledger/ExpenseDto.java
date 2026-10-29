package com.splitbuys.backend.ledger;

import com.splitbuys.backend.split.SplitType;
import com.splitbuys.backend.user.UserDto;
import java.time.Instant;
import java.util.List;

public record ExpenseDto(Long id, String description, long amountPaise, UserDto payer, UserDto createdBy,
                         Instant createdAt, SplitType splitType, List<ShareDto> shares) {
    static ExpenseDto from(Expense e) {
        return new ExpenseDto(e.getId(), e.getDescription(), e.getAmountPaise(), UserDto.from(e.getPayer()),
                UserDto.from(e.getCreatedBy()), e.getCreatedAt(), e.getSplitType(),
                e.getShares().stream().map(s -> new ShareDto(UserDto.from(s.getUser()), s.getAmountPaise())).toList());
    }
}
