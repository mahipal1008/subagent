package com.subscrub.dto;

import java.util.List;

public record SimResult(
    double currentMonthlyTotal,
    double projectedMonthlyTotal,
    double monthlySaving,
    double yearlySaving,
    List<String> cancelled,
    List<String> remaining
) {}
