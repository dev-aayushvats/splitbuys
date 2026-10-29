package com.splitbuys.backend.user;

public record UserDto(Long id, String email, String name) {
    public static UserDto from(AppUser u) {
        return new UserDto(u.getId(), u.getEmail(), u.getName());
    }
}
