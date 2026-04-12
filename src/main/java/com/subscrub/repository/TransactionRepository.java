package com.subscrub.repository;

import com.subscrub.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByUserIdOrderByDateDesc(String userId);

    @Query("SELECT t FROM Transaction t WHERE t.userId = :uid " +
           "AND t.date BETWEEN :from AND :to ORDER BY t.date")
    List<Transaction> findByUserAndDateRange(
        @Param("uid") String userId,
        @Param("from") LocalDate from,
        @Param("to") LocalDate to
    );

    @Query("SELECT DISTINCT t.merchant FROM Transaction t WHERE t.userId = :uid")
    List<String> findDistinctMerchants(@Param("uid") String userId);
}
