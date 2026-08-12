package com.roottrace.ai.diagnosis.dto;

public record AiMetricsResponse(
        long helpfulCount,
        long unhelpfulCount,
        double helpfulnessRate
) {
}
