package com.splitbuys.backend.ledger;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface ExpenseRepository extends JpaRepository<Expense, Long> {
    // Two steps on purpose: LIMIT combined with a collection fetch makes Hibernate page in memory (the whole history is
    // loaded). So page the ids in SQL first, then load full rows for just those ids.
    @Query("select e.id from Expense e where e.group.id = :groupId order by e.createdAt desc, e.id desc")
    List<Long> firstPageIds(Long groupId, Pageable page);

    @Query("select e.id from Expense e where e.group.id = :groupId and (e.createdAt < :at or (e.createdAt = :at and e.id < :id)) "
            + "order by e.createdAt desc, e.id desc")
    List<Long> idsAfter(Long groupId, Instant at, long id, Pageable page);

    @EntityGraph(attributePaths = {"payer", "createdBy", "shares", "shares.user"})
    List<Expense> findByIdIn(Collection<Long> ids);

    @EntityGraph(attributePaths = {"payer", "createdBy", "shares", "shares.user"})
    Optional<Expense> findByIdAndGroup_Id(Long id, Long groupId);

    List<Expense> findByGroup_Id(Long groupId);

    @Query("select new com.splitbuys.backend.ledger.AmountByUser(e.group.id, e.payer.id, sum(e.amountPaise)) "
            + "from Expense e where e.group.id in :groupIds group by e.group.id, e.payer.id")
    List<AmountByUser> paidTotals(Collection<Long> groupIds);

    @Query("select new com.splitbuys.backend.ledger.AmountByUser(s.expense.group.id, s.user.id, sum(s.amountPaise)) "
            + "from ExpenseShare s where s.expense.group.id in :groupIds group by s.expense.group.id, s.user.id")
    List<AmountByUser> owedTotals(Collection<Long> groupIds);

    @Query("select coalesce(sum(e.amountPaise), 0) from Expense e where e.group.id = :groupId")
    long totalSpent(long groupId);
}
