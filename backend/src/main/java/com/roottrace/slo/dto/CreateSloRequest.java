package com.roottrace.slo.dto;

import com.roottrace.slo.SloType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateSloRequest(
        @NotBlank(message = "SLO name is required")
        String name,

        String description,

        @NotNull(message = "SLO type is required")
        SloType sloType,

        @NotNull(message = "Target percentage is required")
        @DecimalMin(value = "0.001", message = "Target percentage must be > 0")
        @DecimalMax(value = "100.000", message = "Target percentage must be <= 100")
        BigDecimal targetPercentage,

        @Min(value = 1, message = "Window days must be at least 1")
        Integer windowDays,

        BigDecimal warningThresholdPercentage,

        BigDecimal criticalThresholdPercentage
) {
}
