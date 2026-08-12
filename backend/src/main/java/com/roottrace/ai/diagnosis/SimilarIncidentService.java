package com.roottrace.ai.diagnosis;

import com.roottrace.ai.config.AiProperties;
import com.roottrace.ai.embedding.AiEmbeddingService;
import com.roottrace.common.audit.AuditEventType;
import com.roottrace.common.audit.AuditService;
import com.roottrace.common.exception.ResourceNotFoundException;
import com.roottrace.common.security.CurrentUserService;
import com.roottrace.incident.Incident;
import com.roottrace.incident.IncidentRepository;
import com.roottrace.incident.IncidentSeverity;
import com.roottrace.incident.IncidentStatus;
import com.roottrace.incident.dto.SimilarIncidentResponse;
import com.roottrace.knowledge.retrieval.QueryPreparationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class SimilarIncidentService {

    private static final Logger logger = LoggerFactory.getLogger(SimilarIncidentService.class);

    private final IncidentRepository incidentRepository;
    private final AiEmbeddingService embeddingService;
    private final QueryPreparationService queryPrepService;
    private final AuditService auditService;
    private final CurrentUserService currentUserService;
    private final AiProperties aiProperties;

    public SimilarIncidentService(IncidentRepository incidentRepository,
                                  AiEmbeddingService embeddingService,
                                  QueryPreparationService queryPrepService,
                                  AuditService auditService,
                                  CurrentUserService currentUserService,
                                  AiProperties aiProperties) {
        this.incidentRepository = incidentRepository;
        this.embeddingService = embeddingService;
        this.queryPrepService = queryPrepService;
        this.auditService = auditService;
        this.currentUserService = currentUserService;
        this.aiProperties = aiProperties;
    }

    /**
     * Finds similar incidents based on vector embeddings.
     * Generates a dynamic embedding for the target incident if it doesn't have one saved,
     * and persists it back to the database so it can be indexed.
     */
    @Transactional
    public List<SimilarIncidentResponse> findSimilar(UUID incidentId, Integer limit) {
        Incident incident = incidentRepository.findById(incidentId)
                .filter(i -> !i.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Incident", incidentId));

        float[] targetEmbedding = incident.getEmbedding();

        // If incident embedding is missing, generate it on the fly and persist it
        if (targetEmbedding == null) {
            String queryStr = queryPrepService.buildQuery(
                    incident.getTitle(),
                    incident.getDescription(),
                    incident.getService(),
                    incident.getEnvironment(),
                    null
            );
            targetEmbedding = embeddingService.embed(queryStr);
            incident.setEmbedding(targetEmbedding);
            incidentRepository.save(incident);
        }

        String vectorString = toVectorString(targetEmbedding);
        int maxResults = limit != null && limit > 0 ? limit : aiProperties.getRetrieval().getTopK();

        List<Object[]> rows = incidentRepository.findSimilarIncidents(incidentId, vectorString, maxResults);
        
        List<SimilarIncidentResponse> results = new ArrayList<>();
        for (Object[] row : rows) {
            results.add(mapRow(row));
        }

        auditService.record(
                AuditEventType.AI_SIMILAR_INCIDENT_SEARCHED,
                "Incident",
                incidentId.toString(),
                currentUserService.getCurrentUser().getEmail(),
                "Searched for similar incidents, found " + results.size()
        );

        return results;
    }

    private SimilarIncidentResponse mapRow(Object[] row) {
        return new SimilarIncidentResponse(
                UUID.fromString(row[0].toString()),
                row[1] != null ? row[1].toString() : "",
                row[2] != null ? row[2].toString() : "",
                row[3] != null ? IncidentSeverity.valueOf(row[3].toString()) : null,
                row[4] != null ? IncidentStatus.valueOf(row[4].toString()) : null,
                row[5] != null ? row[5].toString() : "",
                row[6] != null ? ((Timestamp) row[6]).toInstant() : null,
                row[7] != null ? ((Timestamp) row[7]).toInstant() : null,
                row[8] != null ? row[8].toString() : null,
                row[9] != null ? ((Number) row[9]).doubleValue() : 0.0
        );
    }

    private String toVectorString(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(embedding[i]);
        }
        sb.append(']');
        return sb.toString();
    }
}
