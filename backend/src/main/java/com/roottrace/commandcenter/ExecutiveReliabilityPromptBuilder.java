package com.roottrace.commandcenter;

import com.roottrace.commandcenter.dto.CommandCenterOverviewResponse;
import com.roottrace.commandcenter.dto.ReliabilityScoreResponse;
import com.roottrace.commandcenter.dto.ServiceHealthSummaryResponse;
import com.roottrace.incident.dto.IncidentSummaryResponse;
import com.roottrace.slo.dto.SloEvaluationResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ExecutiveReliabilityPromptBuilder {

    public String buildPrompt(
            CommandCenterOverviewResponse overview,
            ReliabilityScoreResponse scoreResponse,
            List<ServiceHealthSummaryResponse> riskyServices) {

        StringBuilder sb = new StringBuilder();

        sb.append("""
                You are RootTraceAI's Principal Executive SRE Advisor & Operational Intelligence Specialist.
                Your role is to produce a concise, blameless, highly actionable executive reliability briefing for engineering leadership.

                ### STRICT GROUNDING & ACCURACY DIRECTIVES:
                1. You must ONLY use the provided deterministic facts, metrics, service names, incident counts, and SLO numbers.
                2. Do NOT invent, recalculate, or hallucinate metrics, percentages, root causes, incidents, services, or risk scores.
                3. Do NOT contradict any supplied deterministic number.
                4. Maintain a blameless, constructive SRE perspective emphasizing Google SRE principles.
                5. Highlight systemic risks, cross-service bottlenecks, and prioritized executive action items.

                ### DETERMINISTIC OPERATIONAL FACTS (LAST %d DAYS):
                - Organization Reliability Score: %.1f / 100 (%s)
                - Total Monitored Services: %d (%d services at risk)
                - Total Incidents: %d (Active: %d, Resolved: %d)
                - Severity Breakdown: %d Critical, %d High
                - Mean Time to Resolve (MTTR): %.1f min | Mean Time to Detect (MTTD): %.1f min
                - Active SLOs: %d Total (%d Healthy, %d Warning, %d Breached)
                - Average Error Budget Consumption: %.1f%%
                - Open Postmortem Action Items: %d (%d Overdue)
                - Failed Automated Remediation Runbooks: %d

                ### RELIABILITY PENALTY BREAKDOWN:
                """.formatted(
                overview.windowDays(),
                overview.overallReliabilityScore(),
                overview.overallRiskTier(),
                overview.totalServices(),
                overview.servicesAtRisk(),
                overview.totalIncidents(),
                overview.activeIncidents(),
                overview.resolvedIncidents(),
                overview.criticalIncidents(),
                overview.highIncidents(),
                overview.meanMttrMinutes() != null ? overview.meanMttrMinutes() : 0.0,
                overview.meanMttdMinutes() != null ? overview.meanMttdMinutes() : 0.0,
                overview.sloCount(),
                overview.healthySlos(),
                overview.warningSlos(),
                overview.breachedSlos(),
                overview.errorBudgetConsumptionPercent(),
                overview.openPostmortemActionItems(),
                overview.overdueActionItems(),
                overview.failedRunbookExecutions()
        ));

        if (scoreResponse != null && scoreResponse.penalties() != null) {
            for (var p : scoreResponse.penalties()) {
                sb.append(String.format("- [%s] -%.1f pts: %s\n", p.category(), p.points(), p.reason()));
            }
        }

        sb.append("\n### TOP RISKY SERVICES:\n");
        if (riskyServices != null && !riskyServices.isEmpty()) {
            for (ServiceHealthSummaryResponse s : riskyServices) {
                sb.append(String.format("- Service '%s': Health=%.1f (%s), Incidents=%d (%d active, %d critical), Breached SLOs=%d, Error Budget Consumed=%.1f%%, Overdue Actions=%d\n",
                        s.serviceName(), s.healthScore(), s.riskTier(), s.incidentCount(), s.activeIncidentCount(), s.criticalIncidentCount(), s.breachedSloCount(), s.errorBudgetConsumptionPercent(), s.overdueActionItems()));
            }
        } else {
            sb.append("- All services currently operating at healthy risk tiers.\n");
        }

        sb.append("\n### ACTIVE SLO BREACHES:\n");
        if (overview.sloBreaches() != null && !overview.sloBreaches().isEmpty()) {
            for (SloEvaluationResponse breach : overview.sloBreaches()) {
                sb.append(String.format("- %s [%s]: Actual=%.3f%% (Target: %.3f%%), Budget Consumed=%.1f%%\n",
                        breach.sloName(), breach.serviceName(), breach.actualPercentage(), breach.targetPercentage(), breach.budgetConsumedPercentage()));
            }
        } else {
            sb.append("- Zero active SLO breaches across all services.\n");
        }

        sb.append("\n### RECENT CRITICAL INCIDENTS:\n");
        if (overview.recentCriticalIncidents() != null && !overview.recentCriticalIncidents().isEmpty()) {
            for (IncidentSummaryResponse inc : overview.recentCriticalIncidents()) {
                sb.append(String.format("- [%s] %s (Service: %s, Status: %s)\n",
                        inc.id(), inc.title(), inc.service(), inc.status()));
            }
        } else {
            sb.append("- No critical incidents in the current evaluation window.\n");
        }

        return sb.toString();
    }
}
