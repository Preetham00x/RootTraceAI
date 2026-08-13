package com.roottrace.commandcenter.dto;

import com.roottrace.incident.IncidentSeverity;
import com.roottrace.incident.IncidentStatus;

import java.time.Instant;
import java.util.UUID;

public record ActiveIncidentResponse(
        UUID incidentId,
        String title,
        String service,
        IncidentSeverity severity,
        IncidentStatus status,
        long ageMinutes,
        boolean sloBreached,
        String burnRate,
        String serviceRiskTier,
        double priorityScore,
        String recommendedAttention,
        Instant createdAt
) {}
