package com.subscrub.repository;

import com.subscrub.model.MonthlySummary;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

public interface MonthlySummaryRepository extends JpaRepository<MonthlySummary, Long> {

    List<MonthlySummary> findByUserIdOrderByMonthDesc(String userId);

    List<MonthlySummary> findByUserIdAndMonthBetween(String userId, LocalDate from, LocalDate to);

    List<MonthlySummary> findByUserIdAndCategory(String userId, String category);
}
