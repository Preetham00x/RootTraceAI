package com.roottrace.slo.dto;

import com.roottrace.slo.SloType;
import com.roottrace.user.dto.UserDto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SloResponse(
        UUID id,
        String serviceName,
        String name,
        String description,
        SloType sloType,
        BigDecimal targetPercentage,
        Integer windowDays,
        BigDecimal warningThresholdPercentage,
        BigDecimal criticalThresholdPercentage,
        Boolean enabled,
        UserDto createdBy,
        Instant createdAt,
        Instant updatedAt
) {
}
