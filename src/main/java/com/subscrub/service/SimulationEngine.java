package com.subscrub.service;

import com.subscrub.dto.SimResult;
import com.subscrub.model.Subscription;
import com.subscrub.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure Java + JPA — no AI needed. Simple arithmetic on subscription data.
 */
@Service
@RequiredArgsConstructor
public class SimulationEngine {

    private final SubscriptionRepository subscriptionRepo;

    /**
     * Simulates cancelling the given services and computes monthly/yearly savings.
     *
     * @param userId         the user
     * @param cancelTargets  list of service names to simulate cancelling
     * @return SimResult with before/after totals and savings
     */
    public SimResult simulate(String userId, List<String> cancelTargets) {
        List<Subscription> active = subscriptionRepo
            .findByUserIdAndStatus(userId, "ACTIVE");

        // Normalize target names for loose matching (case-insensitive partial)
        List<String> normalizedTargets = cancelTargets.stream()
            .map(String::toLowerCase)
            .toList();

        double currentTotal = 0.0;
        double projectedTotal = 0.0;
        List<String> cancelled = new ArrayList<>();
        List<String> remaining = new ArrayList<>();

        for (Subscription sub : active) {
            double monthly = toMonthlyAmount(sub);
            currentTotal += monthly;

            boolean isCancelled = normalizedTargets.stream()
                .anyMatch(t -> sub.getName().toLowerCase().contains(t)
                            || sub.getMerchant().toLowerCase().contains(t));

            if (isCancelled) {
                cancelled.add(sub.getName());
            } else {
                projectedTotal += monthly;
                remaining.add(sub.getName());
            }
        }

        double saving = currentTotal - projectedTotal;

        return new SimResult(
            currentTotal,
            projectedTotal,
            saving,
            saving * 12,
            cancelled,
            remaining
        );
    }

    /**
     * Normalizes subscription amounts to a monthly figure.
     * YEARLY &rarr; divide by 12, WEEKLY &rarr; multiply by 4.33, QUARTERLY &rarr; divide by 3.
     */
    private double toMonthlyAmount(Subscription sub) {
        double amount = sub.getCurrentAmount().doubleValue();
        return switch (sub.getFrequency()) {
            case "YEARLY"    -> amount / 12.0;
            case "WEEKLY"    -> amount * 4.33;
            case "QUARTERLY" -> amount / 3.0;
            default          -> amount; // MONTHLY
        };
    }
}
