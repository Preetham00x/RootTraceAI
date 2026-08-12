package com.roottrace.intelligence;

import com.roottrace.ai.diagnosis.SimilarIncidentService;
import com.roottrace.common.exception.BadRequestException;
import com.roottrace.common.exception.ResourceNotFoundException;
import com.roottrace.incident.Incident;
import com.roottrace.incident.IncidentRepository;
import com.roottrace.incident.dto.SimilarIncidentResponse;
import com.roottrace.intelligence.dto.CorrelatedIncidentResponse;
import com.roottrace.intelligence.dto.RelatedIncidentsResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class IncidentCorrelationService {

    private static final double WEIGHT_SEMANTIC = 0.60;
    private static final double WEIGHT_SERVICE = 0.25;
    private static final double WEIGHT_TEMPORAL = 0.15;
    private static final double DUPLICATE_THRESHOLD = 0.88;

    private final IncidentRepository incidentRepository;
    private final SimilarIncidentService similarIncidentService;

    public IncidentCorrelationService(
            IncidentRepository incidentRepository,
            SimilarIncidentService similarIncidentService) {
        this.incidentRepository = incidentRepository;
        this.similarIncidentService = similarIncidentService;
    }

    @Transactional(readOnly = true)
    public RelatedIncidentsResponse findRelatedIncidents(
            UUID incidentId,
            Integer limit,
            Double threshold,
            Boolean sameServiceOnly) {

        int maxLimit = (limit != null) ? limit : 5;
        if (maxLimit < 1 || maxLimit > 20) {
            throw new BadRequestException("Limit must be between 1 and 20. Provided: " + maxLimit);
        }

        double minThreshold = (threshold != null) ? threshold : 0.60;
        if (minThreshold < 0.0 || minThreshold > 1.0) {
            throw new BadRequestException("Threshold must be between 0.0 and 1.0. Provided: " + minThreshold);
        }

        boolean filterSameService = Boolean.TRUE.equals(sameServiceOnly);

        Incident target = incidentRepository.findById(incidentId)
                .filter(i -> !i.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Incident", incidentId));

        // Fetch candidates (over-fetch slightly to allow for attribute & threshold filtering)
        int fetchLimit = Math.min(maxLimit * 3, 50);
        List<SimilarIncidentResponse> candidates = similarIncidentService.findSimilar(incidentId, fetchLimit);

        List<CorrelatedIncidentResponse> correlatedList = new ArrayList<>();
        boolean hasDuplicates = false;

        for (SimilarIncidentResponse candidate : candidates) {
            boolean isSameService = target.getService() != null
                    && target.getService().equalsIgnoreCase(candidate.service());

            if (filterSameService && !isSameService) {
                continue;
            }

            double semanticSim = candidate.similarityScore();
            double serviceScore = isSameService ? 1.0 : 0.0;

            double temporalDistanceHours = 720.0;
            double temporalScore = 0.15;

            if (target.getCreatedAt() != null && candidate.createdAt() != null) {
                temporalDistanceHours = Math.abs(Duration.between(target.getCreatedAt(), candidate.createdAt()).toMinutes()) / 60.0;
                if (temporalDistanceHours <= 2.0) {
                    temporalScore = 1.0;
                } else if (temporalDistanceHours <= 24.0) {
                    temporalScore = 0.85;
                } else if (temporalDistanceHours <= 168.0) { // 7 days
                    temporalScore = 0.60;
                } else if (temporalDistanceHours <= 720.0) { // 30 days
                    temporalScore = 0.35;
                } else {
                    temporalScore = 0.15;
                }
            }

            double compositeScore = (semanticSim * WEIGHT_SEMANTIC)
                    + (serviceScore * WEIGHT_SERVICE)
                    + (temporalScore * WEIGHT_TEMPORAL);

            // Round composite score to 4 decimal places
            compositeScore = Math.round(compositeScore * 10000.0) / 10000.0;

            if (compositeScore < minThreshold) {
                continue;
            }

            boolean isDuplicate = compositeScore >= DUPLICATE_THRESHOLD;
            if (isDuplicate) {
                hasDuplicates = true;
            }

            String correlationReason = buildCorrelationReason(
                    semanticSim, isSameService, temporalDistanceHours, isDuplicate
            );

            correlatedList.add(new CorrelatedIncidentResponse(
                    candidate.id(),
                    candidate.title(),
                    candidate.service(),
                    candidate.severity(),
                    candidate.status(),
                    candidate.createdAt(),
                    candidate.resolvedAt(),
                    candidate.resolution(),
                    Math.round(semanticSim * 10000.0) / 10000.0,
                    isSameService,
                    Math.round(temporalDistanceHours * 100.0) / 100.0,
                    compositeScore,
                    isDuplicate,
                    correlationReason
            ));
        }

        // Sort by composite score descending
        correlatedList.sort(Comparator.comparing(CorrelatedIncidentResponse::compositeScore).reversed());

        if (correlatedList.size() > maxLimit) {
            correlatedList = correlatedList.subList(0, maxLimit);
        }

        return new RelatedIncidentsResponse(
                incidentId,
                correlatedList.size(),
                hasDuplicates,
                correlatedList
        );
    }

    private String buildCorrelationReason(
            double semanticSimilarity,
            boolean isSameService,
            double temporalDistanceHours,
            boolean isDuplicate) {

        long semPct = Math.round(semanticSimilarity * 100);

        if (isDuplicate) {
            return String.format("Potential duplicate: %d%% semantic similarity with matching service and temporal proximity (%.1fh).",
                    semPct, temporalDistanceHours);
        }

        if (isSameService && temporalDistanceHours <= 24.0) {
            return String.format("Same service with %d%% semantic match occurring within %.1f hours.",
                    semPct, temporalDistanceHours);
        }

        if (isSameService) {
            return String.format("Same service recurrence with %d%% semantic similarity.", semPct);
        }

        if (temporalDistanceHours <= 24.0) {
            return String.format("Cross-service correlation (%d%% match) within %.1f hours.",
                    semPct, temporalDistanceHours);
        }

        return String.format("Semantic similarity match (%d%%).", semPct);
    }
}
