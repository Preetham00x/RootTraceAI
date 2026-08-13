package com.roottrace.commandcenter.dto;

public record ReliabilityPenaltyResponse(
        String category,
        double points,
        String reason
) {}
