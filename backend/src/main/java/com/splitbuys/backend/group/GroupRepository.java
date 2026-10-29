package com.splitbuys.backend.group;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface GroupRepository extends JpaRepository<ExpenseGroup, Long> {
    Optional<ExpenseGroup> findByInviteToken(String token);

    /** Members are fetched in the same query; the id sub-select keeps the fetch from being filtered to one member. */
    @EntityGraph(attributePaths = "members")
    @Query("select g from ExpenseGroup g where g.id in "
            + "(select g2.id from ExpenseGroup g2 join g2.members m where m.id = :userId) order by g.createdAt desc")
    List<ExpenseGroup> findAllForMember(long userId);
}
