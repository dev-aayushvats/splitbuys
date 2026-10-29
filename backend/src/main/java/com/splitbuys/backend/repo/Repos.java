package com.splitbuys.backend.repo;

import com.splitbuys.backend.domain.AppUser;
import com.splitbuys.backend.domain.Expense;
import com.splitbuys.backend.domain.ExpenseGroup;
import com.splitbuys.backend.domain.OtpCode;
import com.splitbuys.backend.domain.Settlement;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public final class Repos {
    private Repos() {
    }

    public interface UserRepository extends JpaRepository<AppUser, Long> {
        Optional<AppUser> findByEmail(String email);
    }

    public interface GroupRepository extends JpaRepository<ExpenseGroup, Long> {
        Optional<ExpenseGroup> findByInviteToken(String token);

        List<ExpenseGroup> findByMembers_IdOrderByCreatedAtDesc(Long userId);
    }

    public interface ExpenseRepository extends JpaRepository<Expense, Long> {
        List<Expense> findByGroupIdOrderByCreatedAtDescIdDesc(Long groupId);
    }

    public interface SettlementRepository extends JpaRepository<Settlement, Long> {
        List<Settlement> findByGroupIdOrderByCreatedAtDescIdDesc(Long groupId);
    }

    public interface OtpRepository extends JpaRepository<OtpCode, Long> {
        Optional<OtpCode> findFirstByEmailOrderByCreatedAtDesc(String email);

        void deleteByEmail(String email);
    }
}
