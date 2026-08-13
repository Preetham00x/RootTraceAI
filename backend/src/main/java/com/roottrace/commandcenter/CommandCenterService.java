package com.roottrace.commandcenter;

import com.roottrace.commandcenter.dto.CommandCenterOverviewResponse;
import com.roottrace.commandcenter.dto.ReliabilityScoreResponse;
import com.roottrace.commandcenter.dto.ServiceHealthSummaryResponse;
import com.roottrace.common.exception.BadRequestException;
import com.roottrace.incident.Incident;
import com.roottrace.incident.IncidentRepository;
import com.roottrace.incident.IncidentSeverity;
import com.roottrace.incident.IncidentStatus;
import com.roottrace.incident.dto.CreatorResponse;
import com.roottrace.incident.dto.IncidentSummaryResponse;
import com.roottrace.integration.RunbookExecution;
import com.roottrace.integration.RunbookExecutionRepository;
import com.roottrace.integration.RunbookExecutionStatus;
import com.roottrace.intelligence.SreMetricsService;
import com.roottrace.intelligence.dto.SreMetricsSummaryResponse;
import com.roottrace.postmortem.ActionItemStatus;
import com.roottrace.postmortem.PostmortemActionItem;
import com.roottrace.postmortem.PostmortemActionItemRepository;
import com.roottrace.slo.ErrorBudgetService;
import com.roottrace.slo.ReliabilityTrendService;
import com.roottrace.slo.Slo;
import com.roottrace.slo.SloEvaluationService;
import com.roottrace.slo.SloRepository;
import com.roottrace.slo.SloStatus;
import com.roottrace.slo.dto.ErrorBudgetResponse;
import com.roottrace.slo.dto.ReliabilityTrendResponse;
import com.roottrace.slo.dto.SloEvaluationResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CommandCenterService {

    private final IncidentRepository incidentRepository;
    private final SloRepository sloRepository;
    private final SloEvaluationService sloEvaluationService;
    private final ErrorBudgetService errorBudgetService;
    private final ReliabilityTrendService reliabilityTrendService;
    private final SreMetricsService sreMetricsService;
    private final ServiceHealthService serviceHealthService;
    private final ReliabilityScoreService reliabilityScoreService;
    private final PostmortemActionItemRepository actionItemRepository;
    private final RunbookExecutionRepository runbookExecutionRepository;

    public CommandCenterService(
            IncidentRepository incidentRepository,
            SloRepository sloRepository,
            SloEvaluationService sloEvaluationService,
            ErrorBudgetService errorBudgetService,
            ReliabilityTrendService reliabilityTrendService,
            SreMetricsService sreMetricsService,
            ServiceHealthService serviceHealthService,
            ReliabilityScoreService reliabilityScoreService,
            PostmortemActionItemRepository actionItemRepository,
            RunbookExecutionRepository runbookExecutionRepository) {
        this.incidentRepository = incidentRepository;
        this.sloRepository = sloRepository;
        this.sloEvaluationService = sloEvaluationService;
        this.errorBudgetService = errorBudgetService;
        this.reliabilityTrendService = reliabilityTrendService;
        this.sreMetricsService = sreMetricsService;
        this.serviceHealthService = serviceHealthService;
        this.reliabilityScoreService = reliabilityScoreService;
        this.actionItemRepository = actionItemRepository;
        this.runbookExecutionRepository = runbookExecutionRepository;
    }

    @Transactional(readOnly = true)
    public CommandCenterOverviewResponse getOverview(Integer days) {
        int windowDays = (days != null) ? days : 30;
        if (windowDays < 1 || windowDays > 365) {
            throw new BadRequestException("days parameter must be between 1 and 365. Provided: " + windowDays);
        }

        Instant cutoff = Instant.now().minus(Duration.ofDays(windowDays));

        // 1. Incidents in window
        List<Incident> allIncidents = incidentRepository.findAllNotDeleted(Pageable.unpaged()).getContent()
                .stream()
                .filter(i -> i.getCreatedAt() != null && i.getCreatedAt().isAfter(cutoff))
                .collect(Collectors.toList());

        int totalIncidents = allIncidents.size();
        int activeIncidents = (int) allIncidents.stream()
                .filter(i -> i.getStatus() == IncidentStatus.OPEN || i.getStatus() == IncidentStatus.INVESTIGATING)
                .count();
        int resolvedIncidents = totalIncidents - activeIncidents;

        int criticalIncidents = (int) allIncidents.stream()
                .filter(i -> i.getSeverity() == IncidentSeverity.CRITICAL)
                .count();
        int highIncidents = (int) allIncidents.stream()
                .filter(i -> i.getSeverity() == IncidentSeverity.HIGH)
                .count();

        // MTTR & MTTD
        SreMetricsSummaryResponse metrics = sreMetricsService.getSreMetrics(windowDays);
        Double meanMttr = (metrics != null) ? metrics.meanTimeToResolveMinutes() : 0.0;
        Double meanMttd = (metrics != null) ? metrics.meanTimeToDetectMinutes() : 0.0;

        long uniqueTitles = allIncidents.stream().map(Incident::getTitle).distinct().count();
        double recurrenceRate = (totalIncidents > 1) ? Math.max(0.0, (totalIncidents - uniqueTitles) / (double) totalIncidents) : 0.0;

        // 2. SLO & Error Budget Aggregations
        List<Slo> slos = sloRepository.findAll().stream()
                .filter(Slo::getEnabled)
                .toList();

        int sloCount = slos.size();
        int healthySlos = 0;
        int warningSlos = 0;
        int breachedSlos = 0;
        double totalConsumed = 0.0;
        List<SloEvaluationResponse> sloBreaches = new ArrayList<>();

        for (Slo slo : slos) {
            SloEvaluationResponse eval = sloEvaluationService.evaluateSlo(slo);
            if (eval.status() == SloStatus.BREACHED) {
                breachedSlos++;
                sloBreaches.add(eval);
            } else if (eval.status() == SloStatus.WARNING) {
                warningSlos++;
            } else {
                healthySlos++;
            }

            ErrorBudgetResponse budget = errorBudgetService.calculateErrorBudget(slo);
            totalConsumed += budget.budgetConsumedPercentage();
        }

        double avgBudgetConsumed = (sloCount > 0) ? (totalConsumed / sloCount) : 0.0;

        // 3. Postmortem Action Items
        List<PostmortemActionItem> allActionItems = actionItemRepository.findAll().stream()
                .filter(a -> a.getStatus() == ActionItemStatus.OPEN || a.getStatus() == ActionItemStatus.IN_PROGRESS)
                .toList();

        int openActionItems = allActionItems.size();
        int overdueActionItems = (int) allActionItems.stream()
                .filter(a -> a.getDueDate() != null && a.getDueDate().isBefore(Instant.now()))
                .count();

        // 4. Failed Runbook Executions
        int failedRunbooks = (int) runbookExecutionRepository.findAll().stream()
                .filter(r -> r.getCreatedAt() != null && r.getCreatedAt().isAfter(cutoff)
                        && r.getExecutionStatus() == RunbookExecutionStatus.FAILED)
                .count();

        // 5. Service Health Summaries & Top Risky Services
        List<ServiceHealthSummaryResponse> serviceSummaries = serviceHealthService.getServiceHealthSummaries(windowDays, 100, "risk");
        int totalServices = serviceSummaries.size();
        int servicesAtRisk = (int) serviceSummaries.stream()
                .filter(s -> "HIGH".equalsIgnoreCase(s.riskTier()) || "CRITICAL".equalsIgnoreCase(s.riskTier()))
                .count();

        List<ServiceHealthSummaryResponse> topRiskyServices = serviceSummaries.stream()
                .limit(5)
                .collect(Collectors.toList());

        // 6. Overall Reliability Score & Penalties
        ReliabilityScoreResponse reliabilityScore = reliabilityScoreService.calculateReliabilityScore(
                breachedSlos,
                warningSlos,
                avgBudgetConsumed,
                criticalIncidents,
                highIncidents,
                recurrenceRate,
                overdueActionItems,
                failedRunbooks
        );

        // 7. Recent Critical Incidents
        List<IncidentSummaryResponse> recentCriticalIncidents = allIncidents.stream()
                .filter(i -> i.getSeverity() == IncidentSeverity.CRITICAL)
                .sorted(Comparator.comparing(Incident::getCreatedAt).reversed())
                .limit(5)
                .map(this::mapIncidentSummary)
                .collect(Collectors.toList());

        // 8. Organization Reliability Trend
        ReliabilityTrendResponse trendResp = reliabilityTrendService.getReliabilityTrends("default", windowDays, "daily");
        List<ReliabilityTrendResponse.ReliabilityDataPoint> reliabilityTrend = (trendResp != null && trendResp.dataPoints() != null)
                ? trendResp.dataPoints() : List.of();

        return new CommandCenterOverviewResponse(
                windowDays,
                reliabilityScore.score(),
                reliabilityScore.riskTier(),
                totalServices,
                servicesAtRisk,
                totalIncidents,
                activeIncidents,
                resolvedIncidents,
                criticalIncidents,
                highIncidents,
                meanMttr,
                meanMttd,
                sloCount,
                healthySlos,
                warningSlos,
                breachedSlos,
                Math.round(avgBudgetConsumed * 10.0) / 10.0,
                openActionItems,
                overdueActionItems,
                failedRunbooks,
                topRiskyServices,
                recentCriticalIncidents,
                sloBreaches,
                reliabilityTrend
        );
    }

    private IncidentSummaryResponse mapIncidentSummary(Incident inc) {
        CreatorResponse creator = (inc.getCreatedBy() != null)
                ? new CreatorResponse(inc.getCreatedBy().getId(), (inc.getCreatedBy().getFirstName() != null ? inc.getCreatedBy().getFirstName() + " " + (inc.getCreatedBy().getLastName() != null ? inc.getCreatedBy().getLastName() : "") : inc.getCreatedBy().getEmail()).trim())
                : null;
        return new IncidentSummaryResponse(
                inc.getId(),
                inc.getTitle(),
                inc.getService(),
                inc.getSeverity(),
                inc.getStatus(),
                inc.getEnvironment(),
                creator,
                inc.getCreatedAt()
        );
    }
}
