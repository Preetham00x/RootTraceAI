package com.roottrace.commandcenter.dto;

public record ReliabilityRecommendationResponse(
        String type,
        String priority,
        String title,
        String description,
        String targetService
) {}
