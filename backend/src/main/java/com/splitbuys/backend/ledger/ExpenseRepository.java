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
    // The filter is applied here, in SQL, so a page is a page of matches (not a page of everything with some hidden by the client).
    @Query("select e.id from Expense e where e.group.id = :groupId and lower(e.description) like :pattern escape '!' and e.category in :categories "
            + "order by e.createdAt desc, e.id desc")
    List<Long> firstPageIds(Long groupId, String pattern, Collection<ExpenseCategory> categories, Pageable page);

    @Query("select e.id from Expense e where e.group.id = :groupId and lower(e.description) like :pattern escape '!' and e.category in :categories "
            + "and (e.createdAt < :at or (e.createdAt = :at and e.id < :id)) order by e.createdAt desc, e.id desc")
    List<Long> idsAfter(Long groupId, String pattern, Collection<ExpenseCategory> categories, Instant at, long id, Pageable page);

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

    /** One row per category: the user's own share in each of six consecutive months (b0..b6 are the month boundaries). */
    @Query("select e.category, sum(case when e.createdAt >= :b0 and e.createdAt < :b1 then s.amountPaise else 0 end), sum(case when e.createdAt >= :b1 and e.createdAt < :b2 then s.amountPaise else 0 end), sum(case when e.createdAt >= :b2 and e.createdAt < :b3 then s.amountPaise else 0 end), sum(case when e.createdAt >= :b3 and e.createdAt < :b4 then s.amountPaise else 0 end), sum(case when e.createdAt >= :b4 and e.createdAt < :b5 then s.amountPaise else 0 end), sum(case when e.createdAt >= :b5 and e.createdAt < :b6 then s.amountPaise else 0 end) "
            + "from ExpenseShare s join s.expense e where s.user.id = :userId and e.group.id in :groupIds "
            + "and e.createdAt >= :b0 and e.createdAt < :b6 group by e.category")
    List<Object[]> myShareByMonth(long userId, Collection<Long> groupIds, Instant b0, Instant b1, Instant b2, Instant b3,
                                  Instant b4, Instant b5, Instant b6);

    @Query("select coalesce(sum(e.amountPaise), 0) from Expense e where e.group.id = :groupId")
    long totalSpent(long groupId);

    // Activity: expenses the user paid for or has a share in, across the given groups, as (id, time) pairs so pages from
    // expenses and payments can be merged before any full row is loaded.
    @Query("select new com.splitbuys.backend.ledger.Stamp(e.id, e.createdAt) from Expense e where e.group.id in :groupIds "
            + "and (e.payer.id = :userId or exists (select 1 from ExpenseShare s where s.expense = e and s.user.id = :userId)) "
            + "and lower(e.description) like :pattern escape '!' and e.category in :categories "
            + "order by e.createdAt desc, e.id desc")
    List<Stamp> involvingFirst(Collection<Long> groupIds, long userId, String pattern, Collection<ExpenseCategory> categories, Pageable page);

    @Query("select new com.splitbuys.backend.ledger.Stamp(e.id, e.createdAt) from Expense e where e.group.id in :groupIds "
            + "and (e.payer.id = :userId or exists (select 1 from ExpenseShare s where s.expense = e and s.user.id = :userId)) "
            + "and lower(e.description) like :pattern escape '!' and e.category in :categories "
            + "and (e.createdAt < :at or (e.createdAt = :at and e.id < :id)) order by e.createdAt desc, e.id desc")
    List<Stamp> involvingAfter(Collection<Long> groupIds, long userId, String pattern, Collection<ExpenseCategory> categories, Instant at, long id, Pageable page);

    @EntityGraph(attributePaths = {"group", "payer", "createdBy", "shares", "shares.user"})
    List<Expense> findForActivityByIdIn(Collection<Long> ids);
}
