package com.roottrace.slo;

import com.roottrace.common.audit.AuditEventType;
import com.roottrace.common.audit.AuditService;
import com.roottrace.common.security.CurrentUserService;
import com.roottrace.incident.Incident;
import com.roottrace.incident.IncidentRepository;
import com.roottrace.postmortem.ActionItemStatus;
import com.roottrace.postmortem.PostmortemActionItem;
import com.roottrace.postmortem.PostmortemActionItemRepository;
import com.roottrace.slo.dto.ReliabilityAdvisorAiResponse;
import com.roottrace.slo.dto.ReliabilityAdvisorResponse;
import com.roottrace.slo.dto.ReliabilityDashboardResponse;
import com.roottrace.slo.dto.ReliabilityRiskResponse;
import com.roottrace.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class ReliabilityAdvisorService {

    private static final Logger log = LoggerFactory.getLogger(ReliabilityAdvisorService.class);

    private final SloService sloService;
    private final ReliabilityRiskService reliabilityRiskService;
    private final ReliabilityPromptBuilder promptBuilder;
    private final GeminiReliabilityAdvisorService geminiAdvisorService;
    private final IncidentRepository incidentRepository;
    private final PostmortemActionItemRepository actionItemRepository;
    private final CurrentUserService currentUserService;
    private final AuditService auditService;

    public ReliabilityAdvisorService(
            SloService sloService,
            ReliabilityRiskService reliabilityRiskService,
            ReliabilityPromptBuilder promptBuilder,
            GeminiReliabilityAdvisorService geminiAdvisorService,
            IncidentRepository incidentRepository,
            PostmortemActionItemRepository actionItemRepository,
            CurrentUserService currentUserService,
            AuditService auditService) {
        this.sloService = sloService;
        this.reliabilityRiskService = reliabilityRiskService;
        this.promptBuilder = promptBuilder;
        this.geminiAdvisorService = geminiAdvisorService;
        this.incidentRepository = incidentRepository;
        this.actionItemRepository = actionItemRepository;
        this.currentUserService = currentUserService;
        this.auditService = auditService;
    }

    public ReliabilityAdvisorResponse generateReliabilityAdvice(String serviceName) {
        String svc = (serviceName != null && !serviceName.isBlank()) ? serviceName.trim() : "default";

        // Step 1: Gather facts in read transaction
        ReliabilityContext facts = gatherFacts(svc);

        // Step 2: Build prompt
        String prompt = promptBuilder.buildPrompt(
                svc,
                facts.dashboard(),
                facts.risk(),
                facts.recentIncidentSummaries(),
                facts.unresolvedActionItemSummaries()
        );

        // Step 3: Invoke Gemini outside transaction
        ReliabilityAdvisorAiResponse aiResponse = geminiAdvisorService.generateAdvisorRecommendations(prompt);

        // Step 4: Record audit event
        User currentUser = currentUserService.getCurrentUser();
        auditService.record(
                AuditEventType.RELIABILITY_ADVISOR_GENERATED,
                "ServiceReliability",
                svc,
                currentUser != null ? currentUser.getEmail() : "system",
                "Generated AI reliability advisory report for service: " + svc
        );

        return new ReliabilityAdvisorResponse(
                svc,
                aiResponse.executiveSummary(),
                aiResponse.reliabilityConcerns(),
                aiResponse.recommendedActions(),
                aiResponse.priority() != null ? aiResponse.priority() : facts.risk().riskTier(),
                facts.risk().riskScore(),
                facts.risk().riskTier(),
                facts.dashboard().activeBreaches(),
                Instant.now()
        );
    }

    @Transactional(readOnly = true)
    protected ReliabilityContext gatherFacts(String svc) {
        ReliabilityDashboardResponse dashboard = sloService.getReliabilityDashboard(svc);
        ReliabilityRiskResponse risk = reliabilityRiskService.evaluateReliabilityRisk(svc);

        Instant thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);
        List<String> recentIncidents = incidentRepository.findAllNotDeleted(Pageable.unpaged()).getContent()
                .stream()
                .filter(i -> i.getService() != null && i.getService().equalsIgnoreCase(svc)
                        && i.getCreatedAt() != null && i.getCreatedAt().isAfter(thirtyDaysAgo))
                .map(i -> String.format("[%s] %s (Severity: %s, Status: %s)", i.getId(), i.getTitle(), i.getSeverity(), i.getStatus()))
                .toList();

        List<String> openActionItems = actionItemRepository.findAll().stream()
                .filter(a -> a.getStatus() == ActionItemStatus.OPEN || a.getStatus() == ActionItemStatus.IN_PROGRESS)
                .filter(a -> a.getPostmortem() != null && a.getPostmortem().getIncident() != null
                        && svc.equalsIgnoreCase(a.getPostmortem().getIncident().getService()))
                .map(a -> String.format("- %s [%s] (Priority: %s)", a.getTitle(), a.getCategory(), a.getPriority()))
                .toList();

        return new ReliabilityContext(dashboard, risk, recentIncidents, openActionItems);
    }

    protected record ReliabilityContext(
            ReliabilityDashboardResponse dashboard,
            ReliabilityRiskResponse risk,
            List<String> recentIncidentSummaries,
            List<String> unresolvedActionItemSummaries
    ) {}
}
