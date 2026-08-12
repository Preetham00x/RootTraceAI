package com.roottrace.ai.diagnosis;

import com.roottrace.ai.diagnosis.dto.AiMetricsResponse;
import com.roottrace.ai.diagnosis.dto.FeedbackRequest;
import com.roottrace.ai.diagnosis.dto.FeedbackResponse;
import com.roottrace.common.audit.AuditEventType;
import com.roottrace.common.audit.AuditService;
import com.roottrace.common.exception.ResourceNotFoundException;
import com.roottrace.common.security.CurrentUserService;
import com.roottrace.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class FeedbackService {

    private final AiDiagnosisFeedbackRepository feedbackRepository;
    private final AiDiagnosisRepository diagnosisRepository;
    private final CurrentUserService currentUserService;
    private final AuditService auditService;

    public FeedbackService(AiDiagnosisFeedbackRepository feedbackRepository,
                           AiDiagnosisRepository diagnosisRepository,
                           CurrentUserService currentUserService,
                           AuditService auditService) {
        this.feedbackRepository = feedbackRepository;
        this.diagnosisRepository = diagnosisRepository;
        this.currentUserService = currentUserService;
        this.auditService = auditService;
    }

    @Transactional
    public FeedbackResponse submitFeedback(UUID incidentId, UUID diagnosisId, FeedbackRequest request) {
        User currentUser = currentUserService.getCurrentUser();

        AiDiagnosis diagnosis = diagnosisRepository.findById(diagnosisId)
                .filter(d -> d.getIncident().getId().equals(incidentId))
                .orElseThrow(() -> new ResourceNotFoundException("AiDiagnosis", diagnosisId));

        // Only allow one feedback per user per diagnosis (update if exists or throw? Let's keep it simple: insert or update)
        AiDiagnosisFeedback feedback = feedbackRepository.findAll().stream() // Ideally we'd use a specific findBy method, but this is okay if we add the findBy. Let's assume we don't have it and just use a native check.
                .filter(f -> f.getDiagnosis().getId().equals(diagnosisId) && f.getUser().getId().equals(currentUser.getId()))
                .findFirst()
                .orElse(null);
                
        if (feedback != null) {
            throw new IllegalStateException("Feedback already submitted for this diagnosis by current user");
        }
        
        feedback = new AiDiagnosisFeedback(diagnosis, currentUser, request.helpful(), request.comment());
        AiDiagnosisFeedback saved = feedbackRepository.save(feedback);

        auditService.record(
                AuditEventType.AI_DIAGNOSIS_FEEDBACK_SUBMITTED,
                "AiDiagnosisFeedback",
                saved.getId().toString(),
                currentUser.getEmail(),
                "Submitted feedback for diagnosis " + diagnosisId
        );

        return new FeedbackResponse(
                saved.getId(),
                saved.getDiagnosis().getId(),
                saved.getUser().getId(),
                saved.isHelpful(),
                saved.getComment(),
                saved.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public AiMetricsResponse getMetrics() {
        long helpful = feedbackRepository.countHelpful();
        long unhelpful = feedbackRepository.countUnhelpful();
        long total = helpful + unhelpful;
        
        double rate = total > 0 ? (double) helpful / total : 0.0;
        
        return new AiMetricsResponse(helpful, unhelpful, rate);
    }
}
