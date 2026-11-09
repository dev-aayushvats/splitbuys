package com.splitbuys.backend.account;

import com.splitbuys.backend.auth.OtpService;
import com.splitbuys.backend.common.error.ConflictException;
import com.splitbuys.backend.common.error.InvalidRequestException;
import com.splitbuys.backend.common.idempotency.IdempotencyService;
import com.splitbuys.backend.group.GroupService;
import com.splitbuys.backend.group.GroupSummary;
import com.splitbuys.backend.push.PushService;
import com.splitbuys.backend.user.UserService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deleting an account touches users, groups, sign-in codes and remembered requests, so it lives in a feature of its own
 * that sits on top of those, instead of making any of them depend on the others.
 *
 * What happens, all in one transaction (it all happens or none of it does):
 * <ol>
 *   <li>refuse while the person is owed or owes money in any group, the same rule as leaving a group, because the
 *       other members' balances must keep adding up;</li>
 *   <li>leave every group (a group left empty is erased with its history, as when the last member leaves normally);</li>
 *   <li>erase the identity: email, name and UPI ID, keeping the row that other people's history points at;</li>
 *   <li>drop pending sign-in codes, remembered requests and notification subscriptions;</li>
 *   <li>the account's tokens stop working (see ActiveAccountValidator).</li>
 * </ol>
 */
@Service
@Transactional
public class AccountService {
    private final UserService users;
    private final GroupService groups;
    private final OtpService otp;
    private final IdempotencyService idempotency;
    private final PushService push;

    AccountService(UserService users, GroupService groups, OtpService otp, IdempotencyService idempotency, PushService push) {
        this.push = push;
        this.users = users;
        this.groups = groups;
        this.otp = otp;
        this.idempotency = idempotency;
    }

    public void delete(long userId, String typedEmail) {
        String email = users.get(userId).email();
        if (!email.equalsIgnoreCase(typedEmail.trim())) {
            throw new InvalidRequestException("Type your email address exactly to confirm");
        }

        List<GroupSummary> mine = groups.list(userId);
        List<String> unsettled = mine.stream().filter(g -> g.myBalancePaise() != 0).map(GroupSummary::name).toList();
        if (!unsettled.isEmpty()) {
            throw new ConflictException("BALANCE_NOT_SETTLED",
                    "Settle up before deleting your account. Unsettled in: " + String.join(", ", unsettled));
        }

        mine.forEach(g -> groups.leave(g.id(), userId));
        users.closeAccount(userId);
        otp.forget(email);
        idempotency.forgetUser(userId);
        push.forgetUser(userId);
    }
}
