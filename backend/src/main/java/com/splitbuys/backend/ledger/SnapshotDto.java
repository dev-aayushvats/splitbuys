package com.splitbuys.backend.ledger;

import com.splitbuys.backend.split.SplitType;
import com.splitbuys.backend.user.UserDto;
import java.util.List;

public record SnapshotDto(String description, long amountPaise, UserDto payer, SplitType splitType, ExpenseCategory category,
                          List<ShareDto> shares) {
}
