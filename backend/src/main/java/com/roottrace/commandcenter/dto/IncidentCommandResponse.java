package com.roottrace.commandcenter.dto;

import com.roottrace.ai.diagnosis.dto.DiagnosisDetailResponse;
import com.roottrace.incident.dto.IncidentResponse;
import com.roottrace.integration.dto.JiraTicketResponse;
import com.roottrace.integration.dto.RunbookExecutionResponse;
import com.roottrace.intelligence.dto.CorrelatedIncidentResponse;
import com.roottrace.investigation.dto.InvestigationPlanResponse;
import com.roottrace.postmortem.dto.PostmortemActionItemResponse;
import com.roottrace.postmortem.dto.PostmortemResponse;
import com.roottrace.slo.dto.SloEvaluationResponse;

import java.util.List;

public record IncidentCommandResponse(
        IncidentResponse incident,
        DiagnosisDetailResponse diagnosis,
        InvestigationPlanResponse investigation,
        List<CorrelatedIncidentResponse> relatedIncidents,
        PostmortemResponse postmortem,
        List<PostmortemActionItemResponse> postmortemActionItems,
        SloImpactSummary sloImpact,
        ErrorBudgetImpactSummary errorBudgetImpact,
        List<RunbookExecutionResponse> runbookExecutions,
        List<JiraTicketResponse> externalTickets,
        List<String> timelineEvents,
        List<ReliabilityRecommendationResponse> recommendations
) {
    public record SloImpactSummary(
            boolean serviceHasSlos,
            int breachedSlosCount,
            List<SloEvaluationResponse> affectedSlos
    ) {}

    public record ErrorBudgetImpactSummary(
            double avgBudgetConsumedPercentage,
            double avgBudgetRemainingPercentage,
            String status
    ) {}
}
