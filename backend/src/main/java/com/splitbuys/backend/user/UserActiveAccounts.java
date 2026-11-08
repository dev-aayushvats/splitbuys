package com.splitbuys.backend.user;

import com.splitbuys.backend.common.security.ActiveAccounts;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Lets the security layer ask whether an account still exists, without knowing anything about users. */
@Component
class UserActiveAccounts implements ActiveAccounts {
    private final UserRepository users;

    UserActiveAccounts(UserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isActive(long userId) {
        return users.existsByIdAndDeletedAtIsNull(userId);
    }
}
