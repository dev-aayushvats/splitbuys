package com.splitbuys.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.splitbuys.backend.domain.AppUser;
import com.splitbuys.backend.repo.Repos.GroupRepository;
import com.splitbuys.backend.repo.Repos.UserRepository;
import com.splitbuys.backend.split.SplitType;
import com.splitbuys.backend.web.ApiException;
import com.splitbuys.backend.web.Dto.ExpenseRequest;
import com.splitbuys.backend.web.Dto.GroupDetail;
import com.splitbuys.backend.web.Dto.SplitInput;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:sqlite:target/group-service-test.db",
        "app.jwt.secret=test-secret-test-secret-test-secret-123"})
class GroupServiceTest {
    @Autowired GroupService groups;
    @Autowired LedgerService ledger;
    @Autowired UserRepository users;
    @Autowired GroupRepository groupRepo;

    private long newUser() {
        AppUser u = new AppUser();
        u.email = UUID.randomUUID() + "@test.dev";
        return users.save(u).id;
    }

    /** A group owned by `a` with `b` joined. */
    private GroupDetail pair(long a, long b) {
        GroupDetail g = groups.create(a, "Trip");
        groups.join(g.inviteToken(), b);
        return g;
    }

    @Test
    void renameTrimsAndAppliesForAnyMember() {
        long a = newUser(), b = newUser();
        GroupDetail g = pair(a, b);
        assertEquals("Goa", groups.rename(g.id(), b, "  Goa ").name());
    }

    @Test
    void nonMemberCannotRename() {
        long a = newUser(), outsider = newUser();
        GroupDetail g = groups.create(a, "Trip");
        assertThrows(ApiException.class, () -> groups.rename(g.id(), outsider, "X"));
    }

    @Test
    void settledMemberCanBeRemovedByAnotherMember() {
        long a = newUser(), b = newUser();
        GroupDetail g = pair(a, b);
        groups.removeMember(g.id(), a, b);
        assertEquals(1, groups.get(g.id(), a).members().size());
    }

    @Test
    void memberWithBalanceCannotBeRemoved() {
        long a = newUser(), b = newUser();
        GroupDetail g = pair(a, b);
        ledger.addExpense(g.id(), a, new ExpenseRequest("Dinner", 1000, a, SplitType.EQUAL,
                List.of(new SplitInput(a, null), new SplitInput(b, null))));
        assertThrows(ApiException.class, () -> groups.removeMember(g.id(), a, b));
        assertThrows(ApiException.class, () -> groups.leave(g.id(), b));
    }

    @Test
    void deleteIsRefusedWhileOthersRemain() {
        long a = newUser(), b = newUser();
        GroupDetail g = pair(a, b);
        assertThrows(ApiException.class, () -> groups.delete(g.id(), a));
    }

    @Test
    void soleMemberCanDeleteAndHistoryGoesToo() {
        long a = newUser();
        GroupDetail g = groups.create(a, "Solo");
        ledger.addExpense(g.id(), a, new ExpenseRequest("Snack", 500, a, SplitType.EQUAL,
                List.of(new SplitInput(a, null))));
        groups.delete(g.id(), a);
        assertTrue(groupRepo.findById(g.id()).isEmpty());
        assertThrows(ApiException.class, () -> groups.get(g.id(), a));
    }

    @Test
    void lastMemberLeavingRemovesGroup() {
        long a = newUser();
        GroupDetail g = groups.create(a, "Solo");
        groups.leave(g.id(), a);
        assertTrue(groupRepo.findById(g.id()).isEmpty());
    }
}
