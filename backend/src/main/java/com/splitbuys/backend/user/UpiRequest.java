package com.splitbuys.backend.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpiRequest(@NotBlank @Size(max = 100) String upiId) {
}
