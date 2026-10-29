package com.splitbuys.backend.ledger;

import com.splitbuys.backend.user.UserDto;

public record TransferDto(UserDto from, UserDto to, long amountPaise) {
}
