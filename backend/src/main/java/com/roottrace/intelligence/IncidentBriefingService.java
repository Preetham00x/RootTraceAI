package com.roottrace.intelligence;

import com.roottrace.ai.diagnosis.AiDiagnosis;
import com.roottrace.ai.diagnosis.AiDiagnosisRepository;
import com.roottrace.common.audit.AuditEventType;
import com.roottrace.common.audit.AuditService;
import com.roottrace.common.exception.ResourceNotFoundException;
import com.roottrace.common.security.CurrentUserService;
import com.roottrace.incident.Incident;
import com.roottrace.incident.IncidentRepository;
import com.roottrace.intelligence.dto.CorrelatedIncidentResponse;
import com.roottrace.intelligence.dto.IncidentBriefingAiResponse;
import com.roottrace.intelligence.dto.IncidentBriefingResponse;
import com.roottrace.intelligence.dto.RelatedIncidentsResponse;
import com.roottrace.investigation.InvestigationPlan;
import com.roottrace.investigation.InvestigationPlanRepository;
import com.roottrace.postmortem.Postmortem;
import com.roottrace.postmortem.PostmortemRepository;
import com.roottrace.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
public class IncidentBriefingService {

    private final IncidentRepository incidentRepository;
    private final IncidentCorrelationService correlationService;
    private final AiDiagnosisRepository diagnosisRepository;
    private final InvestigationPlanRepository investigationPlanRepository;
    private final PostmortemRepository postmortemRepository;
    private final GeminiBriefingService geminiBriefingService;
    private final CurrentUserService currentUserService;
    private final AuditService auditService;

    public IncidentBriefingService(
            IncidentRepository incidentRepository,
            IncidentCorrelationService correlationService,
            AiDiagnosisRepository diagnosisRepository,
            InvestigationPlanRepository investigationPlanRepository,
            PostmortemRepository postmortemRepository,
            GeminiBriefingService geminiBriefingService,
            CurrentUserService currentUserService,
            AuditService auditService) {
        this.incidentRepository = incidentRepository;
        this.correlationService = correlationService;
        this.diagnosisRepository = diagnosisRepository;
        this.investigationPlanRepository = investigationPlanRepository;
        this.postmortemRepository = postmortemRepository;
        this.geminiBriefingService = geminiBriefingService;
        this.currentUserService = currentUserService;
        this.auditService = auditService;
    }

    /**
     * Generates a comprehensive, AI-assisted SRE incident intelligence briefing.
     * Follows the transaction separation pattern: Load data (Read TX) -> Gemini call outside TX -> Audit.
     */
    public IncidentBriefingResponse generateBriefing(UUID incidentId) {
        User currentUser = currentUserService.getCurrentUser();

        // 1. Read Phase
        Incident targetIncident = loadTargetIncident(incidentId);

        RelatedIncidentsResponse relatedResponse = correlationService.findRelatedIncidents(
                incidentId, 5, 0.50, false
        );

        List<CorrelatedIncidentResponse> correlated = relatedResponse.relatedIncidents();
        List<AiDiagnosis> historicalDiagnoses = new ArrayList<>();
        List<InvestigationPlan> historicalPlans = new ArrayList<>();
        List<Postmortem> historicalPostmortems = new ArrayList<>();

        for (CorrelatedIncidentResponse r : correlated) {
            List<AiDiagnosis> diagnoses = diagnosisRepository.findByIncidentIdOrderByCreatedAtDesc(r.id());
            if (!diagnoses.isEmpty()) {
                historicalDiagnoses.add(diagnoses.get(0));
            }

            List<InvestigationPlan> plans = investigationPlanRepository.findByIncidentIdWithSteps(r.id());
            historicalPlans.addAll(plans);

            postmortemRepository.findByIncidentIdWithActionItems(r.id())
                    .ifPresent(historicalPostmortems::add);
        }

        // 2. AI Phase (Outside TX)
        IncidentBriefingAiResponse aiResponse = geminiBriefingService.generateBriefing(
                targetIncident,
                correlated,
                historicalDiagnoses,
                historicalPlans,
                historicalPostmortems
        );

        // 3. Audit Phase
        auditService.record(
                AuditEventType.AI_INCIDENT_BRIEFING_GENERATED,
                "Incident",
                incidentId.toString(),
                currentUser.getEmail(),
                "Generated AI SRE Intelligence briefing with " + correlated.size() + " correlated incidents"
        );

        return new IncidentBriefingResponse(
                incidentId,
                aiResponse.executiveSummary(),
                Boolean.TRUE.equals(aiResponse.isRecurringIssue()),
                aiResponse.recurrenceCount() != null ? aiResponse.recurrenceCount() : correlated.size(),
                aiResponse.recommendedTriageActions() != null ? aiResponse.recommendedTriageActions() : Collections.emptyList(),
                aiResponse.historicalRootCauses() != null ? aiResponse.historicalRootCauses() : Collections.emptyList(),
                aiResponse.provenInvestigationSteps() != null ? aiResponse.provenInvestigationSteps() : Collections.emptyList(),
                aiResponse.pastPostmortemLessons() != null ? aiResponse.pastPostmortemLessons() : Collections.emptyList(),
                aiResponse.uncompletedActionItems() != null ? aiResponse.uncompletedActionItems() : Collections.emptyList(),
                correlated,
                Instant.now()
        );
    }

    @Transactional(readOnly = true)
    protected Incident loadTargetIncident(UUID incidentId) {
        return incidentRepository.findById(incidentId)
                .filter(i -> !i.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Incident", incidentId));
    }
}
