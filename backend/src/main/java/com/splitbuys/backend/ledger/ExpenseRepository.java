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

    @Query("select e.category, sum(e.amountPaise), count(e) from Expense e where e.group.id = :groupId group by e.category")
    List<Object[]> totalsByCategory(long groupId);

    @Query("select e.category, sum(s.amountPaise) from ExpenseShare s join s.expense e "
            + "where e.group.id = :groupId and s.user.id = :userId group by e.category")
    List<Object[]> myShareByCategory(long groupId, long userId);

    @Query("select coalesce(sum(e.amountPaise), 0) from Expense e where e.group.id = :groupId")
    long totalSpent(long groupId);

    // Activity: expenses the user paid for or has a share in, across the given groups, as (id, time) pairs so pages from
    // expenses and payments can be merged before any full row is loaded.
    @Query("select new com.splitbuys.backend.ledger.Stamp(e.id, e.createdAt) from Expense e where e.group.id in :groupIds "
            + "and (e.payer.id = :userId or exists (select 1 from ExpenseShare s where s.expense = e and s.user.id = :userId)) "
            + "order by e.createdAt desc, e.id desc")
    List<Stamp> involvingFirst(Collection<Long> groupIds, long userId, Pageable page);

    @Query("select new com.splitbuys.backend.ledger.Stamp(e.id, e.createdAt) from Expense e where e.group.id in :groupIds "
            + "and (e.payer.id = :userId or exists (select 1 from ExpenseShare s where s.expense = e and s.user.id = :userId)) "
            + "and (e.createdAt < :at or (e.createdAt = :at and e.id < :id)) order by e.createdAt desc, e.id desc")
    List<Stamp> involvingAfter(Collection<Long> groupIds, long userId, Instant at, long id, Pageable page);

    @EntityGraph(attributePaths = {"group", "payer", "createdBy", "shares", "shares.user"})
    List<Expense> findForActivityByIdIn(Collection<Long> ids);
}
