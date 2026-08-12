package com.roottrace.ai.diagnosis;

import com.roottrace.ai.config.AiProperties;
import com.roottrace.ai.diagnosis.dto.DiagnosisCitationResponse;
import com.roottrace.ai.diagnosis.dto.DiagnosisDetailResponse;
import com.roottrace.ai.diagnosis.dto.DiagnosisEvidenceResponse;
import com.roottrace.ai.diagnosis.dto.DiagnosisResultResponse;
import com.roottrace.ai.diagnosis.dto.DiagnosisSummaryResponse;
import com.roottrace.common.audit.AuditService;
import com.roottrace.common.audit.AuditEventType;
import com.roottrace.common.exception.ResourceNotFoundException;
import com.roottrace.common.security.CurrentUserService;
import com.roottrace.incident.Incident;
import com.roottrace.incident.IncidentRepository;
import com.roottrace.knowledge.KnowledgeChunk;
import com.roottrace.knowledge.KnowledgeChunkRepository;
import com.roottrace.knowledge.KnowledgeDocument;
import com.roottrace.knowledge.KnowledgeDocumentRepository;
import com.roottrace.knowledge.retrieval.HybridRetrievalService;
import com.roottrace.knowledge.retrieval.QueryPreparationService;
import com.roottrace.knowledge.retrieval.RetrievalQuery;
import com.roottrace.knowledge.retrieval.RetrievalResult;
import com.roottrace.user.User;
import com.roottrace.user.dto.UserDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class GeminiDiagnosisService implements DiagnosisService {

    private static final Logger logger = LoggerFactory.getLogger(GeminiDiagnosisService.class);

    private final IncidentRepository incidentRepository;
    private final AiDiagnosisRepository diagnosisRepository;
    private final KnowledgeChunkRepository chunkRepository;
    private final KnowledgeDocumentRepository documentRepository;
    private final QueryPreparationService queryPrepService;
    private final HybridRetrievalService retrievalService;
    private final DiagnosisContextBuilder contextBuilder;
    private final DiagnosisPromptBuilder promptBuilder;
    private final ChatClient chatClient;
    private final CurrentUserService currentUserService;
    private final AuditService auditService;
    private final AiProperties aiProperties;

    public GeminiDiagnosisService(
            IncidentRepository incidentRepository,
            AiDiagnosisRepository diagnosisRepository,
            KnowledgeChunkRepository chunkRepository,
            KnowledgeDocumentRepository documentRepository,
            QueryPreparationService queryPrepService,
            HybridRetrievalService retrievalService,
            DiagnosisContextBuilder contextBuilder,
            DiagnosisPromptBuilder promptBuilder,
            @Autowired(required = false) ChatClient chatClient,
            CurrentUserService currentUserService,
            AuditService auditService,
            AiProperties aiProperties) {
        this.incidentRepository = incidentRepository;
        this.diagnosisRepository = diagnosisRepository;
        this.chunkRepository = chunkRepository;
        this.documentRepository = documentRepository;
        this.queryPrepService = queryPrepService;
        this.retrievalService = retrievalService;
        this.contextBuilder = contextBuilder;
        this.promptBuilder = promptBuilder;
        this.chatClient = chatClient;
        this.currentUserService = currentUserService;
        this.auditService = auditService;
        this.aiProperties = aiProperties;
    }

    @Override
    public DiagnosisResultResponse diagnose(UUID incidentId) {
        if (chatClient == null) {
            throw new DiagnosisException("AI chat client is not configured");
        }

        User currentUser = currentUserService.getCurrentUser();
        
        // 1. TX1: Load incident
        Incident incident = loadIncident(incidentId);

        // 2. NO TX: Build query -> hybrid retrieval -> AI generation
        RetrievalResult retrievalResult = performRetrieval(incident);
        
        if (retrievalResult.results().isEmpty()) {
            throw new InsufficientEvidenceException("No relevant knowledge found to diagnose this incident.");
        }

        DiagnosisAiResponse aiResponse = generateDiagnosis(incident, retrievalResult);

        // 3. TX2: Persist diagnosis
        AiDiagnosis savedDiagnosis = persistDiagnosis(incident, aiResponse, currentUser);

        // 4. Audit
        auditService.record(
                AuditEventType.AI_DIAGNOSIS_CREATED,
                "AiDiagnosis",
                savedDiagnosis.getId().toString(),
                currentUser.getEmail(),
                "Generated diagnosis for incident " + incidentId
        );

        return mapToResultResponse(savedDiagnosis);
    }

    @Transactional(readOnly = true)
    protected Incident loadIncident(UUID incidentId) {
        return incidentRepository.findById(incidentId)
                .filter(inc -> !inc.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Incident", incidentId));
    }

    private RetrievalResult performRetrieval(Incident incident) {
        String queryStr = queryPrepService.buildQuery(
                incident.getTitle(),
                incident.getDescription(),
                incident.getService(),
                incident.getEnvironment(),
                null // errorMessage is typically in description
        );
        
        // Use configured topK for retrieval
        int topK = aiProperties.getRetrieval().getTopK();
        RetrievalQuery query = new RetrievalQuery(queryStr, topK, incident.getService(), incident.getEnvironment());
        
        try {
            return retrievalService.retrieve(query);
        } catch (Exception e) {
            throw new DiagnosisException("Knowledge retrieval failed during diagnosis: " + e.getMessage(), e);
        }
    }

    private DiagnosisAiResponse generateDiagnosis(Incident incident, RetrievalResult retrievalResult) {
        int maxChunks = aiProperties.getDiagnosis().getMaxContextChunks();
        String context = contextBuilder.buildContext(retrievalResult, maxChunks);
        
        BeanOutputConverter<DiagnosisAiResponse> converter = new BeanOutputConverter<>(DiagnosisAiResponse.class);
        String format = converter.getFormat();
        
        String prompt = promptBuilder.buildPrompt(incident, context, format);
        
        logger.info("Calling Gemini for diagnosis of incident {}", incident.getId());
        
        try {
            DiagnosisAiResponse response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .entity(converter);
            
            if (response == null) {
                throw new DiagnosisException("AI returned an empty or invalid response");
            }
            return response;
        } catch (Exception e) {
            logger.error("Gemini call failed: {}", e.getMessage(), e);
            throw new DiagnosisException("Failed to generate diagnosis via AI: " + e.getMessage(), e);
        }
    }

    @Transactional
    protected AiDiagnosis persistDiagnosis(Incident incident, DiagnosisAiResponse aiResponse, User currentUser) {
        // Must reload incident inside TX if we want to attach to it, or merge
        Incident managedIncident = incidentRepository.findById(incident.getId()).orElseThrow();
        
        AiDiagnosis diagnosis = new AiDiagnosis(
                managedIncident,
                aiResponse.summary(),
                aiResponse.probableRootCause(),
                aiResponse.clampedConfidence(),
                aiResponse.contributingFactors(),
                aiResponse.recommendedActions(),
                currentUser
        );
        
        if (aiResponse.evidence() != null) {
            for (DiagnosisAiResponse.EvidenceItem item : aiResponse.evidence()) {
                if (item.chunkId() != null) {
                    try {
                        UUID chunkId = UUID.fromString(item.chunkId());
                        KnowledgeChunk chunk = chunkRepository.findById(chunkId).orElse(null);
                        if (chunk != null) {
                            AiDiagnosisEvidence ev = new AiDiagnosisEvidence(chunk, null, item.reason());
                            diagnosis.addEvidence(ev);
                        } else {
                            logger.warn("AI cited missing chunk ID: {}", item.chunkId());
                        }
                    } catch (IllegalArgumentException e) {
                        logger.warn("AI cited invalid chunk UUID: {}", item.chunkId());
                    }
                }
            }
        }
        
        if (aiResponse.citations() != null) {
            for (DiagnosisAiResponse.CitationItem item : aiResponse.citations()) {
                if (item.documentId() != null) {
                    try {
                        UUID docId = UUID.fromString(item.documentId());
                        KnowledgeDocument doc = documentRepository.findById(docId).orElse(null);
                        if (doc != null) {
                            KnowledgeChunk associatedChunk = null;
                            if (diagnosis.getEvidence().size() > 0) {
                                // Try to map citation to an evidence chunk if we didn't get it explicitly
                                // This is a simplification.
                            }
                            AiDiagnosisCitation cit = new AiDiagnosisCitation(doc, null, item.documentTitle(), item.sectionPath());
                            diagnosis.addCitation(cit);
                        } else {
                            logger.warn("AI cited missing doc ID: {}", item.documentId());
                        }
                    } catch (IllegalArgumentException e) {
                        logger.warn("AI cited invalid doc UUID: {}", item.documentId());
                    }
                }
            }
        }
        
        return diagnosisRepository.save(diagnosis);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DiagnosisSummaryResponse> getDiagnoses(UUID incidentId) {
        if (!incidentRepository.existsById(incidentId)) {
            throw new ResourceNotFoundException("Incident", incidentId);
        }
        return diagnosisRepository.findByIncidentIdOrderByCreatedAtDesc(incidentId)
                .stream()
                .map(this::mapToSummary)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public DiagnosisDetailResponse getDiagnosis(UUID incidentId, UUID diagnosisId) {
        AiDiagnosis diagnosis = diagnosisRepository.findById(diagnosisId)
                .filter(d -> d.getIncident().getId().equals(incidentId))
                .orElseThrow(() -> new ResourceNotFoundException("AiDiagnosis", diagnosisId));
        return mapToDetailResponse(diagnosis);
    }

    private DiagnosisSummaryResponse mapToSummary(AiDiagnosis diagnosis) {
        return new DiagnosisSummaryResponse(
                diagnosis.getId(),
                diagnosis.getSummary(),
                diagnosis.getConfidence(),
                diagnosis.getCreatedAt()
        );
    }

    private DiagnosisResultResponse mapToResultResponse(AiDiagnosis diagnosis) {
        return new DiagnosisResultResponse(
                diagnosis.getId(),
                diagnosis.getIncident().getId(),
                diagnosis.getSummary(),
                diagnosis.getProbableRootCause(),
                diagnosis.getConfidence(),
                diagnosis.getContributingFactors(),
                diagnosis.getRecommendedActions(),
                diagnosis.getEvidence().stream().map(this::mapToEvidence).toList(),
                diagnosis.getCitations().stream().map(this::mapToCitation).toList(),
                mapUser(diagnosis.getCreatedBy()),
                diagnosis.getCreatedAt()
        );
    }

    private DiagnosisDetailResponse mapToDetailResponse(AiDiagnosis diagnosis) {
        return new DiagnosisDetailResponse(
                diagnosis.getId(),
                diagnosis.getIncident().getId(),
                diagnosis.getSummary(),
                diagnosis.getProbableRootCause(),
                diagnosis.getConfidence(),
                diagnosis.getContributingFactors(),
                diagnosis.getRecommendedActions(),
                diagnosis.getEvidence().stream().map(this::mapToEvidence).toList(),
                diagnosis.getCitations().stream().map(this::mapToCitation).toList(),
                mapUser(diagnosis.getCreatedBy()),
                diagnosis.getCreatedAt()
        );
    }

    private DiagnosisEvidenceResponse mapToEvidence(AiDiagnosisEvidence ev) {
        return new DiagnosisEvidenceResponse(
                ev.getChunk().getId(),
                ev.getRelevanceScore(),
                ev.getReason()
        );
    }

    private DiagnosisCitationResponse mapToCitation(AiDiagnosisCitation cit) {
        return new DiagnosisCitationResponse(
                cit.getDocument().getId(),
                cit.getDocumentTitle(),
                cit.getSectionPath()
        );
    }

    private UserDto mapUser(User user) {
        return new UserDto(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getRole().name()
        );
    }
}
