package com.splitbuys.backend.push;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {
    Optional<PushSubscription> findByEndpoint(String endpoint);

    List<PushSubscription> findByUserIdIn(Collection<Long> userIds);

    List<PushSubscription> findByUserIdOrderByCreatedAtAscIdAsc(long userId);

    @Modifying
    @Query("delete from PushSubscription s where s.userId = :userId and s.endpoint = :endpoint")
    int deleteByUserIdAndEndpoint(long userId, String endpoint);

    @Modifying
    @Query("delete from PushSubscription s where s.userId = :userId")
    int deleteByUserId(long userId);
}
