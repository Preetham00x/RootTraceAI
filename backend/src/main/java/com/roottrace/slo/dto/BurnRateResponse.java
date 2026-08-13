package com.roottrace.slo.dto;

import java.time.Instant;
import java.util.UUID;

public record BurnRateResponse(
        UUID sloId,
        String serviceName,
        String sloName,
        double burnRate,
        String severity, // "NORMAL", "ELEVATED", "HIGH", "CRITICAL"
        int windowMinutes,
        double actualErrorRatePercentage,
        double allowedErrorRatePercentage,
        Instant evaluatedAt
) {
}
