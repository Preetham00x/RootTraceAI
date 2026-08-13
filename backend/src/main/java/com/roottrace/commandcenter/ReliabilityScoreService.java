package com.roottrace.commandcenter;

import com.roottrace.commandcenter.dto.ReliabilityPenaltyResponse;
import com.roottrace.commandcenter.dto.ReliabilityScoreResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ReliabilityScoreService {

    public ReliabilityScoreResponse calculateReliabilityScore(
            int breachedSlos,
            int warningSlos,
            double avgBudgetConsumedPercent,
            int criticalIncidents,
            int highIncidents,
            double recurrenceRate,
            int overdueActionItems,
            int failedRunbookExecutions) {

        double baseScore = 100.0;
        List<ReliabilityPenaltyResponse> penalties = new ArrayList<>();

        // 1. Active SLO Breaches Penalty (up to 25 pts)
        if (breachedSlos > 0) {
            double penalty = Math.min(25.0, breachedSlos * 5.0);
            penalties.add(new ReliabilityPenaltyResponse(
                    "SLO_BREACHES",
                    penalty,
                    String.format("%d active SLO breach(es) detected (-%.1f pts)", breachedSlos, penalty)
            ));
        }

        // 2. SLO Warning Status Penalty (up to 10 pts)
        if (warningSlos > 0) {
            double penalty = Math.min(10.0, warningSlos * 1.5);
            penalties.add(new ReliabilityPenaltyResponse(
                    "SLO_WARNINGS",
                    penalty,
                    String.format("%d SLO(s) approaching error budget exhaustion (-%.1f pts)", warningSlos, penalty)
            ));
        }

        // 3. Error Budget Consumption Penalty (up to 15 pts)
        if (avgBudgetConsumedPercent >= 75.0) {
            double penalty = 15.0;
            penalties.add(new ReliabilityPenaltyResponse(
                    "ERROR_BUDGET",
                    penalty,
                    String.format("High average error budget consumption across services (%.1f%%) (-%.1f pts)", avgBudgetConsumedPercent, penalty)
            ));
        } else if (avgBudgetConsumedPercent >= 50.0) {
            double penalty = 8.0;
            penalties.add(new ReliabilityPenaltyResponse(
                    "ERROR_BUDGET",
                    penalty,
                    String.format("Moderate average error budget consumption across services (%.1f%%) (-%.1f pts)", avgBudgetConsumedPercent, penalty)
            ));
        }

        // 4. Critical & High Incidents Penalty (up to 30 pts)
        double incidentPenalty = 0.0;
        if (criticalIncidents > 0) {
            incidentPenalty += Math.min(18.0, criticalIncidents * 6.0);
        }
        if (highIncidents > 0) {
            incidentPenalty += Math.min(12.0, highIncidents * 3.0);
        }
        if (incidentPenalty > 0) {
            penalties.add(new ReliabilityPenaltyResponse(
                    "INCIDENTS",
                    incidentPenalty,
                    String.format("%d critical and %d high severity incident(s) in window (-%.1f pts)", criticalIncidents, highIncidents, incidentPenalty)
            ));
        }

        // 5. Recurrence Rate Penalty (up to 10 pts)
        if (recurrenceRate > 0.1) {
            double penalty = Math.min(10.0, Math.round(recurrenceRate * 15.0 * 10.0) / 10.0);
            penalties.add(new ReliabilityPenaltyResponse(
                    "RECURRENCE",
                    penalty,
                    String.format("%.0f%% incident recurrence rate (-%.1f pts)", recurrenceRate * 100.0, penalty)
            ));
        }

        // 6. Overdue Postmortem Action Items (up to 15 pts)
        if (overdueActionItems > 0) {
            double penalty = Math.min(15.0, overdueActionItems * 2.0);
            penalties.add(new ReliabilityPenaltyResponse(
                    "OVERDUE_ACTION_ITEMS",
                    penalty,
                    String.format("%d overdue postmortem action item(s) (-%.1f pts)", overdueActionItems, penalty)
            ));
        }

        // 7. Failed Runbook Executions (up to 10 pts)
        if (failedRunbookExecutions > 0) {
            double penalty = Math.min(10.0, failedRunbookExecutions * 2.5);
            penalties.add(new ReliabilityPenaltyResponse(
                    "FAILED_RUNBOOKS",
                    penalty,
                    String.format("%d failed automated remediation runbook(s) (-%.1f pts)", failedRunbookExecutions, penalty)
            ));
        }

        double totalPenalties = penalties.stream().mapToDouble(ReliabilityPenaltyResponse::points).sum();
        double calculatedScore = Math.max(0.0, Math.min(100.0, baseScore - totalPenalties));
        double roundedScore = Math.round(calculatedScore * 10.0) / 10.0;

        String riskTier;
        if (roundedScore >= 85.0) {
            riskTier = "LOW";
        } else if (roundedScore >= 70.0) {
            riskTier = "MEDIUM";
        } else if (roundedScore >= 50.0) {
            riskTier = "HIGH";
        } else {
            riskTier = "CRITICAL";
        }

        if (penalties.isEmpty()) {
            penalties.add(new ReliabilityPenaltyResponse(
                    "NONE",
                    0.0,
                    "All reliability dimensions healthy. No penalties assessed."
            ));
        }

        return new ReliabilityScoreResponse(
                roundedScore,
                baseScore,
                riskTier,
                penalties
        );
    }
}
