package com.roottrace.commandcenter;

import com.roottrace.commandcenter.dto.ReliabilityEventResponse;
import com.roottrace.commandcenter.dto.ReliabilityEventsResponse;
import com.roottrace.common.exception.BadRequestException;
import com.roottrace.incident.Incident;
import com.roottrace.incident.IncidentRepository;
import com.roottrace.incident.IncidentSeverity;
import com.roottrace.incident.IncidentStatus;
import com.roottrace.integration.RunbookExecution;
import com.roottrace.integration.RunbookExecutionRepository;
import com.roottrace.integration.RunbookExecutionStatus;
import com.roottrace.postmortem.ActionItemStatus;
import com.roottrace.postmortem.Postmortem;
import com.roottrace.postmortem.PostmortemActionItem;
import com.roottrace.postmortem.PostmortemActionItemRepository;
import com.roottrace.postmortem.PostmortemRepository;
import com.roottrace.postmortem.PostmortemStatus;
import com.roottrace.slo.ErrorBudgetService;
import com.roottrace.slo.Slo;
import com.roottrace.slo.SloEvaluationService;
import com.roottrace.slo.SloRepository;
import com.roottrace.slo.SloStatus;
import com.roottrace.slo.dto.ErrorBudgetResponse;
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
public class ReliabilityEventFeedService {

    private final IncidentRepository incidentRepository;
    private final SloRepository sloRepository;
    private final SloEvaluationService sloEvaluationService;
    private final ErrorBudgetService errorBudgetService;
    private final PostmortemRepository postmortemRepository;
    private final PostmortemActionItemRepository actionItemRepository;
    private final RunbookExecutionRepository runbookExecutionRepository;

    public ReliabilityEventFeedService(
            IncidentRepository incidentRepository,
            SloRepository sloRepository,
            SloEvaluationService sloEvaluationService,
            ErrorBudgetService errorBudgetService,
            PostmortemRepository postmortemRepository,
            PostmortemActionItemRepository actionItemRepository,
            RunbookExecutionRepository runbookExecutionRepository) {
        this.incidentRepository = incidentRepository;
        this.sloRepository = sloRepository;
        this.sloEvaluationService = sloEvaluationService;
        this.errorBudgetService = errorBudgetService;
        this.postmortemRepository = postmortemRepository;
        this.actionItemRepository = actionItemRepository;
        this.runbookExecutionRepository = runbookExecutionRepository;
    }

    @Transactional(readOnly = true)
    public ReliabilityEventsResponse getEventFeed(String serviceName, Integer days, Integer limit) {
        int windowDays = (days != null) ? days : 30;
        if (windowDays < 1 || windowDays > 365) {
            throw new BadRequestException("days parameter must be between 1 and 365. Provided: " + windowDays);
        }

        int maxLimit = (limit != null) ? limit : 50;
        if (maxLimit < 1 || maxLimit > 100) {
            throw new BadRequestException("limit parameter must be between 1 and 100. Provided: " + maxLimit);
        }

        Instant cutoff = Instant.now().minus(Duration.ofDays(windowDays));
        String svcFilter = (serviceName != null && !serviceName.isBlank()) ? serviceName.trim() : null;

        List<ReliabilityEventResponse> allEvents = new ArrayList<>();

        // 1. Collect Incident Events
        List<Incident> incidents = incidentRepository.findAllNotDeleted(Pageable.unpaged()).getContent();
        for (Incident inc : incidents) {
            if (svcFilter != null && !inc.getService().equalsIgnoreCase(svcFilter)) {
                continue;
            }

            if (inc.getCreatedAt() != null && inc.getCreatedAt().isAfter(cutoff)) {
                String sev = inc.getSeverity() == IncidentSeverity.CRITICAL ? "CRITICAL" :
                        (inc.getSeverity() == IncidentSeverity.HIGH ? "HIGH" : "MEDIUM");
                allEvents.add(new ReliabilityEventResponse(
                        inc.getCreatedAt(),
                        "INCIDENT_CREATED",
                        sev,
                        inc.getService(),
                        inc.getId().toString(),
                        String.format("Incident created: %s [%s]", inc.getTitle(), inc.getSeverity())
                ));
            }

            if (inc.getResolvedAt() != null && inc.getResolvedAt().isAfter(cutoff)
                    && (inc.getStatus() == IncidentStatus.RESOLVED || inc.getStatus() == IncidentStatus.CLOSED)) {
                allEvents.add(new ReliabilityEventResponse(
                        inc.getResolvedAt(),
                        "INCIDENT_RESOLVED",
                        "INFO",
                        inc.getService(),
                        inc.getId().toString(),
                        String.format("Incident resolved: %s", inc.getTitle())
                ));
            }
        }

        // 2. Collect SLO & Error Budget Events
        List<Slo> slos = sloRepository.findAll().stream()
                .filter(Slo::getEnabled)
                .filter(s -> svcFilter == null || s.getServiceName().equalsIgnoreCase(svcFilter))
                .toList();

        for (Slo slo : slos) {
            SloEvaluationResponse eval = sloEvaluationService.evaluateSlo(slo);
            ErrorBudgetResponse budget = errorBudgetService.calculateErrorBudget(slo);

            if (eval.status() == SloStatus.BREACHED) {
                allEvents.add(new ReliabilityEventResponse(
                        eval.evaluatedAt() != null ? eval.evaluatedAt() : Instant.now(),
                        "SLO_BREACH",
                        "CRITICAL",
                        slo.getServiceName(),
                        slo.getId().toString(),
                        String.format("SLO '%s' breached availability target (Actual: %.2f%%, Target: %.2f%%)",
                                slo.getName(), eval.actualPercentage(), eval.targetPercentage())
                ));
            } else if (eval.status() == SloStatus.WARNING || budget.budgetConsumedPercentage() >= 75.0) {
                allEvents.add(new ReliabilityEventResponse(
                        eval.evaluatedAt() != null ? eval.evaluatedAt() : Instant.now(),
                        "ERROR_BUDGET_WARNING",
                        "HIGH",
                        slo.getServiceName(),
                        slo.getId().toString(),
                        String.format("SLO '%s' has consumed %.1f%% of its error budget",
                                slo.getName(), budget.budgetConsumedPercentage())
                ));
            }
        }

        // 3. Collect Postmortem & Action Item Events
        List<Postmortem> postmortems = postmortemRepository.findAll();
        for (Postmortem pm : postmortems) {
            if (pm.getIncident() != null) {
                String svc = pm.getIncident().getService();
                if (svcFilter != null && !svc.equalsIgnoreCase(svcFilter)) {
                    continue;
                }

                if (pm.getStatus() == PostmortemStatus.PUBLISHED && pm.getUpdatedAt() != null && pm.getUpdatedAt().isAfter(cutoff)) {
                    allEvents.add(new ReliabilityEventResponse(
                            pm.getUpdatedAt(),
                            "POSTMORTEM_PUBLISHED",
                            "INFO",
                            svc,
                            pm.getId().toString(),
                            String.format("Postmortem published for incident: %s", pm.getIncident().getTitle())
                    ));
                }
            }
        }

        List<PostmortemActionItem> actionItems = actionItemRepository.findAll();
        Instant now = Instant.now();
        for (PostmortemActionItem item : actionItems) {
            if (item.getPostmortem() != null && item.getPostmortem().getIncident() != null) {
                String svc = item.getPostmortem().getIncident().getService();
                if (svcFilter != null && !svc.equalsIgnoreCase(svcFilter)) {
                    continue;
                }

                if ((item.getStatus() == ActionItemStatus.OPEN || item.getStatus() == ActionItemStatus.IN_PROGRESS)
                        && item.getDueDate() != null && item.getDueDate().isBefore(now)) {
                    allEvents.add(new ReliabilityEventResponse(
                            item.getDueDate(),
                            "ACTION_ITEM_OVERDUE",
                            "HIGH",
                            svc,
                            item.getId().toString(),
                            String.format("Postmortem action item is overdue: %s [%s]", item.getTitle(), item.getPriority())
                    ));
                }
            }
        }

        // 4. Collect Runbook Execution Events
        List<RunbookExecution> runbooks = runbookExecutionRepository.findAll();
        for (RunbookExecution rb : runbooks) {
            if (rb.getIncident() != null) {
                String svc = rb.getIncident().getService();
                if (svcFilter != null && !svc.equalsIgnoreCase(svcFilter)) {
                    continue;
                }

                if (rb.getCreatedAt() != null && rb.getCreatedAt().isAfter(cutoff)) {
                    if (rb.getExecutionStatus() == RunbookExecutionStatus.FAILED) {
                        allEvents.add(new ReliabilityEventResponse(
                                rb.getCompletedAt() != null ? rb.getCompletedAt() : rb.getCreatedAt(),
                                "RUNBOOK_FAILED",
                                "HIGH",
                                svc,
                                rb.getId().toString(),
                                String.format("Automated remediation runbook failed: %s", rb.getCommand())
                        ));
                    } else if (rb.getExecutionStatus() == RunbookExecutionStatus.SUCCEEDED) {
                        allEvents.add(new ReliabilityEventResponse(
                                rb.getCompletedAt() != null ? rb.getCompletedAt() : rb.getCreatedAt(),
                                "RUNBOOK_COMPLETED",
                                "INFO",
                                svc,
                                rb.getId().toString(),
                                String.format("Automated remediation runbook completed: %s", rb.getCommand())
                        ));
                    }
                }
            }
        }

        // Sort descending by timestamp
        List<ReliabilityEventResponse> sorted = allEvents.stream()
                .filter(e -> e.timestamp() != null)
                .sorted(Comparator.comparing(ReliabilityEventResponse::timestamp).reversed())
                .limit(maxLimit)
                .collect(Collectors.toList());

        return new ReliabilityEventsResponse(sorted.size(), sorted);
    }
}
