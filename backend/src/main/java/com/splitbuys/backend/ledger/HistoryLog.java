package com.splitbuys.backend.ledger;

import com.splitbuys.backend.group.ExpenseGroup;
import com.splitbuys.backend.user.AppUser;
import com.splitbuys.backend.user.UserDto;
import com.splitbuys.backend.user.UserService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Writes the history of expenses and turns stored rows back into what the API returns. */
@Component
class HistoryLog {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final ExpenseHistoryRepository repo;
    private final UserService users;

    HistoryLog(ExpenseHistoryRepository repo, UserService users) {
        this.repo = repo;
        this.users = users;
    }

    ExpenseHistory record(ExpenseGroup group, long expenseId, HistoryAction action, AppUser actor,
                          ExpenseSnapshot before, ExpenseSnapshot after) {
        return repo.save(new ExpenseHistory(group, expenseId, action, actor,
                before == null ? null : JSON.writeValueAsString(before), after == null ? null : JSON.writeValueAsString(after)));
    }

    static ExpenseSnapshot before(ExpenseHistory h) {
        return h.getBeforeJson() == null ? null : JSON.readValue(h.getBeforeJson(), ExpenseSnapshot.class);
    }

    static ExpenseSnapshot after(ExpenseHistory h) {
        return h.getAfterJson() == null ? null : JSON.readValue(h.getAfterJson(), ExpenseSnapshot.class);
    }

    /**
     * @param liveIds ids of expenses that exist right now (an edit can only be undone on a live expense)
     */
    List<HistoryDto> toDtos(long groupId, List<ExpenseHistory> rows, Set<Long> liveIds) {
        Map<Long, Long> newest = new HashMap<>();
        for (Object[] r : repo.newestIdPerExpense(groupId, rows.stream().map(ExpenseHistory::getExpenseId).distinct().toList())) {
            newest.put(((Number) r[0]).longValue(), ((Number) r[1]).longValue());
        }
        Set<Long> userIds = new HashSet<>();
        List<ExpenseSnapshot[]> snaps = new ArrayList<>();
        for (ExpenseHistory h : rows) {
            ExpenseSnapshot b = before(h), a = after(h);
            snaps.add(new ExpenseSnapshot[]{b, a});
            for (ExpenseSnapshot s : new ExpenseSnapshot[]{b, a}) {
                if (s != null) {
                    userIds.add(s.payerId());
                    s.shares().forEach(sh -> userIds.add(sh.userId()));
                }
            }
        }
        Map<Long, AppUser> people = users.byIds(userIds);
        List<HistoryDto> out = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            ExpenseHistory h = rows.get(i);
            boolean latest = h.getId().equals(newest.get(h.getExpenseId()));
            boolean undoable = switch (h.getAction()) {
                case EDITED -> latest && liveIds.contains(h.getExpenseId());
                case DELETED -> h.getRestoredAs() == null;
                default -> false;
            };
            out.add(new HistoryDto(h.getId(), h.getExpenseId(), h.getAction(), UserDto.from(h.getActor()), h.getCreatedAt(),
                    dto(snaps.get(i)[0], people), dto(snaps.get(i)[1], people), undoable));
        }
        return out;
    }

    private static SnapshotDto dto(ExpenseSnapshot s, Map<Long, AppUser> people) {
        if (s == null) {
            return null;
        }
        return new SnapshotDto(s.description(), s.amountPaise(), person(people, s.payerId()), s.splitType(),
                s.shares().stream().map(sh -> new ShareDto(person(people, sh.userId()), sh.amountPaise())).toList());
    }

    private static UserDto person(Map<Long, AppUser> people, long id) {
        AppUser u = people.get(id);
        return u == null ? new UserDto(id, null, null) : UserDto.from(u);
    }
}
