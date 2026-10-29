package com.splitbuys.backend.ledger;

import com.splitbuys.backend.user.UserDto;

public record BalanceDto(UserDto user, long netPaise) {
}
