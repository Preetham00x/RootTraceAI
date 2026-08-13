package com.roottrace.slo.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

public record RecordSliMeasurementRequest(
        Instant measurementTime,

        @NotNull(message = "totalEvents is required")
        @Min(value = 0, message = "totalEvents must be >= 0")
        Long totalEvents,

        @NotNull(message = "goodEvents is required")
        @Min(value = 0, message = "goodEvents must be >= 0")
        Long goodEvents,

        @NotNull(message = "badEvents is required")
        @Min(value = 0, message = "badEvents must be >= 0")
        Long badEvents,

        BigDecimal value,

        String source
) {
}
