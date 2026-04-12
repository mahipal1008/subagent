package com.subscrub.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Structured output for Gemini entity-extraction (Step 2C of the NLQ pipeline).
 *
 * Gemini is asked ONLY to extract named parameters from the user query.
 * Intent classification is handled upstream by the BGE-M3 + cross-encoder pipeline.
 */
public record EntityParams(

    /** Subscription/service name mentioned by the user (e.g. "Netflix", "Spotify"). */
    @JsonProperty("service_name")
    String serviceName,

    /** Number of days ahead mentioned in future-debit queries (e.g. 7, 30). */
    @JsonProperty("days_ahead")
    Integer daysAhead,

    /** Calendar year mentioned in spend/summary queries (e.g. 2024, 2025). */
    @JsonProperty("year")
    Integer year,

    /** List of service names the user wants to simulate cancelling. */
    @JsonProperty("cancel_targets")
    List<String> cancelTargets
) {}
