package com.roottrace.commandcenter;

import com.roottrace.ai.diagnosis.AiDiagnosis;
import com.roottrace.ai.diagnosis.AiDiagnosisRepository;
import com.roottrace.ai.diagnosis.dto.DiagnosisDetailResponse;
import com.roottrace.common.exception.ResourceNotFoundException;
import com.roottrace.incident.Incident;
import com.roottrace.incident.IncidentRepository;
import com.roottrace.incident.dto.CreatorResponse;
import com.roottrace.incident.dto.IncidentResponse;
import com.roottrace.integration.ExternalTicket;
import com.roottrace.integration.ExternalTicketRepository;
import com.roottrace.integration.RunbookExecution;
import com.roottrace.integration.RunbookExecutionRepository;
import com.roottrace.integration.dto.JiraTicketResponse;
import com.roottrace.integration.dto.RunbookExecutionResponse;
import com.roottrace.intelligence.IncidentCorrelationService;
import com.roottrace.intelligence.dto.CorrelatedIncidentResponse;
import com.roottrace.intelligence.dto.RelatedIncidentsResponse;
import com.roottrace.investigation.InvestigationPlan;
import com.roottrace.investigation.InvestigationPlanRepository;
import com.roottrace.investigation.dto.InvestigationPlanResponse;
import com.roottrace.investigation.dto.InvestigationStepResponse;
import com.roottrace.postmortem.Postmortem;
import com.roottrace.postmortem.PostmortemActionItem;
import com.roottrace.postmortem.PostmortemActionItemRepository;
import com.roottrace.postmortem.PostmortemRepository;
import com.roottrace.postmortem.dto.PostmortemActionItemResponse;
import com.roottrace.postmortem.dto.PostmortemResponse;
import com.roottrace.postmortem.dto.PostmortemTimelineEntry;
import com.roottrace.slo.BurnRateService;
import com.roottrace.slo.ErrorBudgetService;
import com.roottrace.slo.Slo;
import com.roottrace.slo.SloEvaluationService;
import com.roottrace.slo.SloRepository;
import com.roottrace.slo.SloStatus;
import com.roottrace.slo.dto.BurnRateResponse;
import com.roottrace.slo.dto.ErrorBudgetResponse;
import com.roottrace.slo.dto.SloEvaluationResponse;
import com.roottrace.user.dto.UserDto;
import com.roottrace.commandcenter.dto.IncidentCommandResponse;
import com.roottrace.commandcenter.dto.ReliabilityRecommendationResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class IncidentCommandService {

    private final IncidentRepository incidentRepository;
    private final AiDiagnosisRepository diagnosisRepository;
    private final InvestigationPlanRepository investigationPlanRepository;
    private final IncidentCorrelationService correlationService;
    private final PostmortemRepository postmortemRepository;
    private final PostmortemActionItemRepository actionItemRepository;
    private final SloRepository sloRepository;
    private final SloEvaluationService sloEvaluationService;
    private final ErrorBudgetService errorBudgetService;
    private final BurnRateService burnRateService;
    private final RunbookExecutionRepository runbookExecutionRepository;
    private final ExternalTicketRepository externalTicketRepository;
    private final ReliabilityRecommendationService recommendationService;

    public IncidentCommandService(
            IncidentRepository incidentRepository,
            AiDiagnosisRepository diagnosisRepository,
            InvestigationPlanRepository investigationPlanRepository,
            IncidentCorrelationService correlationService,
            PostmortemRepository postmortemRepository,
            PostmortemActionItemRepository actionItemRepository,
            SloRepository sloRepository,
            SloEvaluationService sloEvaluationService,
            ErrorBudgetService errorBudgetService,
            BurnRateService burnRateService,
            RunbookExecutionRepository runbookExecutionRepository,
            ExternalTicketRepository externalTicketRepository,
            ReliabilityRecommendationService recommendationService) {
        this.incidentRepository = incidentRepository;
        this.diagnosisRepository = diagnosisRepository;
        this.investigationPlanRepository = investigationPlanRepository;
        this.correlationService = correlationService;
        this.postmortemRepository = postmortemRepository;
        this.actionItemRepository = actionItemRepository;
        this.sloRepository = sloRepository;
        this.sloEvaluationService = sloEvaluationService;
        this.errorBudgetService = errorBudgetService;
        this.burnRateService = burnRateService;
        this.runbookExecutionRepository = runbookExecutionRepository;
        this.externalTicketRepository = externalTicketRepository;
        this.recommendationService = recommendationService;
    }

    @Transactional(readOnly = true)
    public IncidentCommandResponse getIncidentCommandDetails(UUID incidentId) {
        Incident incident = incidentRepository.findByIdAndNotDeleted(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident", incidentId));

        String svc = incident.getService();

        // 1. Incident Details
        IncidentResponse incResp = mapIncident(incident);

        // 2. Latest AI Diagnosis
        DiagnosisDetailResponse diagnosisResp = diagnosisRepository.findByIncidentIdOrderByCreatedAtDesc(incidentId).stream()
                .findFirst()
                .map(this::mapDiagnosis)
                .orElse(null);

        // 3. Latest Investigation Plan
        InvestigationPlanResponse planResp = investigationPlanRepository.findByIncidentIdWithSteps(incidentId).stream()
                .findFirst()
                .map(this::mapPlan)
                .orElse(null);

        // 4. Correlated / Related Incidents
        RelatedIncidentsResponse corrResp = correlationService.findRelatedIncidents(incidentId, 5, 0.60, false);
        List<CorrelatedIncidentResponse> relatedIncidents = (corrResp != null && corrResp.relatedIncidents() != null)
                ? corrResp.relatedIncidents() : List.of();

        // 5. Postmortem & Action Items
        Postmortem pm = postmortemRepository.findByIncidentIdWithActionItems(incidentId).orElse(null);
        PostmortemResponse pmResp = pm != null ? mapPostmortem(pm) : null;

        List<PostmortemActionItemResponse> actionItems = (pm != null && pm.getActionItems() != null)
                ? pm.getActionItems().stream().map(this::mapActionItem).collect(Collectors.toList())
                : List.of();

        // 6. Service SLO Impact & Error Budget Impact
        List<Slo> slos = (svc != null && !svc.isBlank())
                ? sloRepository.findByServiceNameAndEnabledTrue(svc)
                : List.of();

        List<SloEvaluationResponse> sloEvals = new ArrayList<>();
        List<ErrorBudgetResponse> budgets = new ArrayList<>();
        List<BurnRateResponse> burns = new ArrayList<>();
        int breachedCount = 0;
        double totalConsumed = 0.0;

        for (Slo slo : slos) {
            SloEvaluationResponse eval = sloEvaluationService.evaluateSlo(slo);
            sloEvals.add(eval);
            if (eval.status() == SloStatus.BREACHED) breachedCount++;

            ErrorBudgetResponse budget = errorBudgetService.calculateErrorBudget(slo);
            budgets.add(budget);
            totalConsumed += budget.budgetConsumedPercentage();

            burns.add(burnRateService.calculateBurnRate(slo, 60));
        }

        double avgConsumed = !slos.isEmpty() ? (totalConsumed / slos.size()) : 0.0;
        double avgRemaining = Math.max(0.0, 100.0 - avgConsumed);
        String budgetStatus = (avgConsumed >= 100.0) ? "BREACHED" : (avgConsumed >= 75.0 ? "WARNING" : "HEALTHY");

        IncidentCommandResponse.SloImpactSummary sloImpact = new IncidentCommandResponse.SloImpactSummary(
                !slos.isEmpty(), breachedCount, sloEvals
        );

        IncidentCommandResponse.ErrorBudgetImpactSummary budgetImpact = new IncidentCommandResponse.ErrorBudgetImpactSummary(
                Math.round(avgConsumed * 10.0) / 10.0,
                Math.round(avgRemaining * 10.0) / 10.0,
                budgetStatus
        );

        // 7. Runbook Executions
        List<RunbookExecutionResponse> runbookExecutions = runbookExecutionRepository.findByIncidentIdOrderByCreatedAtDesc(incidentId)
                .stream()
                .map(this::mapRunbook)
                .collect(Collectors.toList());

        // 8. External Tickets
        List<JiraTicketResponse> externalTickets = externalTicketRepository.findByIncidentId(incidentId)
                .stream()
                .map(this::mapTicket)
                .collect(Collectors.toList());

        // 9. Timeline Operational Events
        List<String> timelineEvents = new ArrayList<>();
        timelineEvents.add(String.format("Incident created at %s with severity %s", incident.getCreatedAt(), incident.getSeverity()));
        if (diagnosisResp != null) {
            timelineEvents.add(String.format("AI Diagnosis generated at %s: %s (Confidence: %.0f%%)",
                    diagnosisResp.createdAt(), diagnosisResp.probableRootCause(), (diagnosisResp.confidence() != null ? diagnosisResp.confidence() : 0.0) * 100.0));
        }
        if (planResp != null) {
            timelineEvents.add(String.format("Investigation plan generated at %s with %d step(s)",
                    planResp.createdAt(), planResp.steps().size()));
        }
        if (!runbookExecutions.isEmpty()) {
            timelineEvents.add(String.format("%d automated runbook(s) executed", runbookExecutions.size()));
        }
        if (incident.getResolvedAt() != null) {
            timelineEvents.add(String.format("Incident resolved at %s", incident.getResolvedAt()));
        }
        if (pmResp != null) {
            timelineEvents.add(String.format("Postmortem %s at %s", pmResp.status(), pmResp.updatedAt()));
        }

        // 10. Deterministic Recommendations
        List<ReliabilityRecommendationResponse> recommendations = recommendationService.generateServiceRecommendations(
                svc, sloEvals, budgets, burns, 1, 0.0, 0, 0
        );

        return new IncidentCommandResponse(
                incResp,
                diagnosisResp,
                planResp,
                relatedIncidents,
                pmResp,
                actionItems,
                sloImpact,
                budgetImpact,
                runbookExecutions,
                externalTickets,
                timelineEvents,
                recommendations
        );
    }

    private IncidentResponse mapIncident(Incident inc) {
        CreatorResponse creator = (inc.getCreatedBy() != null)
                ? new CreatorResponse(inc.getCreatedBy().getId(), (inc.getCreatedBy().getFirstName() != null ? inc.getCreatedBy().getFirstName() + " " + (inc.getCreatedBy().getLastName() != null ? inc.getCreatedBy().getLastName() : "") : inc.getCreatedBy().getEmail()).trim())
                : null;

        return new IncidentResponse(
                inc.getId(),
                inc.getTitle(),
                inc.getDescription(),
                inc.getService(),
                inc.getSeverity(),
                inc.getStatus(),
                inc.getEnvironment(),
                creator,
                inc.getCreatedAt(),
                inc.getUpdatedAt(),
                inc.getResolvedAt(),
                inc.getResolution()
        );
    }

    private DiagnosisDetailResponse mapDiagnosis(AiDiagnosis d) {
        UserDto creator = (d.getCreatedBy() != null)
                ? new UserDto(d.getCreatedBy().getId(), d.getCreatedBy().getEmail(), d.getCreatedBy().getFirstName(), d.getCreatedBy().getLastName(), d.getCreatedBy().getRole().name())
                : null;
        return new DiagnosisDetailResponse(
                d.getId(),
                d.getIncident().getId(),
                d.getSummary(),
                d.getProbableRootCause(),
                d.getConfidence(),
                d.getContributingFactors() != null ? d.getContributingFactors() : Collections.emptyList(),
                d.getRecommendedActions() != null ? d.getRecommendedActions() : Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                creator,
                d.getCreatedAt()
        );
    }

    private InvestigationPlanResponse mapPlan(InvestigationPlan p) {
        List<InvestigationStepResponse> steps = (p.getSteps() != null)
                ? p.getSteps().stream().map(s -> {
                    UserDto stepAssignee = (s.getAssignedTo() != null)
                            ? new UserDto(s.getAssignedTo().getId(), s.getAssignedTo().getEmail(), s.getAssignedTo().getFirstName(), s.getAssignedTo().getLastName(), s.getAssignedTo().getRole().name())
                            : null;
                    return new InvestigationStepResponse(
                            s.getId(), s.getStepOrder(), s.getTitle(), s.getDescription(), s.getStatus(), s.getEvidence(), stepAssignee, s.getCompletedAt(), s.getCreatedAt(), s.getUpdatedAt()
                    );
                }).collect(Collectors.toList())
                : List.of();

        UserDto planCreator = (p.getCreatedBy() != null)
                ? new UserDto(p.getCreatedBy().getId(), p.getCreatedBy().getEmail(), p.getCreatedBy().getFirstName(), p.getCreatedBy().getLastName(), p.getCreatedBy().getRole().name())
                : null;

        return new InvestigationPlanResponse(
                p.getId(),
                p.getIncident().getId(),
                p.getSourceDiagnosis() != null ? p.getSourceDiagnosis().getId() : null,
                p.getTitle(),
                planCreator,
                steps,
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }

    private PostmortemResponse mapPostmortem(Postmortem pm) {
        List<PostmortemActionItemResponse> actionItems = (pm.getActionItems() != null)
                ? pm.getActionItems().stream().map(this::mapActionItem).collect(Collectors.toList())
                : List.of();

        List<PostmortemTimelineEntry> timeline = (pm.getTimeline() != null) ? pm.getTimeline() : Collections.emptyList();

        UserDto author = (pm.getCreatedBy() != null)
                ? new UserDto(pm.getCreatedBy().getId(), pm.getCreatedBy().getEmail(), pm.getCreatedBy().getFirstName(), pm.getCreatedBy().getLastName(), pm.getCreatedBy().getRole().name())
                : null;

        return new PostmortemResponse(
                pm.getId(),
                pm.getIncident().getId(),
                pm.getTitle(),
                pm.getSummary(),
                pm.getImpactSummary(),
                pm.getRootCauseAnalysis(),
                pm.getResolutionSummary(),
                timeline,
                pm.getLessonsLearned() != null ? pm.getLessonsLearned() : Collections.emptyList(),
                pm.getStatus(),
                pm.getDowntimeMinutes(),
                author,
                pm.getPublishedAt(),
                actionItems,
                pm.getCreatedAt(),
                pm.getUpdatedAt()
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

    private JiraTicketResponse mapTicket(ExternalTicket t) {
        return new JiraTicketResponse(
                t.getId(),
                t.getIncident().getId(),
                t.getActionItem() != null ? t.getActionItem().getId() : null,
                t.getProvider(),
                t.getExternalTicketId(),
                t.getExternalUrl(),
                t.getStatus()
        );
    }
}
