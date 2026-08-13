package com.roottrace.commandcenter.dto;

import com.roottrace.integration.dto.RunbookExecutionResponse;
import com.roottrace.intelligence.dto.IncidentTrendsResponse;
import com.roottrace.postmortem.dto.PostmortemActionItemResponse;
import com.roottrace.slo.dto.ReliabilityTrendResponse;
import com.roottrace.slo.dto.SloEvaluationResponse;

import java.util.List;

public record ServiceHealthDetailResponse(
        String serviceName,
        double healthScore,
        String riskTier,
        ServiceIncidentSummary incidentSummary,
        ServiceSloSummary sloSummary,
        ServiceErrorBudgetSummary errorBudgetSummary,
        ServiceBurnRateSummary burnRateSummary,
        List<IncidentTrendsResponse.TrendDataPoint> incidentTrend,
        List<ReliabilityTrendResponse.ReliabilityDataPoint> reliabilityTrend,
        List<String> topRootCauses,
        List<PostmortemActionItemResponse> openActionItems,
        List<RunbookExecutionResponse> recentRunbookExecutions,
        List<String> riskFactors,
        List<ReliabilityRecommendationResponse> recommendations
) {
    public record ServiceIncidentSummary(
            int totalIncidents,
            int activeIncidents,
            int resolvedIncidents,
            int criticalIncidents,
            int highIncidents,
            Double meanMttrMinutes,
            Double meanMttdMinutes,
            double recurrenceRate
    ) {}

    public record ServiceSloSummary(
            int totalSlos,
            int healthySlos,
            int warningSlos,
            int breachedSlos,
            List<SloEvaluationResponse> slos
    ) {}

    public record ServiceErrorBudgetSummary(
            double averageConsumedPercent,
            double averageRemainingPercent,
            String status
    ) {}

    public record ServiceBurnRateSummary(
            double highestBurnRate,
            String highestSeverity,
            int criticalBurnCount
    ) {}
}
