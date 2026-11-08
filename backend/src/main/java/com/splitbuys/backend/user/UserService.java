package com.splitbuys.backend.user;

import com.splitbuys.backend.common.error.NotFoundException;
import com.splitbuys.backend.common.security.AccountClosed;
import java.time.Clock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The only way other features reach users: nobody else touches the repository. */
@Service
@Transactional(readOnly = true)
public class UserService {
    /** Result of resolving a verified email to an account. */
    public record Resolved(AppUser user, boolean created) {
    }

    private final UserRepository users;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public UserService(UserRepository users, ApplicationEventPublisher events, Clock clock) {
        this.users = users;
        this.events = events;
        this.clock = clock;
    }

    public AppUser require(long id) {
        return users.findById(id).orElseThrow(() -> new NotFoundException("User not found"));
    }

    public ProfileDto get(long id) {
        return ProfileDto.from(require(id));
    }

    @Transactional
    public ProfileDto rename(long id, String name) {
        AppUser user = require(id);
        user.rename(name);
        return ProfileDto.from(user);
    }

    /** Sets or replaces where the user wants to be paid. */
    @Transactional
    public ProfileDto setUpiId(long id, String upiId) {
        AppUser user = require(id);
        user.setUpiId(UpiId.normalize(upiId));
        return ProfileDto.from(user);
    }

    @Transactional
    public ProfileDto clearUpiId(long id) {
        AppUser user = require(id);
        user.setUpiId(null);
        return ProfileDto.from(user);
    }

    /** Erases the person's identity (see {@link AppUser#closeAccount}) and announces it so their tokens stop working. */
    @Transactional
    public void closeAccount(long id) {
        require(id).closeAccount(clock.instant());
        events.publishEvent(new AccountClosed(id));
    }

    @Transactional
    public Resolved findOrCreateByEmail(String email) {
        return users.findByEmail(email)
                .map(u -> new Resolved(u, false))
                .orElseGet(() -> new Resolved(users.save(new AppUser(email)), true));
    }
}
