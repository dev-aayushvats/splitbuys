package com.splitbuys.backend.ledger;

import com.splitbuys.backend.user.UserDto;

public record ShareDto(UserDto user, long amountPaise) {
}
