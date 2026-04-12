package com.subscrub.service;

import com.subscrub.dto.IntentResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * Resolves the SQL template for a given intent and fills all named parameters.
 */
@Service
@RequiredArgsConstructor
public class SQLBuilder {

    private final SemanticLayer semanticLayer;

    /**
     * Resolves the SQL template for the given intent and fills all named params.
     *
     * @return map with keys: "sql" (String), "params" (Map&lt;String,Object&gt;)
     */
    public Map<String, Object> build(IntentResult intent, String userId) {
        String template = semanticLayer.getSqlTemplate(intent.intent());
        if (template == null) {
            throw new IllegalArgumentException("No metric for intent: " + intent.intent());
        }

        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);

        switch (intent.intent()) {
            case "UPCOMING_DEBITS" -> {
                int days = intent.daysAhead() != null ? intent.daysAhead() : 10;
                params.put("daysAhead", days);
            }
            case "PRICE_CHANGES" -> {
                int year = intent.year() != null ? intent.year() : LocalDate.now().getYear();
                params.put("year", year);
            }
            case "SPEND_SUMMARY" -> {
                params.put("month", LocalDate.now().getMonth().getValue());
                params.put("year",  LocalDate.now().getYear());
            }
            // LIST_SUBSCRIPTIONS and CANCEL_SIMULATION only need userId
            default -> {}
        }

        return Map.of("sql", template, "params", params);
    }

    /**
     * Builds the data window description string for transparency display.
     */
    public String buildDataWindow(IntentResult intent) {
        return switch (intent.intent()) {
            case "UPCOMING_DEBITS"      ->
                "Next %d days from today (%s)".formatted(
                    intent.daysAhead() != null ? intent.daysAhead() : 10,
                    LocalDate.now());
            case "PRICE_CHANGES"        ->
                "Full year %d".formatted(
                    intent.year() != null ? intent.year() : LocalDate.now().getYear());
            case "SPEND_SUMMARY"        ->
                "Month %d / %d".formatted(
                    LocalDate.now().getMonthValue(), LocalDate.now().getYear());
            case "MONTHLY_TREND"        -> "Last 12 months";
            case "CATEGORY_BREAKDOWN"   -> "Current active subscriptions";
            case "ANOMALY_LIST"         -> "All anomalies on record";
            case "MOST_EXPENSIVE"       -> "Top 5 most expensive active subscriptions";
            case "CANCELLED_SUBS"       -> "All cancelled/paused subscriptions";
            case "RECENT_TRANSACTIONS"  -> "Last 20 transactions";
            case "USER_INFO"            -> "User profile";
            default                     -> "Current snapshot";
        };
    }
}
