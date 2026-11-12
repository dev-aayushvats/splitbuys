package com.splitbuys.backend.ledger;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface ExpenseHistoryRepository extends JpaRepository<ExpenseHistory, Long> {
    @Query("select h.id from ExpenseHistory h where h.group.id = :groupId order by h.createdAt desc, h.id desc")
    List<Long> firstPageIds(Long groupId, Pageable page);

    @Query("select h.id from ExpenseHistory h where h.group.id = :groupId and (h.createdAt < :at or (h.createdAt = :at and h.id < :id)) "
            + "order by h.createdAt desc, h.id desc")
    List<Long> idsAfter(Long groupId, Instant at, long id, Pageable page);

    @EntityGraph(attributePaths = {"actor"})
    List<ExpenseHistory> findByIdIn(Collection<Long> ids);

    Optional<ExpenseHistory> findByIdAndGroup_Id(Long id, Long groupId);

    @EntityGraph(attributePaths = {"actor"})
    List<ExpenseHistory> findTop50ByGroup_IdAndExpenseIdOrderByIdDesc(Long groupId, Long expenseId);

    /** The newest history row of each given expense: only that row's edit can still be undone. */
    @Query("select h.expenseId, max(h.id) from ExpenseHistory h where h.group.id = :groupId and h.expenseId in :expenseIds "
            + "group by h.expenseId")
    List<Object[]> newestIdPerExpense(Long groupId, Collection<Long> expenseIds);

    @Modifying
    @Query("delete from ExpenseHistory h where h.group.id = :groupId")
    void deleteByGroupId(Long groupId);
}
