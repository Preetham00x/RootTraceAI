package com.roottrace.commandcenter.dto;

import com.roottrace.incident.dto.IncidentSummaryResponse;
import com.roottrace.slo.dto.ReliabilityTrendResponse;
import com.roottrace.slo.dto.SloEvaluationResponse;

import java.util.List;

public record CommandCenterOverviewResponse(
        int windowDays,
        double overallReliabilityScore,
        String overallRiskTier,

        int totalServices,
        int servicesAtRisk,

        int totalIncidents,
        int activeIncidents,
        int resolvedIncidents,

        int criticalIncidents,
        int highIncidents,

        Double meanMttrMinutes,
        Double meanMttdMinutes,

        int sloCount,
        int healthySlos,
        int warningSlos,
        int breachedSlos,

        double errorBudgetConsumptionPercent,

        int openPostmortemActionItems,
        int overdueActionItems,

        int failedRunbookExecutions,

        List<ServiceHealthSummaryResponse> topRiskyServices,
        List<IncidentSummaryResponse> recentCriticalIncidents,
        List<SloEvaluationResponse> sloBreaches,
        List<ReliabilityTrendResponse.ReliabilityDataPoint> reliabilityTrend
) {}
