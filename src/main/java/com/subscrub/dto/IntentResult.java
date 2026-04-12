package com.subscrub.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Returned by IntentRouter — used by NLQOrchestrator to branch.
 * Spring AI maps Gemini's structured JSON output directly into this record.
 */
public record IntentResult(

    @JsonProperty("intent")
    String intent,               // LIST_SUBSCRIPTIONS | PRICE_CHANGES | UPCOMING_DEBITS
                                 // SPEND_SUMMARY | CANCEL_SIMULATION | UNKNOWN

    @JsonProperty("confidence")
    double confidence,           // 0.0 – 1.0

    @JsonProperty("service_name")
    String serviceName,          // nullable — "Netflix", "Spotify", etc.

    @JsonProperty("days_ahead")
    Integer daysAhead,           // nullable — for UPCOMING_DEBITS

    @JsonProperty("year")
    Integer year,                // nullable — for PRICE_CHANGES

    @JsonProperty("cancel_targets")
    List<String> cancelTargets   // nullable — for CANCEL_SIMULATION
) {}
