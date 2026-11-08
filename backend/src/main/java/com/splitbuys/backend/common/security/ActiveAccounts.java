package com.splitbuys.backend.common.security;

/** Whether an account is still usable. Declared here and implemented by the user feature, so security never depends on it. */
public interface ActiveAccounts {
    boolean isActive(long userId);
}
