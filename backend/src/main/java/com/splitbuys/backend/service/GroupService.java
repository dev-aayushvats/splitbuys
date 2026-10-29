package com.splitbuys.backend.service;

import com.splitbuys.backend.domain.AppUser;
import com.splitbuys.backend.domain.Expense;
import com.splitbuys.backend.domain.ExpenseGroup;
import com.splitbuys.backend.domain.ExpenseShare;
import com.splitbuys.backend.domain.Settlement;
import com.splitbuys.backend.repo.Repos.ExpenseRepository;
import com.splitbuys.backend.repo.Repos.GroupRepository;
import com.splitbuys.backend.repo.Repos.SettlementRepository;
import com.splitbuys.backend.repo.Repos.UserRepository;
import com.splitbuys.backend.split.DebtSimplifier;
import com.splitbuys.backend.web.ApiException;
import com.splitbuys.backend.web.Dto.BalanceDto;
import com.splitbuys.backend.web.Dto.BalancesResponse;
import com.splitbuys.backend.web.Dto.GroupDetail;
import com.splitbuys.backend.web.Dto.GroupSummary;
import com.splitbuys.backend.web.Dto.InvitePreview;
import com.splitbuys.backend.web.Dto.TransferDto;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class GroupService {
    private final GroupRepository groups;
    private final UserRepository users;
    private final ExpenseRepository expenses;
    private final SettlementRepository settlements;
    private final SecureRandom random = new SecureRandom();

    public GroupService(GroupRepository groups, UserRepository users, ExpenseRepository expenses,
                        SettlementRepository settlements) {
        this.groups = groups;
        this.users = users;
        this.expenses = expenses;
        this.settlements = settlements;
    }

    public AppUser user(long userId) {
        return users.findById(userId).orElseThrow(() -> ApiException.notFound("User not found"));
    }

    /** Loads a group the user belongs to; non-members get the same 404 as a missing group. */
    public ExpenseGroup requireMemberGroup(long groupId, long userId) {
        ExpenseGroup g = groups.findById(groupId).orElseThrow(() -> ApiException.notFound("Group not found"));
        if (!g.hasMember(userId)) {
            throw ApiException.notFound("Group not found");
        }
        return g;
    }

    public GroupDetail create(long userId, String name) {
        ExpenseGroup g = new ExpenseGroup();
        g.name = name.trim();
        g.inviteToken = newToken();
        g.members.add(user(userId));
        return detail(groups.save(g));
    }

    @Transactional(readOnly = true)
    public List<GroupSummary> list(long userId) {
        return groups.findByMembers_IdOrderByCreatedAtDesc(userId).stream()
                .map(g -> new GroupSummary(g.id, g.name, g.members.size(), netBalances(g).getOrDefault(userId, 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public GroupDetail get(long groupId, long userId) {
        return detail(requireMemberGroup(groupId, userId));
    }

    public GroupDetail regenerateInvite(long groupId, long userId) {
        ExpenseGroup g = requireMemberGroup(groupId, userId);
        g.inviteToken = newToken();
        return detail(g);
    }

    @Transactional(readOnly = true)
    public InvitePreview previewInvite(String token, long userId) {
        ExpenseGroup g = byToken(token);
        return new InvitePreview(g.name, g.members.size(), g.hasMember(userId), g.hasMember(userId) ? g.id : null);
    }

    public GroupDetail join(String token, long userId) {
        ExpenseGroup g = byToken(token);
        if (!g.hasMember(userId)) {
            g.members.add(user(userId));
        }
        return detail(g);
    }

    public void leave(long groupId, long userId) {
        ExpenseGroup g = requireMemberGroup(groupId, userId);
        if (netBalances(g).getOrDefault(userId, 0L) != 0) {
            throw ApiException.badRequest("Settle up your balance before leaving the group");
        }
        g.members.removeIf(u -> u.id == userId);
    }

    @Transactional(readOnly = true)
    public BalancesResponse balances(long groupId, long userId) {
        ExpenseGroup g = requireMemberGroup(groupId, userId);
        Map<Long, Long> net = netBalances(g);
        Map<Long, AppUser> byId = new LinkedHashMap<>();
        g.members.forEach(u -> byId.put(u.id, u));
        List<BalanceDto> balances = byId.values().stream()
                .map(u -> new BalanceDto(Mappers.user(u), net.getOrDefault(u.id, 0L)))
                .toList();
        List<TransferDto> settleUp = DebtSimplifier.simplify(net).stream()
                .map(t -> new TransferDto(Mappers.user(byId.get(t.fromUserId())), Mappers.user(byId.get(t.toUserId())),
                        t.amountPaise()))
                .toList();
        return new BalancesResponse(balances, settleUp);
    }

    /** Net paise per user in the group: positive = owed money, negative = owes money. */
    public Map<Long, Long> netBalances(ExpenseGroup g) {
        Map<Long, Long> net = new LinkedHashMap<>();
        g.members.forEach(u -> net.put(u.id, 0L));
        for (Expense e : expenses.findByGroupIdOrderByCreatedAtDescIdDesc(g.id)) {
            net.merge(e.payer.id, e.amountPaise, Long::sum);
            for (ExpenseShare s : e.shares) {
                net.merge(s.user.id, -s.amountPaise, Long::sum);
            }
        }
        for (Settlement s : settlements.findByGroupIdOrderByCreatedAtDescIdDesc(g.id)) {
            net.merge(s.fromUser.id, s.amountPaise, Long::sum);
            net.merge(s.toUser.id, -s.amountPaise, Long::sum);
        }
        return net;
    }

    private ExpenseGroup byToken(String token) {
        return groups.findByInviteToken(token).orElseThrow(() -> ApiException.notFound("Invite link is invalid or has been reset"));
    }

    private GroupDetail detail(ExpenseGroup g) {
        return new GroupDetail(g.id, g.name, g.inviteToken, g.members.stream().map(Mappers::user).toList());
    }

    private String newToken() {
        byte[] bytes = new byte[18];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
