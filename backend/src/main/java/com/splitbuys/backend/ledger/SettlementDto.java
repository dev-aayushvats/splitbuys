package com.splitbuys.backend.ledger;

import com.splitbuys.backend.user.UserDto;
import java.time.Instant;

public record SettlementDto(Long id, UserDto fromUser, UserDto toUser, long amountPaise, UserDto createdBy,
                            Instant createdAt) {
    static SettlementDto from(Settlement s) {
        return new SettlementDto(s.getId(), UserDto.from(s.getFromUser()), UserDto.from(s.getToUser()),
                s.getAmountPaise(), UserDto.from(s.getCreatedBy()), s.getCreatedAt());
    }
}
