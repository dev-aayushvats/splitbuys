package com.splitbuys.backend.user;

/**
 * The signed-in user's own account, including the private UPI ID. Everyone else's account is a {@link UserDto}, which
 * deliberately has no UPI ID: it is only revealed to the person who has to pay it (see the ledger's settle-up plan).
 */
public record ProfileDto(Long id, String email, String name, String upiId) {
    public static ProfileDto from(AppUser u) {
        return new ProfileDto(u.getId(), u.getEmail(), u.getName(), u.getUpiId());
    }
}
