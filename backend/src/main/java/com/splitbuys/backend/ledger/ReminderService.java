package com.splitbuys.backend.ledger;

import com.splitbuys.backend.common.error.ConflictException;
import com.splitbuys.backend.common.error.InvalidRequestException;
import com.splitbuys.backend.common.error.RateLimitedException;
import com.splitbuys.backend.common.event.ReminderSent;
import com.splitbuys.backend.common.notify.DeviceReach;
import com.splitbuys.backend.group.ExpenseGroup;
import com.splitbuys.backend.group.GroupService;
import com.splitbuys.backend.user.AppUser;
import java.time.Duration;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A member nudging someone who owes them. It is only allowed for a payment the settle-up plan really asks for, at most once a
 * day per person and group, and at most {@value #DAILY_CAP} a day overall, so it cannot be used to pester anyone.
 */
@Service
@Transactional
public class ReminderService {
    static final Duration COOLDOWN = Duration.ofHours(24);
    static final int DAILY_CAP = 20;

    private final GroupService groups;
    private final BalanceService balances;
    private final PaymentReminderRepository reminders;
    private final DeviceReach reach;
    private final ApplicationEventPublisher events;

    ReminderService(GroupService groups, BalanceService balances, PaymentReminderRepository reminders, DeviceReach reach,
                    ApplicationEventPublisher events) {
        this.groups = groups;
        this.balances = balances;
        this.reminders = reminders;
        this.reach = reach;
        this.events = events;
    }

    public ReminderDto remind(long groupId, long senderId, long debtorId) {
        ExpenseGroup group = groups.requireMember(groupId, senderId);
        if (senderId == debtorId) {
            throw new InvalidRequestException("You cannot remind yourself");
        }
        AppUser sender = group.findMember(senderId).orElseThrow();
        AppUser debtor = group.findMember(debtorId)
                .orElseThrow(() -> new InvalidRequestException("User " + debtorId + " is not in this group"));

        long owed = balances.balances(groupId, senderId).settleUp().stream()
                .filter(t -> t.from().id() == debtorId && t.to().id() == senderId)
                .mapToLong(TransferDto::amountPaise).sum();
        if (owed <= 0) {
            throw new ConflictException("NOTHING_OWED", "They do not owe you anything right now");
        }
        Instant now = Instant.now();
        if (reminders.findFirstByGroup_IdAndSender_IdAndDebtor_IdAndCreatedAtAfterOrderByCreatedAtDesc(
                groupId, senderId, debtorId, now.minus(COOLDOWN)).isPresent()) {
            throw new RateLimitedException("You already reminded them recently. Try again tomorrow.");
        }
        if (reminders.countBySender_IdAndCreatedAtAfter(senderId, now.minus(COOLDOWN)) >= DAILY_CAP) {
            throw new RateLimitedException("You have sent a lot of reminders today. Try again tomorrow.");
        }
        if (!reach.canNotify(debtorId)) {
            return new ReminderDto(false, owed, null); // nothing sent, so nothing used up
        }
        PaymentReminder saved = reminders.save(new PaymentReminder(group, sender, debtor, owed));
        events.publishEvent(new ReminderSent(groupId, group.getName(), senderId, label(sender), debtorId, owed));
        return new ReminderDto(true, owed, saved.getCreatedAt().plus(COOLDOWN));
    }

    private static String label(AppUser u) {
        return u.getName() == null || u.getName().isBlank() ? u.getEmail() : u.getName();
    }
}
