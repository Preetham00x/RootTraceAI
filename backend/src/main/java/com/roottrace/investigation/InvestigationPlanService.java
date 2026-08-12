package com.roottrace.investigation;

import com.roottrace.ai.diagnosis.AiDiagnosis;
import com.roottrace.ai.diagnosis.AiDiagnosisRepository;
import com.roottrace.common.audit.AuditService;
import com.roottrace.common.audit.AuditEventType;
import com.roottrace.common.exception.BadRequestException;
import com.roottrace.common.exception.ResourceNotFoundException;
import com.roottrace.common.security.CurrentUserService;
import com.roottrace.incident.Incident;
import com.roottrace.incident.IncidentRepository;
import com.roottrace.investigation.dto.CreateInvestigationPlanRequest;
import com.roottrace.investigation.dto.GenerateInvestigationPlanRequest;
import com.roottrace.investigation.dto.InvestigationPlanAiResponse;
import com.roottrace.investigation.dto.InvestigationPlanResponse;
import com.roottrace.investigation.dto.InvestigationStepResponse;
import com.roottrace.investigation.dto.UpdateInvestigationStepRequest;
import com.roottrace.user.User;
import com.roottrace.user.UserRepository;
import com.roottrace.user.dto.UserDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class InvestigationPlanService {

    private static final Logger log = LoggerFactory.getLogger(InvestigationPlanService.class);

    private final IncidentRepository incidentRepository;
    private final AiDiagnosisRepository diagnosisRepository;
    private final InvestigationPlanRepository planRepository;
    private final InvestigationStepRepository stepRepository;
    private final UserRepository userRepository;
    private final GeminiInvestigationService geminiInvestigationService;
    private final CurrentUserService currentUserService;
    private final AuditService auditService;

    public InvestigationPlanService(
            IncidentRepository incidentRepository,
            AiDiagnosisRepository diagnosisRepository,
            InvestigationPlanRepository planRepository,
            InvestigationStepRepository stepRepository,
            UserRepository userRepository,
            GeminiInvestigationService geminiInvestigationService,
            CurrentUserService currentUserService,
            AuditService auditService) {
        this.incidentRepository = incidentRepository;
        this.diagnosisRepository = diagnosisRepository;
        this.planRepository = planRepository;
        this.stepRepository = stepRepository;
        this.userRepository = userRepository;
        this.geminiInvestigationService = geminiInvestigationService;
        this.currentUserService = currentUserService;
        this.auditService = auditService;
    }

    /**
     * Generates a structured investigation plan from an existing diagnosis.
     * Follows the transaction separation pattern: Load data -> AI call outside TX -> Persist in new TX.
     */
    public InvestigationPlanResponse generatePlan(UUID incidentId, GenerateInvestigationPlanRequest request) {
        User currentUser = currentUserService.getCurrentUser();

        // 1. TX1: Load incident and diagnosis
        Incident incident = loadIncident(incidentId);
        AiDiagnosis diagnosis = loadDiagnosis(request.diagnosisId(), incidentId);

        // 2. OUTSIDE TX: Call Gemini
        InvestigationPlanAiResponse aiResponse = geminiInvestigationService.generatePlan(incident, diagnosis);

        // 3. TX2: Persist Plan + Steps
        InvestigationPlan savedPlan = persistGeneratedPlan(incident, diagnosis, aiResponse, currentUser);

        // 4. Audit
        auditService.record(
                AuditEventType.INVESTIGATION_PLAN_CREATED,
                "InvestigationPlan",
                String.valueOf(savedPlan.getId()),
                currentUser.getEmail(),
                "Generated investigation plan for incident " + incidentId
        );

        return mapToResponse(savedPlan);
    }

    /**
     * Manually creates an investigation plan with provided steps.
     */
    @Transactional
    public InvestigationPlanResponse createPlan(UUID incidentId, CreateInvestigationPlanRequest request) {
        User currentUser = currentUserService.getCurrentUser();
        Incident incident = loadIncident(incidentId);

        AiDiagnosis sourceDiagnosis = null;
        if (request.sourceDiagnosisId() != null) {
            sourceDiagnosis = loadDiagnosis(request.sourceDiagnosisId(), incidentId);
        }

        InvestigationPlan plan = new InvestigationPlan(incident, sourceDiagnosis, request.title(), currentUser);

        int order = 1;
        for (CreateInvestigationPlanRequest.CreateInvestigationStepRequest stepReq : request.steps()) {
            User assignedTo = null;
            if (stepReq.assignedToId() != null) {
                assignedTo = userRepository.findById(stepReq.assignedToId())
                        .orElseThrow(() -> new ResourceNotFoundException("User", stepReq.assignedToId()));
            }
            InvestigationStep step = new InvestigationStep(plan, order++, stepReq.title(), stepReq.description(), assignedTo);
            plan.addStep(step);
        }

        InvestigationPlan savedPlan = planRepository.save(plan);

        auditService.record(
                AuditEventType.INVESTIGATION_PLAN_CREATED,
                "InvestigationPlan",
                String.valueOf(savedPlan.getId()),
                currentUser.getEmail(),
                "Manually created investigation plan for incident " + incidentId
        );

        return mapToResponse(savedPlan);
    }

    /**
     * Lists all investigation plans for an incident.
     */
    @Transactional(readOnly = true)
    public List<InvestigationPlanResponse> getPlans(UUID incidentId) {
        // Validate incident exists
        loadIncident(incidentId);

        List<InvestigationPlan> plans = planRepository.findByIncidentIdWithSteps(incidentId);
        return plans.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Updates an investigation step's status, evidence, or assigned user.
     */
    @Transactional
    public InvestigationStepResponse updateStep(UUID incidentId, UUID planId, UUID stepId, UpdateInvestigationStepRequest request) {
        // Verify incident exists
        loadIncident(incidentId);

        // Find plan and ensure it belongs to the incident
        InvestigationPlan plan = planRepository.findByIdAndIncidentIdWithSteps(planId, incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("InvestigationPlan", planId));

        // Find step and ensure it belongs to the plan
        InvestigationStep step = stepRepository.findByIdAndPlanId(stepId, planId)
                .orElseThrow(() -> new ResourceNotFoundException("InvestigationStep", stepId));

        boolean statusChanged = false;
        boolean becameCompleted = false;

        if (request.status() != null) {
            if (!step.getStatus().canTransitionTo(request.status())) {
                throw new BadRequestException("Cannot transition step status from " + step.getStatus() + " to " + request.status());
            }

            if (step.getStatus() != request.status()) {
                statusChanged = true;
                if (request.status() == InvestigationStepStatus.COMPLETED) {
                    step.setCompletedAt(Instant.now());
                    becameCompleted = true;
                } else if (step.getStatus() == InvestigationStepStatus.COMPLETED) {
                    step.setCompletedAt(null);
                }
                step.setStatus(request.status());
            }
        }

        if (request.evidence() != null) {
            step.setEvidence(request.evidence());
        }

        if (request.assignedToId() != null) {
            User assignedTo = userRepository.findById(request.assignedToId())
                    .orElseThrow(() -> new ResourceNotFoundException("User", request.assignedToId()));
            step.setAssignedTo(assignedTo);
        }

        InvestigationStep savedStep = stepRepository.save(step);
        plan.setUpdatedAt(Instant.now());

        User currentUser = currentUserService.getCurrentUser();

        if (becameCompleted) {
            auditService.record(
                    AuditEventType.INVESTIGATION_STEP_COMPLETED,
                    "InvestigationStep",
                    String.valueOf(savedStep.getId()),
                    currentUser.getEmail(),
                    "Completed investigation step: " + savedStep.getTitle()
            );
        } else {
            auditService.record(
                    AuditEventType.INVESTIGATION_STEP_UPDATED,
                    "InvestigationStep",
                    String.valueOf(savedStep.getId()),
                    currentUser.getEmail(),
                    "Updated investigation step: " + savedStep.getTitle()
            );
        }

        return mapStepToResponse(savedStep);
    }

    // --- Helper Transactional Methods ---

    @Transactional(readOnly = true)
    protected Incident loadIncident(UUID incidentId) {
        return incidentRepository.findById(incidentId)
                .filter(inc -> !inc.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Incident", incidentId));
    }

    @Transactional(readOnly = true)
    protected AiDiagnosis loadDiagnosis(UUID diagnosisId, UUID incidentId) {
        AiDiagnosis diagnosis = diagnosisRepository.findById(diagnosisId)
                .orElseThrow(() -> new ResourceNotFoundException("AiDiagnosis", diagnosisId));

        if (!diagnosis.getIncident().getId().equals(incidentId)) {
            throw new BadRequestException("Diagnosis does not belong to the specified incident");
        }

        return diagnosis;
    }

    @Transactional
    protected InvestigationPlan persistGeneratedPlan(
            Incident incident,
            AiDiagnosis diagnosis,
            InvestigationPlanAiResponse aiResponse,
            User currentUser) {

        String title = aiResponse.title() != null && !aiResponse.title().isBlank()
                ? aiResponse.title()
                : "Investigation Plan: " + incident.getTitle();

        InvestigationPlan plan = new InvestigationPlan(incident, diagnosis, title, currentUser);

        int order = 1;
        for (InvestigationPlanAiResponse.StepAiResponse stepAi : aiResponse.steps()) {
            InvestigationStep step = new InvestigationStep(
                    plan,
                    order++,
                    stepAi.title(),
                    stepAi.description()
            );
            plan.addStep(step);
        }

        return planRepository.save(plan);
    }

    // --- Response Mapping Helpers ---

    private InvestigationPlanResponse mapToResponse(InvestigationPlan plan) {
        List<InvestigationStepResponse> stepResponses = plan.getSteps().stream()
                .map(this::mapStepToResponse)
                .collect(Collectors.toList());

        UUID sourceDiagnosisId = plan.getSourceDiagnosis() != null ? plan.getSourceDiagnosis().getId() : null;

        return new InvestigationPlanResponse(
                plan.getId(),
                plan.getIncident().getId(),
                sourceDiagnosisId,
                plan.getTitle(),
                mapUser(plan.getCreatedBy()),
                stepResponses,
                plan.getCreatedAt(),
                plan.getUpdatedAt()
        );
    }

    private InvestigationStepResponse mapStepToResponse(InvestigationStep step) {
        return new InvestigationStepResponse(
                step.getId(),
                step.getStepOrder(),
                step.getTitle(),
                step.getDescription(),
                step.getStatus(),
                step.getEvidence(),
                mapUser(step.getAssignedTo()),
                step.getCompletedAt(),
                step.getCreatedAt(),
                step.getUpdatedAt()
        );
    }

    private UserDto mapUser(User user) {
        if (user == null) {
            return null;
        }
        return new UserDto(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getRole().name()
        );
    }
}
