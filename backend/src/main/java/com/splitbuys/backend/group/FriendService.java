package com.splitbuys.backend.group;

import com.splitbuys.backend.split.DebtSimplifier;
import com.splitbuys.backend.user.AppUser;
import com.splitbuys.backend.user.UserDto;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The people a user shares groups with, and what each owes the other.
 *
 * <p>Balances in this app are per person per group, not per pair, so "what Bo owes me" has to come from somewhere. It
 * comes from the settle-up plan (the same one the Balances tab shows): in each shared group, the payment between the
 * viewer and the friend that the plan asks for. That is what they would actually pay each other, and it adds up across
 * groups. It can differ from a naive "who paid for whom" tally, because the plan routes debts through third people to
 * need fewer payments.
 */
@Service
@Transactional(readOnly = true)
public class FriendService {
    private final GroupRepository groups;
    private final LedgerPort ledger;

    FriendService(GroupRepository groups, LedgerPort ledger) {
        this.groups = groups;
        this.ledger = ledger;
    }

    private static final class Acc {
        final AppUser user;
        long balance;
        final List<FriendDto.FriendGroup> groups = new ArrayList<>();

        Acc(AppUser user) {
            this.user = user;
        }
    }

    public List<FriendDto> list(long userId) {
        List<ExpenseGroup> mine = groups.findAllForMember(userId);
        Map<Long, Map<Long, Long>> nets = ledger.netBalances(mine.stream().map(ExpenseGroup::getId).toList());

        Map<Long, Acc> friends = new LinkedHashMap<>();
        for (ExpenseGroup g : mine) {
            Map<Long, Long> net = new HashMap<>();
            g.getMembers().forEach(m -> net.put(m.getId(), nets.getOrDefault(g.getId(), Map.of()).getOrDefault(m.getId(), 0L)));

            // what the plan has the viewer pay (negative) or receive (positive), per other member
            Map<Long, Long> withViewer = new HashMap<>();
            for (DebtSimplifier.Transfer t : DebtSimplifier.simplify(net)) {
                if (t.fromUserId() == userId) {
                    withViewer.merge(t.toUserId(), -t.amountPaise(), Long::sum);
                } else if (t.toUserId() == userId) {
                    withViewer.merge(t.fromUserId(), t.amountPaise(), Long::sum);
                }
            }

            for (AppUser m : g.getMembers()) {
                if (m.getId() == userId) {
                    continue;
                }
                long amount = withViewer.getOrDefault(m.getId(), 0L);
                Acc acc = friends.computeIfAbsent(m.getId(), id -> new Acc(m));
                acc.balance += amount;
                acc.groups.add(new FriendDto.FriendGroup(g.getId(), g.getName(), amount));
            }
        }

        Comparator<FriendDto.FriendGroup> groupOrder = Comparator
                .comparingLong((FriendDto.FriendGroup g) -> Math.abs(g.balancePaise())).reversed()
                .thenComparing(FriendDto.FriendGroup::name, String.CASE_INSENSITIVE_ORDER);
        Comparator<FriendDto> friendOrder = Comparator
                .comparingLong((FriendDto f) -> Math.abs(f.balancePaise())).reversed()
                .thenComparing(f -> displayName(f.user()), String.CASE_INSENSITIVE_ORDER);

        return friends.values().stream()
                .map(a -> new FriendDto(UserDto.from(a.user), a.balance, a.groups.stream().sorted(groupOrder).toList()))
                .sorted(friendOrder)
                .toList();
    }

    private static String displayName(UserDto u) {
        return u.name() != null && !u.name().isBlank() ? u.name() : u.email();
    }
}
