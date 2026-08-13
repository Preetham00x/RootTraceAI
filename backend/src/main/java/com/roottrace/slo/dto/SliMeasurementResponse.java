package com.roottrace.slo.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SliMeasurementResponse(
        UUID id,
        UUID sloId,
        Instant measurementTime,
        Long totalEvents,
        Long goodEvents,
        Long badEvents,
        BigDecimal value,
        String source,
        Instant createdAt
) {
}
