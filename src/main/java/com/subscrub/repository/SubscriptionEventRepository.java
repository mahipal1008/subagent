package com.subscrub.repository;

import com.subscrub.model.SubscriptionEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

public interface SubscriptionEventRepository extends JpaRepository<SubscriptionEvent, Long> {

    List<SubscriptionEvent> findBySubscriptionIdOrderByDateDesc(Long subscriptionId);

    List<SubscriptionEvent> findByUserIdAndDateBetween(String userId, LocalDate from, LocalDate to);

    List<SubscriptionEvent> findByAnomalyFlagTrue();
}
