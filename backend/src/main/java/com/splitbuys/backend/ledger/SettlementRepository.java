package com.splitbuys.backend.ledger;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface SettlementRepository extends JpaRepository<Settlement, Long> {
    @Query("select s.id from Settlement s where s.group.id = :groupId order by s.createdAt desc, s.id desc")
    List<Long> firstPageIds(Long groupId, Pageable page);

    @Query("select s.id from Settlement s where s.group.id = :groupId and (s.createdAt < :at or (s.createdAt = :at and s.id < :id)) "
            + "order by s.createdAt desc, s.id desc")
    List<Long> idsAfter(Long groupId, Instant at, long id, Pageable page);

    @EntityGraph(attributePaths = {"fromUser", "toUser", "createdBy"})
    List<Settlement> findByIdIn(Collection<Long> ids);

    Optional<Settlement> findByIdAndGroup_Id(Long id, Long groupId);

    List<Settlement> findByGroup_Id(Long groupId);

    @Query("select new com.splitbuys.backend.ledger.AmountByUser(s.group.id, s.fromUser.id, sum(s.amountPaise)) "
            + "from Settlement s where s.group.id in :groupIds group by s.group.id, s.fromUser.id")
    List<AmountByUser> paidOut(Collection<Long> groupIds);

    @Query("select new com.splitbuys.backend.ledger.AmountByUser(s.group.id, s.toUser.id, sum(s.amountPaise)) "
            + "from Settlement s where s.group.id in :groupIds group by s.group.id, s.toUser.id")
    List<AmountByUser> receivedTotals(Collection<Long> groupIds);
}
