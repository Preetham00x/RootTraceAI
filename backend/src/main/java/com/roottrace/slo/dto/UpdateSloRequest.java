package com.roottrace.slo.dto;

import com.roottrace.slo.SloType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;

public record UpdateSloRequest(
        String name,
        String description,
        SloType sloType,

        @DecimalMin(value = "0.001", message = "Target percentage must be > 0")
        @DecimalMax(value = "100.000", message = "Target percentage must be <= 100")
        BigDecimal targetPercentage,

        @Min(value = 1, message = "Window days must be at least 1")
        Integer windowDays,

        BigDecimal warningThresholdPercentage,
        BigDecimal criticalThresholdPercentage,
        Boolean enabled
) {
}
