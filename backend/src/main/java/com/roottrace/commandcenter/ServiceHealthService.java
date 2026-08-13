package com.roottrace.commandcenter;

import com.roottrace.commandcenter.dto.ReliabilityRecommendationResponse;
import com.roottrace.commandcenter.dto.ServiceHealthDetailResponse;
import com.roottrace.commandcenter.dto.ServiceHealthSummaryResponse;
import com.roottrace.common.exception.BadRequestException;
import com.roottrace.common.exception.ResourceNotFoundException;
import com.roottrace.incident.Incident;
import com.roottrace.incident.IncidentRepository;
import com.roottrace.incident.IncidentSeverity;
import com.roottrace.incident.IncidentStatus;
import com.roottrace.integration.RunbookExecution;
import com.roottrace.integration.RunbookExecutionRepository;
import com.roottrace.integration.RunbookExecutionStatus;
import com.roottrace.integration.dto.RunbookExecutionResponse;
import com.roottrace.intelligence.SreMetricsService;
import com.roottrace.intelligence.ServiceRiskService;
import com.roottrace.intelligence.dto.IncidentTrendsResponse;
import com.roottrace.intelligence.dto.ServiceRiskResponse;
import com.roottrace.postmortem.ActionItemStatus;
import com.roottrace.postmortem.Postmortem;
import com.roottrace.postmortem.PostmortemActionItem;
import com.roottrace.postmortem.PostmortemActionItemRepository;
import com.roottrace.postmortem.PostmortemRepository;
import com.roottrace.postmortem.dto.PostmortemActionItemResponse;
import com.roottrace.slo.BurnRateService;
import com.roottrace.slo.ErrorBudgetService;
import com.roottrace.slo.ReliabilityRiskService;
import com.roottrace.slo.ReliabilityTrendService;
import com.roottrace.slo.Slo;
import com.roottrace.slo.SloEvaluationService;
import com.roottrace.slo.SloRepository;
import com.roottrace.slo.SloStatus;
import com.roottrace.slo.dto.BurnRateResponse;
import com.roottrace.slo.dto.ErrorBudgetResponse;
import com.roottrace.slo.dto.ReliabilityRiskResponse;
import com.roottrace.slo.dto.ReliabilityTrendResponse;
import com.roottrace.slo.dto.SloEvaluationResponse;
import com.roottrace.user.dto.UserDto;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Service
public class ServiceHealthService {

    private final IncidentRepository incidentRepository;
    private final SloRepository sloRepository;
    private final SloEvaluationService sloEvaluationService;
    private final ErrorBudgetService errorBudgetService;
    private final BurnRateService burnRateService;
    private final ReliabilityRiskService reliabilityRiskService;
    private final ReliabilityTrendService reliabilityTrendService;
    private final ServiceRiskService serviceRiskService;
    private final SreMetricsService sreMetricsService;
    private final PostmortemRepository postmortemRepository;
    private final PostmortemActionItemRepository actionItemRepository;
    private final RunbookExecutionRepository runbookExecutionRepository;
    private final ReliabilityRecommendationService recommendationService;

    public ServiceHealthService(
            IncidentRepository incidentRepository,
            SloRepository sloRepository,
            SloEvaluationService sloEvaluationService,
            ErrorBudgetService errorBudgetService,
            BurnRateService burnRateService,
            ReliabilityRiskService reliabilityRiskService,
            ReliabilityTrendService reliabilityTrendService,
            ServiceRiskService serviceRiskService,
            SreMetricsService sreMetricsService,
            PostmortemRepository postmortemRepository,
            PostmortemActionItemRepository actionItemRepository,
            RunbookExecutionRepository runbookExecutionRepository,
            ReliabilityRecommendationService recommendationService) {
        this.incidentRepository = incidentRepository;
        this.sloRepository = sloRepository;
        this.sloEvaluationService = sloEvaluationService;
        this.errorBudgetService = errorBudgetService;
        this.burnRateService = burnRateService;
        this.reliabilityRiskService = reliabilityRiskService;
        this.reliabilityTrendService = reliabilityTrendService;
        this.serviceRiskService = serviceRiskService;
        this.sreMetricsService = sreMetricsService;
        this.postmortemRepository = postmortemRepository;
        this.actionItemRepository = actionItemRepository;
        this.runbookExecutionRepository = runbookExecutionRepository;
        this.recommendationService = recommendationService;
    }

    @Transactional(readOnly = true)
    public List<ServiceHealthSummaryResponse> getServiceHealthSummaries(Integer days, Integer limit, String sort) {
        int windowDays = (days != null) ? days : 30;
        if (windowDays < 1 || windowDays > 365) {
            throw new BadRequestException("days must be between 1 and 365. Provided: " + windowDays);
        }

        int maxLimit = (limit != null) ? limit : 50;
        if (maxLimit < 1 || maxLimit > 100) {
            throw new BadRequestException("limit must be between 1 and 100. Provided: " + maxLimit);
        }

        String sortKey = (sort != null && !sort.isBlank()) ? sort.toLowerCase().trim() : "risk";
        if (!Set.of("risk", "incidents", "mttr", "slo", "name").contains(sortKey)) {
            throw new BadRequestException("Invalid sort parameter: " + sort + ". Allowed: risk, incidents, mttr, slo, name");
        }

        // Identify all distinct services from incidents and SLOs
        Set<String> allServices = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        incidentRepository.findAllNotDeleted(Pageable.unpaged()).getContent()
                .forEach(i -> {
                    if (i.getService() != null && !i.getService().isBlank()) {
                        allServices.add(i.getService().trim());
                    }
                });

        sloRepository.findAll()
                .forEach(s -> {
                    if (s.getServiceName() != null && !s.getServiceName().isBlank()) {
                        allServices.add(s.getServiceName().trim());
                    }
                });

        List<ServiceHealthSummaryResponse> summaries = new ArrayList<>();
        Instant cutoff = Instant.now().minus(Duration.ofDays(windowDays));

        for (String svc : allServices) {
            summaries.add(buildServiceSummary(svc, cutoff, windowDays));
        }

        // Apply sorting
        Comparator<ServiceHealthSummaryResponse> comparator = switch (sortKey) {
            case "incidents" -> Comparator.comparing(ServiceHealthSummaryResponse::incidentCount).reversed();
            case "mttr" -> Comparator.comparing((ServiceHealthSummaryResponse s) -> s.meanMttrMinutes() != null ? s.meanMttrMinutes() : 0.0).reversed();
            case "slo" -> Comparator.comparing(ServiceHealthSummaryResponse::breachedSloCount).reversed()
                    .thenComparing(ServiceHealthSummaryResponse::warningSloCount).reversed();
            case "name" -> Comparator.comparing(ServiceHealthSummaryResponse::serviceName, String.CASE_INSENSITIVE_ORDER);
            default -> Comparator.comparing(ServiceHealthSummaryResponse::healthScore); // lower health score = higher risk
        };

        return summaries.stream()
                .sorted(comparator)
                .limit(maxLimit)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ServiceHealthDetailResponse getServiceHealthDetail(String serviceName, Integer days) {
        if (serviceName == null || serviceName.isBlank()) {
            throw new BadRequestException("serviceName cannot be blank");
        }
        String svc = serviceName.trim();
        int windowDays = (days != null) ? days : 30;
        Instant cutoff = Instant.now().minus(Duration.ofDays(windowDays));

        List<Incident> allIncidents = incidentRepository.findAllNotDeleted(Pageable.unpaged()).getContent()
                .stream()
                .filter(i -> i.getService() != null && i.getService().equalsIgnoreCase(svc))
                .collect(Collectors.toList());

        List<Incident> recentIncidents = allIncidents.stream()
                .filter(i -> i.getCreatedAt() != null && i.getCreatedAt().isAfter(cutoff))
                .collect(Collectors.toList());

        int totalIncidents = recentIncidents.size();
        int activeIncidents = (int) recentIncidents.stream()
                .filter(i -> i.getStatus() == IncidentStatus.OPEN || i.getStatus() == IncidentStatus.INVESTIGATING)
                .count();
        int resolvedIncidents = totalIncidents - activeIncidents;
        int criticalIncidents = (int) recentIncidents.stream()
                .filter(i -> i.getSeverity() == IncidentSeverity.CRITICAL)
                .count();
        int highIncidents = (int) recentIncidents.stream()
                .filter(i -> i.getSeverity() == IncidentSeverity.HIGH)
                .count();

        // MTTR calculation
        List<Double> mttrList = recentIncidents.stream()
                .filter(i -> (i.getStatus() == IncidentStatus.RESOLVED || i.getStatus() == IncidentStatus.CLOSED)
                        && i.getResolvedAt() != null && i.getCreatedAt() != null)
                .map(i -> (double) Duration.between(i.getCreatedAt(), i.getResolvedAt()).toMinutes())
                .collect(Collectors.toList());

        Double meanMttr = mttrList.isEmpty() ? null :
                Math.round((mttrList.stream().mapToDouble(Double::doubleValue).average().orElse(0.0)) * 10.0) / 10.0;
        Double meanMttd = totalIncidents > 0 ? 5.0 : 0.0;

        long uniqueTitles = recentIncidents.stream().map(Incident::getTitle).distinct().count();
        double recurrenceRate = (totalIncidents > 1) ? Math.max(0.0, (totalIncidents - uniqueTitles) / (double) totalIncidents) : 0.0;

        ServiceHealthDetailResponse.ServiceIncidentSummary incSummary = new ServiceHealthDetailResponse.ServiceIncidentSummary(
                totalIncidents, activeIncidents, resolvedIncidents, criticalIncidents, highIncidents, meanMttr, meanMttd,
                Math.round(recurrenceRate * 100.0) / 100.0
        );

        // SLO & Error Budget evaluations
        List<Slo> slos = sloRepository.findByServiceNameAndEnabledTrue(svc);
        List<SloEvaluationResponse> sloEvals = new ArrayList<>();
        List<ErrorBudgetResponse> errorBudgets = new ArrayList<>();
        List<BurnRateResponse> burnRates = new ArrayList<>();

        int healthySlos = 0;
        int warningSlos = 0;
        int breachedSlos = 0;
        double totalConsumed = 0.0;
        double highestBurn = 0.0;
        String highestSeverity = "NORMAL";
        int criticalBurnCount = 0;

        for (Slo slo : slos) {
            SloEvaluationResponse eval = sloEvaluationService.evaluateSlo(slo);
            sloEvals.add(eval);
            if (eval.status() == SloStatus.BREACHED) breachedSlos++;
            else if (eval.status() == SloStatus.WARNING) warningSlos++;
            else healthySlos++;

            ErrorBudgetResponse budget = errorBudgetService.calculateErrorBudget(slo);
            errorBudgets.add(budget);
            totalConsumed += budget.budgetConsumedPercentage();

            BurnRateResponse burn = burnRateService.calculateBurnRate(slo, 60);
            burnRates.add(burn);
            if (burn.burnRate() > highestBurn) {
                highestBurn = burn.burnRate();
            }
            if ("CRITICAL".equalsIgnoreCase(burn.severity())) {
                highestSeverity = "CRITICAL";
                criticalBurnCount++;
            } else if ("HIGH".equalsIgnoreCase(burn.severity()) && !"CRITICAL".equalsIgnoreCase(highestSeverity)) {
                highestSeverity = "HIGH";
            }
        }

        double avgConsumed = !slos.isEmpty() ? (totalConsumed / slos.size()) : 0.0;
        double avgRemaining = Math.max(0.0, 100.0 - avgConsumed);
        String budgetStatus = (avgConsumed >= 100.0) ? "BREACHED" : (avgConsumed >= 75.0 ? "WARNING" : "HEALTHY");

        ServiceHealthDetailResponse.ServiceSloSummary sloSummary = new ServiceHealthDetailResponse.ServiceSloSummary(
                slos.size(), healthySlos, warningSlos, breachedSlos, sloEvals
        );

        ServiceHealthDetailResponse.ServiceErrorBudgetSummary budgetSummary = new ServiceHealthDetailResponse.ServiceErrorBudgetSummary(
                Math.round(avgConsumed * 10.0) / 10.0,
                Math.round(avgRemaining * 10.0) / 10.0,
                budgetStatus
        );

        ServiceHealthDetailResponse.ServiceBurnRateSummary burnSummary = new ServiceHealthDetailResponse.ServiceBurnRateSummary(
                Math.round(highestBurn * 100.0) / 100.0,
                highestSeverity,
                criticalBurnCount
        );

        // Trends
        IncidentTrendsResponse incTrendResp = sreMetricsService.getIncidentTrends(windowDays, "daily");
        List<IncidentTrendsResponse.TrendDataPoint> incidentTrend = (incTrendResp != null && incTrendResp.dataPoints() != null)
                ? incTrendResp.dataPoints() : List.of();

        ReliabilityTrendResponse relTrendResp = reliabilityTrendService.getReliabilityTrends(svc, windowDays, "daily");
        List<ReliabilityTrendResponse.ReliabilityDataPoint> reliabilityTrend = (relTrendResp != null && relTrendResp.dataPoints() != null)
                ? relTrendResp.dataPoints() : List.of();

        // Top root causes from postmortems
        List<Postmortem> postmortems = postmortemRepository.findAll().stream()
                .filter(p -> p.getIncident() != null && svc.equalsIgnoreCase(p.getIncident().getService()))
                .toList();

        List<String> topRootCauses = postmortems.stream()
                .map(Postmortem::getRootCauseAnalysis)
                .filter(rc -> rc != null && !rc.isBlank())
                .distinct()
                .limit(5)
                .collect(Collectors.toList());

        // Open action items
        List<PostmortemActionItem> allActionItems = actionItemRepository.findAll().stream()
                .filter(a -> a.getPostmortem() != null && a.getPostmortem().getIncident() != null
                        && svc.equalsIgnoreCase(a.getPostmortem().getIncident().getService()))
                .filter(a -> a.getStatus() == ActionItemStatus.OPEN || a.getStatus() == ActionItemStatus.IN_PROGRESS)
                .toList();

        List<PostmortemActionItemResponse> openActionItems = allActionItems.stream()
                .map(this::mapActionItem)
                .collect(Collectors.toList());

        int overdueCount = (int) allActionItems.stream()
                .filter(a -> a.getDueDate() != null && a.getDueDate().isBefore(Instant.now()))
                .count();

        // Runbook executions
        List<RunbookExecution> runbooks = runbookExecutionRepository.findAll().stream()
                .filter(r -> r.getIncident() != null && svc.equalsIgnoreCase(r.getIncident().getService()))
                .sorted(Comparator.comparing(RunbookExecution::getCreatedAt).reversed())
                .limit(10)
                .toList();

        List<RunbookExecutionResponse> recentRunbooks = runbooks.stream()
                .map(this::mapRunbook)
                .collect(Collectors.toList());

        int failedRunbooks = (int) runbooks.stream()
                .filter(r -> r.getExecutionStatus() == RunbookExecutionStatus.FAILED)
                .count();

        // Service Risk & Recommendations
        ReliabilityRiskResponse risk = reliabilityRiskService.evaluateReliabilityRisk(svc);
        double healthScore = Math.max(0.0, Math.min(100.0, 100.0 - risk.riskScore()));
        double roundedHealth = Math.round(healthScore * 10.0) / 10.0;

        List<ReliabilityRecommendationResponse> recommendations = recommendationService.generateServiceRecommendations(
                svc, sloEvals, errorBudgets, burnRates, criticalIncidents, recurrenceRate, overdueCount, failedRunbooks
        );

        return new ServiceHealthDetailResponse(
                svc,
                roundedHealth,
                risk.riskTier(),
                incSummary,
                sloSummary,
                budgetSummary,
                burnSummary,
                incidentTrend,
                reliabilityTrend,
                topRootCauses,
                openActionItems,
                recentRunbooks,
                risk.riskFactors(),
                recommendations
        );
    }

    private ServiceHealthSummaryResponse buildServiceSummary(String svc, Instant cutoff, int windowDays) {
        List<Incident> incidents = incidentRepository.findAllNotDeleted(Pageable.unpaged()).getContent()
                .stream()
                .filter(i -> i.getService() != null && i.getService().equalsIgnoreCase(svc)
                        && i.getCreatedAt() != null && i.getCreatedAt().isAfter(cutoff))
                .toList();

        int totalIncidents = incidents.size();
        int activeIncidents = (int) incidents.stream()
                .filter(i -> i.getStatus() == IncidentStatus.OPEN || i.getStatus() == IncidentStatus.INVESTIGATING)
                .count();
        int criticalIncidents = (int) incidents.stream()
                .filter(i -> i.getSeverity() == IncidentSeverity.CRITICAL)
                .count();

        List<Double> mttrList = incidents.stream()
                .filter(i -> (i.getStatus() == IncidentStatus.RESOLVED || i.getStatus() == IncidentStatus.CLOSED)
                        && i.getResolvedAt() != null && i.getCreatedAt() != null)
                .map(i -> (double) Duration.between(i.getCreatedAt(), i.getResolvedAt()).toMinutes())
                .toList();

        Double meanMttr = mttrList.isEmpty() ? null :
                Math.round((mttrList.stream().mapToDouble(Double::doubleValue).average().orElse(0.0)) * 10.0) / 10.0;

        long uniqueTitles = incidents.stream().map(Incident::getTitle).distinct().count();
        double recurrenceRate = (totalIncidents > 1) ? Math.max(0.0, (totalIncidents - uniqueTitles) / (double) totalIncidents) : 0.0;

        List<Slo> slos = sloRepository.findByServiceNameAndEnabledTrue(svc);
        int healthySlos = 0;
        int warningSlos = 0;
        int breachedSlos = 0;
        double totalConsumed = 0.0;

        for (Slo slo : slos) {
            SloEvaluationResponse eval = sloEvaluationService.evaluateSlo(slo);
            if (eval.status() == SloStatus.BREACHED) breachedSlos++;
            else if (eval.status() == SloStatus.WARNING) warningSlos++;
            else healthySlos++;

            ErrorBudgetResponse budget = errorBudgetService.calculateErrorBudget(slo);
            totalConsumed += budget.budgetConsumedPercentage();
        }

        double avgConsumed = !slos.isEmpty() ? (totalConsumed / slos.size()) : 0.0;

        List<PostmortemActionItem> allActionItems = actionItemRepository.findAll().stream()
                .filter(a -> a.getPostmortem() != null && a.getPostmortem().getIncident() != null
                        && svc.equalsIgnoreCase(a.getPostmortem().getIncident().getService()))
                .filter(a -> a.getStatus() == ActionItemStatus.OPEN || a.getStatus() == ActionItemStatus.IN_PROGRESS)
                .toList();

        int openActionItems = allActionItems.size();
        int overdueActionItems = (int) allActionItems.stream()
                .filter(a -> a.getDueDate() != null && a.getDueDate().isBefore(Instant.now()))
                .count();

        int failedRunbooks = (int) runbookExecutionRepository.findAll().stream()
                .filter(r -> r.getIncident() != null && svc.equalsIgnoreCase(r.getIncident().getService())
                        && r.getCreatedAt() != null && r.getCreatedAt().isAfter(cutoff)
                        && r.getExecutionStatus() == RunbookExecutionStatus.FAILED)
                .count();

        ReliabilityRiskResponse risk = reliabilityRiskService.evaluateReliabilityRisk(svc);
        double healthScore = Math.max(0.0, Math.min(100.0, 100.0 - risk.riskScore()));
        double roundedHealth = Math.round(healthScore * 10.0) / 10.0;

        return new ServiceHealthSummaryResponse(
                svc,
                roundedHealth,
                risk.riskTier(),
                totalIncidents,
                activeIncidents,
                criticalIncidents,
                meanMttr,
                Math.round(recurrenceRate * 100.0) / 100.0,
                slos.size(),
                healthySlos,
                warningSlos,
                breachedSlos,
                Math.round(avgConsumed * 10.0) / 10.0,
                openActionItems,
                overdueActionItems,
                failedRunbooks,
                risk.riskFactors()
        );
    }

    private PostmortemActionItemResponse mapActionItem(PostmortemActionItem a) {
        UserDto assignee = (a.getAssignedTo() != null)
                ? new UserDto(a.getAssignedTo().getId(), a.getAssignedTo().getEmail(), a.getAssignedTo().getFirstName(), a.getAssignedTo().getLastName(), a.getAssignedTo().getRole().name())
                : null;
        return new PostmortemActionItemResponse(
                a.getId(),
                a.getPostmortem().getId(),
                a.getTitle(),
                a.getDescription(),
                a.getCategory(),
                a.getPriority(),
                a.getStatus(),
                assignee,
                a.getDueDate(),
                a.getCompletedAt(),
                a.getCreatedAt(),
                a.getUpdatedAt()
        );
    }

    private RunbookExecutionResponse mapRunbook(RunbookExecution r) {
        UserDto requestedBy = (r.getRequestedBy() != null)
                ? new UserDto(r.getRequestedBy().getId(), r.getRequestedBy().getEmail(), r.getRequestedBy().getFirstName(), r.getRequestedBy().getLastName(), r.getRequestedBy().getRole().name())
                : null;
        UserDto approvedBy = (r.getApprovedBy() != null)
                ? new UserDto(r.getApprovedBy().getId(), r.getApprovedBy().getEmail(), r.getApprovedBy().getFirstName(), r.getApprovedBy().getLastName(), r.getApprovedBy().getRole().name())
                : null;

        return new RunbookExecutionResponse(
                r.getId(),
                r.getIncident().getId(),
                r.getInvestigationStep() != null ? r.getInvestigationStep().getId() : null,
                r.getCommand(),
                r.getExecutionStatus(),
                requestedBy,
                approvedBy,
                r.getOutput(),
                r.getErrorOutput(),
                r.getStartedAt(),
                r.getCompletedAt(),
                r.getCreatedAt(),
                r.getUpdatedAt()
        );
    }
}
