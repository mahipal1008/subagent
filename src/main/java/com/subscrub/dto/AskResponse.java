package com.subscrub.dto;

import java.util.List;
import java.util.Map;

public record AskResponse(
    String answer,                   // plain language explanation
    String intent,                   // detected intent
    double confidence,               // 0.0-1.0
    String sqlUsed,                  // shown to user for transparency
    List<Map<String, Object>> data,  // table rows returned
    String metricVersion,            // semantic layer version used
    String dataWindow                // e.g. "2025-01-01 to 2026-03-31"
) {}
