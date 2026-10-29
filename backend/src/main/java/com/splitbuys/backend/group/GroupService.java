package com.splitbuys.backend.group;

import com.splitbuys.backend.common.error.ConflictException;
import com.splitbuys.backend.common.error.NotFoundException;
import com.splitbuys.backend.user.AppUser;
import com.splitbuys.backend.user.UserService;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Groups and who is in them. Money lives in the ledger feature; this class only asks it (through {@link LedgerPort})
 * whether a member is settled up.
 */
@Service
@Transactional
public class GroupService {
    private final GroupRepository groups;
    private final UserService users;
    private final LedgerPort ledger;
    private final SecureRandom random = new SecureRandom();

    GroupService(GroupRepository groups, UserService users, LedgerPort ledger) {
        this.groups = groups;
        this.users = users;
        this.ledger = ledger;
    }

    /**
     * The access check every group-scoped operation starts with. A non-member gets the same 404 as a missing group, so
     * group ids cannot be probed.
     */
    @Transactional(readOnly = true)
    public ExpenseGroup requireMember(long groupId, long userId) {
        ExpenseGroup g = groups.findById(groupId).orElseThrow(() -> new NotFoundException("Group not found"));
        if (!g.hasMember(userId)) {
            throw new NotFoundException("Group not found");
        }
        return g;
    }

    public GroupDetail create(long userId, String name) {
        AppUser creator = users.require(userId);
        return GroupDetail.from(groups.save(new ExpenseGroup(name, newToken(), creator)));
    }

    @Transactional(readOnly = true)
    public List<GroupSummary> list(long userId) {
        List<ExpenseGroup> mine = groups.findAllForMember(userId);
        Map<Long, Map<Long, Long>> nets = ledger.netBalances(mine.stream().map(ExpenseGroup::getId).toList());
        return mine.stream()
                .map(g -> new GroupSummary(g.getId(), g.getName(), g.getMembers().size(),
                        nets.getOrDefault(g.getId(), Map.of()).getOrDefault(userId, 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public GroupDetail get(long groupId, long userId) {
        return GroupDetail.from(requireMember(groupId, userId));
    }

    public GroupDetail rename(long groupId, long userId, String name) {
        ExpenseGroup g = requireMember(groupId, userId);
        g.rename(name);
        return GroupDetail.from(g);
    }

    public GroupDetail regenerateInvite(long groupId, long userId) {
        ExpenseGroup g = requireMember(groupId, userId);
        g.rotateInviteToken(newToken());
        return GroupDetail.from(g);
    }

    @Transactional(readOnly = true)
    public InvitePreview previewInvite(String token, long userId) {
        ExpenseGroup g = byToken(token);
        boolean member = g.hasMember(userId);
        return new InvitePreview(g.getName(), g.getMembers().size(), member, member ? g.getId() : null);
    }

    public GroupDetail join(String token, long userId) {
        ExpenseGroup g = byToken(token);
        if (!g.hasMember(userId)) {
            g.addMember(users.require(userId));
        }
        return GroupDetail.from(g);
    }

    public void leave(long groupId, long userId) {
        removeMember(groupId, userId, userId);
    }

    /**
     * Removes a member who has no outstanding balance. Any member may remove another (friend groups have no owner).
     * When the last member goes, the group and its history go with them.
     */
    public void removeMember(long groupId, long actorId, long targetId) {
        ExpenseGroup g = requireMember(groupId, actorId);
        if (!g.hasMember(targetId)) {
            throw new NotFoundException("Member not found");
        }
        if (netOf(groupId, targetId) != 0) {
            throw new ConflictException("BALANCE_NOT_SETTLED", actorId == targetId
                    ? "Settle up your balance before leaving the group"
                    : "Settle up their balance before removing them from the group");
        }
        g.removeMember(targetId);
        if (g.getMembers().isEmpty()) {
            purge(g);
        }
    }

    /** Deleting a group erases its expenses and payments, so it is only allowed for a sole remaining member. */
    public void delete(long groupId, long userId) {
        ExpenseGroup g = requireMember(groupId, userId);
        if (g.getMembers().size() > 1) {
            throw new ConflictException("GROUP_HAS_MEMBERS", "Remove the other members first, or just leave the group");
        }
        purge(g);
    }

    private void purge(ExpenseGroup g) {
        ledger.purge(g.getId());
        groups.delete(g);
    }

    private long netOf(long groupId, long userId) {
        return ledger.netBalances(List.of(groupId)).getOrDefault(groupId, Map.of()).getOrDefault(userId, 0L);
    }

    private ExpenseGroup byToken(String token) {
        return groups.findByInviteToken(token)
                .orElseThrow(() -> new NotFoundException("Invite link is invalid or has been reset"));
    }

    private String newToken() {
        byte[] bytes = new byte[18];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
