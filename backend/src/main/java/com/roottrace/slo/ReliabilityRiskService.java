package com.roottrace.slo;

import com.roottrace.incident.Incident;
import com.roottrace.incident.IncidentRepository;
import com.roottrace.incident.IncidentSeverity;
import com.roottrace.incident.IncidentStatus;
import com.roottrace.postmortem.ActionItemStatus;
import com.roottrace.postmortem.PostmortemActionItem;
import com.roottrace.postmortem.PostmortemActionItemRepository;
import com.roottrace.slo.dto.BurnRateResponse;
import com.roottrace.slo.dto.ErrorBudgetResponse;
import com.roottrace.slo.dto.ReliabilityRiskResponse;
import com.roottrace.slo.dto.SloEvaluationResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
public class ReliabilityRiskService {

    private final SloRepository sloRepository;
    private final SloEvaluationService sloEvaluationService;
    private final ErrorBudgetService errorBudgetService;
    private final BurnRateService burnRateService;
    private final IncidentRepository incidentRepository;
    private final PostmortemActionItemRepository actionItemRepository;

    public ReliabilityRiskService(
            SloRepository sloRepository,
            SloEvaluationService sloEvaluationService,
            ErrorBudgetService errorBudgetService,
            BurnRateService burnRateService,
            IncidentRepository incidentRepository,
            PostmortemActionItemRepository actionItemRepository) {
        this.sloRepository = sloRepository;
        this.sloEvaluationService = sloEvaluationService;
        this.errorBudgetService = errorBudgetService;
        this.burnRateService = burnRateService;
        this.incidentRepository = incidentRepository;
        this.actionItemRepository = actionItemRepository;
    }

    @Transactional(readOnly = true)
    public ReliabilityRiskResponse evaluateReliabilityRisk(String serviceName) {
        String svc = (serviceName != null && !serviceName.isBlank()) ? serviceName.trim() : "default";

        List<Slo> slos = sloRepository.findByServiceNameAndEnabledTrue(svc);

        int activeBreaches = 0;
        double totalBudgetConsumed = 0;
        int criticalBurnRates = 0;

        for (Slo slo : slos) {
            SloEvaluationResponse eval = sloEvaluationService.evaluateSlo(slo);
            if (eval.status() == SloStatus.BREACHED) {
                activeBreaches++;
            }
            ErrorBudgetResponse budget = errorBudgetService.calculateErrorBudget(slo);
            totalBudgetConsumed += budget.budgetConsumedPercentage();

            BurnRateResponse burn = burnRateService.calculateBurnRate(slo, 60);
            if ("CRITICAL".equals(burn.severity())) {
                criticalBurnRates++;
            }
        }

        double avgBudgetConsumed = !slos.isEmpty() ? (totalBudgetConsumed / slos.size()) : 0.0;

        // Incident metrics in last 30 days
        Instant thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);
        List<Incident> recentIncidents = incidentRepository.findAllNotDeleted(Pageable.unpaged()).getContent()
                .stream()
                .filter(i -> i.getService() != null && i.getService().equalsIgnoreCase(svc)
                        && i.getCreatedAt() != null && i.getCreatedAt().isAfter(thirtyDaysAgo))
                .toList();

        int criticalIncidents = (int) recentIncidents.stream()
                .filter(i -> i.getSeverity() == IncidentSeverity.CRITICAL)
                .count();

        // Recurrence rate (heuristic: matching titles or same service repeated)
        long totalHistorical = recentIncidents.size();
        long uniqueTitles = recentIncidents.stream().map(Incident::getTitle).distinct().count();
        double recurrenceRate = (totalHistorical > 1) ? Math.max(0.0, (totalHistorical - uniqueTitles) / (double) totalHistorical) : 0.0;

        // Unresolved action items
        List<PostmortemActionItem> allActionItems = actionItemRepository.findAll();
        int unresolvedActionItems = (int) allActionItems.stream()
                .filter(a -> a.getStatus() == ActionItemStatus.OPEN || a.getStatus() == ActionItemStatus.IN_PROGRESS)
                .filter(a -> a.getPostmortem() != null && a.getPostmortem().getIncident() != null
                        && svc.equalsIgnoreCase(a.getPostmortem().getIncident().getService()))
                .count();

        // Calculate deterministic risk score (0-100)
        double score = 0.0;
        List<String> riskFactors = new ArrayList<>();

        if (activeBreaches > 0) {
            double breachPts = Math.min(50.0, activeBreaches * 25.0);
            score += breachPts;
            riskFactors.add(String.format("%d active SLO breach(es) (+%.1f)", activeBreaches, breachPts));
        }

        if (avgBudgetConsumed >= 90.0) {
            score += 25.0;
            riskFactors.add(String.format("Critical error budget consumption (%.1f%%) (+25.0)", avgBudgetConsumed));
        } else if (avgBudgetConsumed >= 75.0) {
            score += 20.0;
            riskFactors.add(String.format("High error budget consumption (%.1f%%) (+20.0)", avgBudgetConsumed));
        } else if (avgBudgetConsumed >= 50.0) {
            score += 10.0;
            riskFactors.add(String.format("Elevated error budget consumption (%.1f%%) (+10.0)", avgBudgetConsumed));
        }

        if (criticalBurnRates > 0) {
            score += 15.0;
            riskFactors.add(String.format("%d critical burn rate alert(s) detected (+15.0)", criticalBurnRates));
        }

        if (criticalIncidents > 0) {
            double critPts = Math.min(20.0, criticalIncidents * 5.0);
            score += critPts;
            riskFactors.add(String.format("%d critical incident(s) in last 30 days (+%.1f)", criticalIncidents, critPts));
        }

        if (recurrenceRate > 0.1) {
            double recPts = Math.min(15.0, recurrenceRate * 25.0);
            score += recPts;
            riskFactors.add(String.format("%.0f%% incident recurrence rate (+%.1f)", recurrenceRate * 100.0, recPts));
        }

        if (unresolvedActionItems > 0) {
            double actPts = Math.min(15.0, unresolvedActionItems * 3.0);
            score += actPts;
            riskFactors.add(String.format("%d unresolved postmortem action item(s) (+%.1f)", unresolvedActionItems, actPts));
        }

        double clampedScore = Math.min(100.0, Math.max(0.0, score));
        double roundedScore = Math.round(clampedScore * 10.0) / 10.0;

        String riskTier;
        if (roundedScore >= 80.0) {
            riskTier = "CRITICAL";
        } else if (roundedScore >= 60.0) {
            riskTier = "HIGH";
        } else if (roundedScore >= 30.0) {
            riskTier = "MEDIUM";
        } else {
            riskTier = "LOW";
        }

        if (riskFactors.isEmpty()) {
            riskFactors.add("All SLOs healthy and error budgets within normal limits.");
        }

        return new ReliabilityRiskResponse(
                svc,
                roundedScore,
                riskTier,
                activeBreaches,
                Math.round(avgBudgetConsumed * 10.0) / 10.0,
                criticalBurnRates,
                criticalIncidents,
                Math.round(recurrenceRate * 100.0) / 100.0,
                unresolvedActionItems,
                riskFactors,
                Instant.now()
        );
    }
}
