package com.splitbuys.backend.ledger;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CommentRequest(@NotBlank @Size(max = CommentService.MAX_LENGTH) String text) {
}
