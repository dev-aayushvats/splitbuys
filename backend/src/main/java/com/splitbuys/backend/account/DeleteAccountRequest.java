package com.splitbuys.backend.account;

import jakarta.validation.constraints.NotBlank;

/** The person must type their own email address, so an account is never deleted by a stray request. */
record DeleteAccountRequest(@NotBlank String email) {
}
