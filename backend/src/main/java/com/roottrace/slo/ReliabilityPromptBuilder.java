package com.roottrace.slo;

import com.roottrace.slo.dto.ReliabilityDashboardResponse;
import com.roottrace.slo.dto.ReliabilityRiskResponse;
import com.roottrace.slo.dto.SloEvaluationResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ReliabilityPromptBuilder {

    public String buildPrompt(
            String serviceName,
            ReliabilityDashboardResponse dashboard,
            ReliabilityRiskResponse risk,
            List<String> recentIncidentSummaries,
            List<String> unresolvedActionItemSummaries) {

        StringBuilder sb = new StringBuilder();

        sb.append("""
                You are RootTraceAI's Principal SRE Reliability Governance Advisor.
                Your task is to analyze the operational reliability posture and SLO health for service: `%s`.

                ### CRITICAL RELIABILITY DIRECTIVES:
                1. Ground your recommendations strictly in the provided deterministic facts.
                2. Do NOT calculate or hallucinate metrics, risk scores, error budgets, or percentages.
                3. Do NOT invent incidents, root causes, or infrastructure components not present in the facts.
                4. Maintain a constructive, blameless SRE perspective adhering to Google SRE principles.
                5. Provide concrete, prioritized engineering recommendations (e.g., rate limiting, circuit breaking, scaling, alerting thresholds).

                ### SERVICE RELIABILITY METRICS (DETERMINISTIC FACTS):
                - Service: %s
                - Overall Reliability Risk Score: %.1f / 100 (%s)
                - Active SLO Breaches: %d
                - Average Error Budget Consumed: %.1f%%
                - Highest Burn Rate: %.2fx
                - Recent Incidents (30d): %d
                - Incident Recurrence Rate: %.0f%%
                - Unresolved Postmortem Action Items: %d

                ### ACTIVE SERVICE LEVEL OBJECTIVES (SLOs):
                """.formatted(
                serviceName,
                serviceName,
                risk.riskScore(),
                risk.riskTier(),
                dashboard.activeBreaches(),
                dashboard.averageBudgetConsumedPercentage(),
                dashboard.highestBurnRate(),
                dashboard.recentIncidentCount(),
                dashboard.recurrenceRate() * 100.0,
                dashboard.unresolvedActionItems()
        ));

        if (dashboard.slos() != null && !dashboard.slos().isEmpty()) {
            for (SloEvaluationResponse slo : dashboard.slos()) {
                sb.append(String.format("- SLO '%s': Target=%.3f%%, Actual=%.3f%%, Status=%s, Budget Remaining=%.1f%%\n",
                        slo.sloName(), slo.targetPercentage(), slo.actualPercentage(), slo.status(), slo.errorBudgetRemainingPercentage()));
            }
        } else {
            sb.append("- No active SLOs configured for this service.\n");
        }

        sb.append("\n### RECENT IDENTIFIED RISK FACTORS:\n");
        if (risk.riskFactors() != null) {
            for (String factor : risk.riskFactors()) {
                sb.append("- ").append(factor).append("\n");
            }
        }

        sb.append("\n### RECENT INCIDENTS (LAST 30 DAYS):\n");
        if (recentIncidentSummaries != null && !recentIncidentSummaries.isEmpty()) {
            for (String inc : recentIncidentSummaries) {
                sb.append("- ").append(inc).append("\n");
            }
        } else {
            sb.append("- No recent incidents recorded.\n");
        }

        sb.append("\n### UNRESOLVED POSTMORTEM ACTION ITEMS:\n");
        if (unresolvedActionItemSummaries != null && !unresolvedActionItemSummaries.isEmpty()) {
            for (String act : unresolvedActionItemSummaries) {
                sb.append("- ").append(act).append("\n");
            }
        } else {
            sb.append("- No open action items.\n");
        }

        return sb.toString();
    }
}
