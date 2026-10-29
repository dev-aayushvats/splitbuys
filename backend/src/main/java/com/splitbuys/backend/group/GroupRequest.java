package com.splitbuys.backend.group;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GroupRequest(@NotBlank @Size(max = 80) String name) {
}
