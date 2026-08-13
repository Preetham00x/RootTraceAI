package com.roottrace.commandcenter.dto;

import java.util.List;

public record ReliabilityScoreResponse(
        double score,
        double baseScore,
        String riskTier,
        List<ReliabilityPenaltyResponse> penalties
) {}
