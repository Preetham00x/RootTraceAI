package com.roottrace.commandcenter;

import com.roottrace.commandcenter.dto.CommandCenterOverviewResponse;
import com.roottrace.commandcenter.dto.ExecutiveReliabilityAdvisorAiResponse;
import com.roottrace.commandcenter.dto.ExecutiveReliabilityAdvisorResponse;
import com.roottrace.commandcenter.dto.ReliabilityScoreResponse;
import com.roottrace.commandcenter.dto.ServiceHealthSummaryResponse;
import com.roottrace.common.audit.AuditEventType;
import com.roottrace.common.audit.AuditService;
import com.roottrace.common.exception.BadRequestException;
import com.roottrace.common.security.CurrentUserService;
import com.roottrace.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class ExecutiveReliabilityService {

    private static final Logger log = LoggerFactory.getLogger(ExecutiveReliabilityService.class);

    private final CommandCenterService commandCenterService;
    private final ServiceHealthService serviceHealthService;
    private final ReliabilityScoreService reliabilityScoreService;
    private final ExecutiveReliabilityPromptBuilder promptBuilder;
    private final GeminiExecutiveReliabilityService geminiExecutiveService;
    private final CurrentUserService currentUserService;
    private final AuditService auditService;

    public ExecutiveReliabilityService(
            CommandCenterService commandCenterService,
            ServiceHealthService serviceHealthService,
            ReliabilityScoreService reliabilityScoreService,
            ExecutiveReliabilityPromptBuilder promptBuilder,
            GeminiExecutiveReliabilityService geminiExecutiveService,
            CurrentUserService currentUserService,
            AuditService auditService) {
        this.commandCenterService = commandCenterService;
        this.serviceHealthService = serviceHealthService;
        this.reliabilityScoreService = reliabilityScoreService;
        this.promptBuilder = promptBuilder;
        this.geminiExecutiveService = geminiExecutiveService;
        this.currentUserService = currentUserService;
        this.auditService = auditService;
    }

    public ExecutiveReliabilityAdvisorResponse generateExecutiveAdvisor(Integer days) {
        int windowDays = (days != null) ? days : 30;
        if (windowDays < 1 || windowDays > 365) {
            throw new BadRequestException("days parameter must be between 1 and 365. Provided: " + windowDays);
        }

        // 1. Gather deterministic facts in read-only transaction
        ExecutiveContext facts = gatherFacts(windowDays);

        // 2. Build prompt
        String prompt = promptBuilder.buildPrompt(
                facts.overview(),
                facts.scoreResponse(),
                facts.riskyServices()
        );

        // 3. Invoke Gemini outside transaction
        ExecutiveReliabilityAdvisorAiResponse aiResponse = geminiExecutiveService.generateExecutiveAdvice(prompt);

        // 4. Audit generation
        User currentUser = currentUserService.getCurrentUser();
        auditService.record(
                AuditEventType.AI_EXECUTIVE_RELIABILITY_ADVISOR_GENERATED,
                "ExecutiveReliability",
                "Organization",
                currentUser != null ? currentUser.getEmail() : "system",
                "Generated Executive AI Reliability Advisor report for " + windowDays + " days window"
        );

        return new ExecutiveReliabilityAdvisorResponse(
                aiResponse.executiveSummary(),
                aiResponse.keyConcerns(),
                aiResponse.servicesRequiringAttention(),
                aiResponse.recommendedActions(),
                aiResponse.positiveSignals(),
                facts.overview().overallReliabilityScore(),
                facts.overview().overallRiskTier(),
                facts.overview().breachedSlos(),
                Instant.now()
        );
    }

    @Transactional(readOnly = true)
    protected ExecutiveContext gatherFacts(int windowDays) {
        CommandCenterOverviewResponse overview = commandCenterService.getOverview(windowDays);
        List<ServiceHealthSummaryResponse> riskyServices = serviceHealthService.getServiceHealthSummaries(windowDays, 5, "risk");

        ReliabilityScoreResponse scoreResponse = reliabilityScoreService.calculateReliabilityScore(
                overview.breachedSlos(),
                overview.warningSlos(),
                overview.errorBudgetConsumptionPercent(),
                overview.criticalIncidents(),
                overview.highIncidents(),
                0.0,
                overview.overdueActionItems(),
                overview.failedRunbookExecutions()
        );

        return new ExecutiveContext(overview, scoreResponse, riskyServices);
    }

    protected record ExecutiveContext(
            CommandCenterOverviewResponse overview,
            ReliabilityScoreResponse scoreResponse,
            List<ServiceHealthSummaryResponse> riskyServices
    ) {}
}
