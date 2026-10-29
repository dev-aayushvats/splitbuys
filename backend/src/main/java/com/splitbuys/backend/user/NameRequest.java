package com.splitbuys.backend.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NameRequest(@NotBlank @Size(max = 60) String name) {
}
