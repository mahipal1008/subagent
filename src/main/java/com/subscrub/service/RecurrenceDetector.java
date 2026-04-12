package com.subscrub.service;

import com.subscrub.model.Subscription;
import com.subscrub.model.Transaction;
import com.subscrub.repository.SubscriptionRepository;
import com.subscrub.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Detects recurring transaction patterns and populates
 * the subscriptions and subscription_events tables.
 * Called after every CSV upload.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecurrenceDetector {

    private final TransactionRepository txRepo;
    private final SubscriptionRepository subRepo;
    private final JdbcTemplate jdbc;

    private static final int MIN_OCCURRENCES     = 3;
    private static final double AMOUNT_VARIANCE_PCT = 0.15; // 15% tolerance
    private static final int MONTHLY_GAP_MIN     = 25;
    private static final int MONTHLY_GAP_MAX     = 40;
    private static final int YEARLY_GAP_MIN      = 350;
    private static final int YEARLY_GAP_MAX      = 380;

    @Transactional
    public int detect(String userId) {
        List<Transaction> txs = txRepo.findByUserIdOrderByDateDesc(userId);

        // Group by normalized merchant name
        Map<String, List<Transaction>> grouped = txs.stream()
            .collect(Collectors.groupingBy(t -> normalize(t.getMerchant())));

        int detected = 0;
        for (Map.Entry<String, List<Transaction>> entry : grouped.entrySet()) {
            List<Transaction> group = entry.getValue().stream()
                .sorted(Comparator.comparing(Transaction::getDate))
                .toList();

            if (group.size() < MIN_OCCURRENCES) continue;

            String frequency = detectFrequency(group);
            if (frequency == null) continue;

            if (!amountsAreConsistent(group)) continue;

            upsertSubscription(userId, group, frequency);
            detected++;
        }

        log.info("RecurrenceDetector: {} subscriptions detected for user {}", detected, userId);
        return detected;
    }

    private String detectFrequency(List<Transaction> sorted) {
        List<Long> gaps = new ArrayList<>();
        for (int i = 1; i < sorted.size(); i++) {
            gaps.add(ChronoUnit.DAYS.between(
                sorted.get(i - 1).getDate(), sorted.get(i).getDate()));
        }
        double avgGap = gaps.stream().mapToLong(Long::longValue).average().orElse(0);

        if (avgGap >= MONTHLY_GAP_MIN && avgGap <= MONTHLY_GAP_MAX) return "MONTHLY";
        if (avgGap >= YEARLY_GAP_MIN  && avgGap <= YEARLY_GAP_MAX)  return "YEARLY";
        if (avgGap >= 6  && avgGap <= 10) return "WEEKLY";
        if (avgGap >= 85 && avgGap <= 95) return "QUARTERLY";
        return null;
    }

    private boolean amountsAreConsistent(List<Transaction> txs) {
        DoubleSummaryStatistics stats = txs.stream()
            .mapToDouble(t -> t.getAmount().doubleValue())
            .summaryStatistics();
        if (stats.getMin() == 0) return false;
        double variance = (stats.getMax() - stats.getMin()) / stats.getMin();
        return variance <= AMOUNT_VARIANCE_PCT;
    }

    private void upsertSubscription(String userId, List<Transaction> group, String frequency) {
        String merchant = group.get(0).getMerchant();
        Transaction last = group.get(group.size() - 1);

        Subscription sub = subRepo.findByUserIdAndMerchant(userId, merchant)
            .orElse(new Subscription());

        sub.setUserId(userId);
        sub.setName(merchant);
        sub.setMerchant(merchant);
        sub.setCurrentAmount(last.getAmount());
        sub.setFrequency(frequency);
        sub.setStatus("ACTIVE");
        sub.setFirstSeen(group.get(0).getDate());
        sub.setLastSeen(last.getDate());
        sub.setNextDueDate(computeNextDue(last.getDate(), frequency));
        sub.setAvgAmount(computeAvg(group));

        Subscription saved = subRepo.save(sub);

        // Insert subscription_events for each charge
        for (Transaction tx : group) {
            jdbc.update(
                "INSERT INTO subscription_events " +
                "(subscription_id, user_id, date, amount, source_transaction_id, " +
                "anomaly_flag, amount_delta, amount_delta_pct) " +
                "VALUES (?,?,?,?,?,?,?,?)",
                saved.getId(), userId, tx.getDate(), tx.getAmount(), tx.getId(),
                false, BigDecimal.ZERO, BigDecimal.ZERO
            );
        }

        // Mark anomalies (price changes)
        markAnomalies(saved.getId());
    }

    private void markAnomalies(Long subscriptionId) {
        List<Map<String, Object>> events = jdbc.queryForList(
            "SELECT id, amount FROM subscription_events " +
            "WHERE subscription_id = ? ORDER BY date ASC", subscriptionId);

        for (int i = 1; i < events.size(); i++) {
            BigDecimal prev = (BigDecimal) events.get(i - 1).get("amount");
            BigDecimal curr = (BigDecimal) events.get(i).get("amount");
            if (curr.compareTo(prev) != 0) {
                BigDecimal delta    = curr.subtract(prev);
                BigDecimal deltaPct = delta.divide(prev, 4, RoundingMode.HALF_UP)
                                          .multiply(BigDecimal.valueOf(100));
                long eventId = ((Number) events.get(i).get("id")).longValue();
                jdbc.update(
                    "UPDATE subscription_events " +
                    "SET anomaly_flag=true, amount_delta=?, amount_delta_pct=? WHERE id=?",
                    delta, deltaPct, eventId
                );
            }
        }
    }

    private LocalDate computeNextDue(LocalDate lastSeen, String frequency) {
        return switch (frequency) {
            case "MONTHLY"   -> lastSeen.plusMonths(1);
            case "YEARLY"    -> lastSeen.plusYears(1);
            case "WEEKLY"    -> lastSeen.plusWeeks(1);
            case "QUARTERLY" -> lastSeen.plusMonths(3);
            default          -> lastSeen.plusMonths(1);
        };
    }

    private BigDecimal computeAvg(List<Transaction> txs) {
        double avg = txs.stream()
            .mapToDouble(t -> t.getAmount().doubleValue())
            .average().orElse(0);
        return BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP);
    }

    private String normalize(String merchant) {
        return merchant == null ? "" :
            merchant.toLowerCase()
                .replaceAll("[^a-z0-9 ]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
