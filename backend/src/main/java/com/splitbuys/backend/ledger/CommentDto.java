package com.splitbuys.backend.ledger;

import com.splitbuys.backend.user.UserDto;
import java.time.Instant;

public record CommentDto(Long id, UserDto author, String text, Instant createdAt) {
    static CommentDto from(ExpenseComment c) {
        return new CommentDto(c.getId(), UserDto.from(c.getAuthor()), c.getBody(), c.getCreatedAt());
    }
}
