package com.roottrace.commandcenter;

import com.roottrace.commandcenter.dto.ReliabilityRecommendationResponse;
import com.roottrace.slo.dto.BurnRateResponse;
import com.roottrace.slo.dto.ErrorBudgetResponse;
import com.roottrace.slo.dto.SloEvaluationResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ReliabilityRecommendationService {

    public List<ReliabilityRecommendationResponse> generateServiceRecommendations(
            String serviceName,
            List<SloEvaluationResponse> sloEvaluations,
            List<ErrorBudgetResponse> errorBudgets,
            List<BurnRateResponse> burnRates,
            int criticalIncidents,
            double recurrenceRate,
            int overdueActionItems,
            int failedRunbooks) {

        List<ReliabilityRecommendationResponse> recommendations = new ArrayList<>();
        String svc = (serviceName != null && !serviceName.isBlank()) ? serviceName.trim() : "All Services";

        // 1. Check for active SLO breaches
        if (sloEvaluations != null) {
            for (SloEvaluationResponse eval : sloEvaluations) {
                if (eval.differencePercentage() < 0 || eval.errorBudgetRemainingPercentage() <= 0.0) {
                    recommendations.add(new ReliabilityRecommendationResponse(
                            "SLO_BREACH",
                            "CRITICAL",
                            "Active SLO Breach: " + eval.sloName(),
                            String.format("SLO '%s' for service '%s' is breached (Actual: %.3f%%, Target: %.3f%%). Freeze non-critical deployments and focus on reliability remediation.",
                                    eval.sloName(), svc, eval.actualPercentage(), eval.targetPercentage()),
                            svc
                    ));
                }
            }
        }

        // 2. Check for high Error Budget consumption
        if (errorBudgets != null) {
            for (ErrorBudgetResponse budget : errorBudgets) {
                if (budget.budgetConsumedPercentage() >= 80.0 && budget.budgetRemainingPercentage() > 0.0) {
                    recommendations.add(new ReliabilityRecommendationResponse(
                            "ERROR_BUDGET_WARNING",
                            "HIGH",
                            "Error Budget Depletion Warning: " + budget.sloName(),
                            String.format("Service '%s' has consumed %.1f%% of its error budget for '%s'. Prioritize stability and throttle risky release pipelines.",
                                    svc, budget.budgetConsumedPercentage(), budget.sloName()),
                            svc
                    ));
                }
            }
        }

        // 3. Check for Critical Burn Rates
        if (burnRates != null) {
            for (BurnRateResponse burn : burnRates) {
                if ("CRITICAL".equalsIgnoreCase(burn.severity()) || burn.burnRate() >= 5.0) {
                    recommendations.add(new ReliabilityRecommendationResponse(
                            "BURN_RATE_CRITICAL",
                            "CRITICAL",
                            "Critical Error Budget Burn Rate: " + burn.sloName(),
                            String.format("Active burn rate is %.2fx normal error rate over %d minutes. Immediate operational triage required to prevent total error budget exhaustion.",
                                    burn.burnRate(), burn.windowMinutes()),
                            svc
                    ));
                } else if ("HIGH".equalsIgnoreCase(burn.severity()) || burn.burnRate() >= 2.0) {
                    recommendations.add(new ReliabilityRecommendationResponse(
                            "BURN_RATE_ELEVATED",
                            "HIGH",
                            "Elevated Error Budget Burn Rate: " + burn.sloName(),
                            String.format("Active burn rate is %.2fx normal error rate over %d minutes. Investigate downstream service dependencies and traffic spikes.",
                                    burn.burnRate(), burn.windowMinutes()),
                            svc
                    ));
                }
            }
        }

        // 4. Overdue Postmortem Action Items
        if (overdueActionItems > 0) {
            recommendations.add(new ReliabilityRecommendationResponse(
                    "OVERDUE_ACTIONS",
                    "HIGH",
                    "Overdue Postmortem Action Items",
                    String.format("Service '%s' has %d overdue postmortem action item(s). Execute remediation tickets to prevent repeat incidents.",
                            svc, overdueActionItems),
                    svc
            ));
        }

        // 5. High Incident Recurrence
        if (recurrenceRate >= 0.3 && criticalIncidents > 0) {
            recommendations.add(new ReliabilityRecommendationResponse(
                    "INCIDENT_RECURRENCE",
                    "HIGH",
                    "High Incident Recurrence Rate",
                    String.format("Incident recurrence rate is %.0f%% with %d critical incident(s) in the evaluation window. Perform deeper architectural review and harden fault isolation.",
                            recurrenceRate * 100.0, criticalIncidents),
                    svc
                    ));
        }

        // 6. Failed Automated Runbooks
        if (failedRunbooks > 0) {
            recommendations.add(new ReliabilityRecommendationResponse(
                    "RUNBOOK_FAILURES",
                    "MEDIUM",
                    "Failed Automated Remediation Runbooks",
                    String.format("%d automated runbook execution(s) failed. Verify script arguments, credentials, and Kubernetes cluster permissions.",
                            failedRunbooks),
                    svc
            ));
        }

        if (recommendations.isEmpty()) {
            recommendations.add(new ReliabilityRecommendationResponse(
                    "MAINTAIN_HEALTH",
                    "LOW",
                    "Healthy Reliability Posture",
                    String.format("Service '%s' is operating within healthy SLO thresholds and error budget allocations. Continue routine monitoring.", svc),
                    svc
            ));
        }

        return recommendations;
    }
}
