package com.splitbuys.backend.ledger;

import com.splitbuys.backend.user.UserDto;

/**
 * One suggested payment. {@code upiId} is filled in only for the person who has to pay (and only if the receiver has set
 * a UPI ID), so a UPI ID is never shown to someone who has no reason to pay it.
 */
public record TransferDto(UserDto from, UserDto to, long amountPaise, String upiId) {
}
