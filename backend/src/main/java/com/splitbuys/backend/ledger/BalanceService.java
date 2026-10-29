package com.splitbuys.backend.ledger;

import com.splitbuys.backend.group.ExpenseGroup;
import com.splitbuys.backend.group.GroupService;
import com.splitbuys.backend.split.DebtSimplifier;
import com.splitbuys.backend.user.AppUser;
import com.splitbuys.backend.user.UserDto;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Per-member balances of a group and the shortest list of payments that would settle everyone. */
@Service
@Transactional(readOnly = true)
public class BalanceService {
    private final GroupService groups;
    private final NetBalances netBalances;

    BalanceService(GroupService groups, NetBalances netBalances) {
        this.groups = groups;
        this.netBalances = netBalances;
    }

    public BalancesResponse balances(long groupId, long userId) {
        ExpenseGroup group = groups.requireMember(groupId, userId);
        Map<Long, Long> net = netBalances.netBalances(List.of(groupId)).getOrDefault(groupId, Map.of());

        Map<Long, AppUser> byId = new LinkedHashMap<>();
        group.getMembers().forEach(u -> byId.put(u.getId(), u));
        Map<Long, Long> memberNet = new LinkedHashMap<>();
        byId.keySet().forEach(id -> memberNet.put(id, net.getOrDefault(id, 0L)));

        List<BalanceDto> balances = byId.values().stream()
                .map(u -> new BalanceDto(UserDto.from(u), memberNet.get(u.getId())))
                .toList();
        List<TransferDto> settleUp = DebtSimplifier.simplify(memberNet).stream()
                .map(t -> new TransferDto(UserDto.from(byId.get(t.fromUserId())), UserDto.from(byId.get(t.toUserId())),
                        t.amountPaise()))
                .toList();
        return new BalancesResponse(balances, settleUp);
    }
}
