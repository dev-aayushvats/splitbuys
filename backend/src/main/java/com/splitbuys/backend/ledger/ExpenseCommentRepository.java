package com.splitbuys.backend.ledger;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface ExpenseCommentRepository extends JpaRepository<ExpenseComment, Long> {
    /** Newest first and capped, so a very long thread cannot make the request heavy; the service turns it around for display. */
    @EntityGraph(attributePaths = {"author"})
    List<ExpenseComment> findTop200ByGroup_IdAndExpenseIdOrderByIdDesc(Long groupId, Long expenseId);

    @EntityGraph(attributePaths = {"author"})
    Optional<ExpenseComment> findByIdAndGroup_Id(Long id, Long groupId);

    long countByGroup_IdAndExpenseId(Long groupId, Long expenseId);

    long countByAuthor_IdAndCreatedAtAfter(Long authorId, Instant after);

    /** A restored expense has a new id: its comments follow it. */
    @Modifying
    @Query("update ExpenseComment c set c.expenseId = :to where c.group.id = :groupId and c.expenseId = :from")
    void moveToExpense(Long groupId, Long from, Long to);

    @Modifying
    @Query("delete from ExpenseComment c where c.group.id = :groupId")
    void deleteByGroupId(Long groupId);
}
