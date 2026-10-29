package com.splitbuys.backend.ledger;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface ExpenseRepository extends JpaRepository<Expense, Long> {
    @EntityGraph(attributePaths = {"payer", "createdBy", "shares", "shares.user"})
    List<Expense> findByGroup_IdOrderByCreatedAtDescIdDesc(Long groupId);

    @EntityGraph(attributePaths = {"payer", "createdBy", "shares", "shares.user"})
    Optional<Expense> findByIdAndGroup_Id(Long id, Long groupId);

    List<Expense> findByGroup_Id(Long groupId);

    @Query("select new com.splitbuys.backend.ledger.AmountByUser(e.group.id, e.payer.id, sum(e.amountPaise)) "
            + "from Expense e where e.group.id in :groupIds group by e.group.id, e.payer.id")
    List<AmountByUser> paidTotals(Collection<Long> groupIds);

    @Query("select new com.splitbuys.backend.ledger.AmountByUser(s.expense.group.id, s.user.id, sum(s.amountPaise)) "
            + "from ExpenseShare s where s.expense.group.id in :groupIds group by s.expense.group.id, s.user.id")
    List<AmountByUser> owedTotals(Collection<Long> groupIds);
}
