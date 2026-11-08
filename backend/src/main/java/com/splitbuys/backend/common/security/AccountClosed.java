package com.splitbuys.backend.common.security;

/** Published when an account is deleted, so cached "this account is fine" answers can be dropped at once. */
public record AccountClosed(long userId) {
}
