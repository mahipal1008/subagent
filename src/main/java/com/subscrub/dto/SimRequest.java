package com.subscrub.dto;

import java.util.List;

public record SimRequest(String userId, List<String> cancelTargets) {}
