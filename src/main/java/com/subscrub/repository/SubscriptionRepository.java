package com.subscrub.repository;

import com.subscrub.model.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    List<Subscription> findByUserIdAndStatus(String userId, String status);

    Optional<Subscription> findByUserIdAndMerchant(String userId, String merchant);

    @Query("SELECT s FROM Subscription s WHERE s.userId = :uid " +
           "AND s.status = 'ACTIVE' " +
           "AND s.nextDueDate BETWEEN :from AND :to")
    List<Subscription> findUpcoming(
        @Param("uid") String userId,
        @Param("from") LocalDate from,
        @Param("to") LocalDate to
    );
}
