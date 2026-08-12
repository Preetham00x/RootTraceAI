package com.roottrace.intelligence;

import com.roottrace.ai.diagnosis.AiDiagnosis;
import com.roottrace.ai.diagnosis.AiDiagnosisRepository;
import com.roottrace.common.exception.BadRequestException;
import com.roottrace.incident.Incident;
import com.roottrace.incident.IncidentRepository;
import com.roottrace.incident.IncidentStatus;
import com.roottrace.intelligence.dto.IncidentClusterResponse;
import com.roottrace.intelligence.dto.IncidentClustersResponse;
import com.roottrace.postmortem.ActionItemStatus;
import com.roottrace.postmortem.Postmortem;
import com.roottrace.postmortem.PostmortemActionItem;
import com.roottrace.postmortem.PostmortemRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class IncidentClusteringService {

    private final IncidentRepository incidentRepository;
    private final AiDiagnosisRepository diagnosisRepository;
    private final PostmortemRepository postmortemRepository;

    public IncidentClusteringService(
            IncidentRepository incidentRepository,
            AiDiagnosisRepository diagnosisRepository,
            PostmortemRepository postmortemRepository) {
        this.incidentRepository = incidentRepository;
        this.diagnosisRepository = diagnosisRepository;
        this.postmortemRepository = postmortemRepository;
    }

    @Transactional(readOnly = true)
    public IncidentClustersResponse findClusters(String serviceFilter, Integer minClusterSize) {
        int minSize = (minClusterSize != null) ? minClusterSize : 2;
        if (minSize < 1) {
            throw new BadRequestException("minClusterSize must be >= 1. Provided: " + minSize);
        }

        List<Incident> allIncidents = incidentRepository.findAllNotDeleted(Pageable.unpaged()).getContent();

        if (serviceFilter != null && !serviceFilter.isBlank()) {
            allIncidents = allIncidents.stream()
                    .filter(i -> i.getService() != null && i.getService().equalsIgnoreCase(serviceFilter.trim()))
                    .collect(Collectors.toList());
        }

        if (allIncidents.isEmpty()) {
            return new IncidentClustersResponse(0, Collections.emptyList());
        }

        // Group incidents by service first
        Map<String, List<Incident>> byService = allIncidents.stream()
                .filter(i -> i.getService() != null)
                .collect(Collectors.groupingBy(i -> i.getService().toLowerCase(Locale.ROOT)));

        List<IncidentClusterResponse> clusters = new ArrayList<>();

        for (Map.Entry<String, List<Incident>> entry : byService.entrySet()) {
            String serviceName = entry.getKey();
            List<Incident> serviceIncidents = entry.getValue();

            // Group service incidents by normalized failure pattern
            Map<String, List<Incident>> groupedByPattern = new HashMap<>();

            for (Incident inc : serviceIncidents) {
                String patternKey = extractFailurePattern(inc);
                groupedByPattern.computeIfAbsent(patternKey, k -> new ArrayList<>()).add(inc);
            }

            for (Map.Entry<String, List<Incident>> patternEntry : groupedByPattern.entrySet()) {
                String pattern = patternEntry.getKey();
                List<Incident> clusterIncidents = patternEntry.getValue();

                if (clusterIncidents.size() < minSize) {
                    continue;
                }

                String clusterId = "cluster-" + slugify(serviceName) + "-" + slugify(pattern);
                String title = formatClusterTitle(serviceName, pattern);

                // Compute cluster statistics
                Instant latestAt = clusterIncidents.stream()
                        .map(Incident::getCreatedAt)
                        .filter(i -> i != null)
                        .max(Comparator.naturalOrder())
                        .orElse(Instant.now());

                List<Long> mttrList = clusterIncidents.stream()
                        .filter(i -> (i.getStatus() == IncidentStatus.RESOLVED || i.getStatus() == IncidentStatus.CLOSED)
                                && i.getResolvedAt() != null && i.getCreatedAt() != null)
                        .map(i -> Duration.between(i.getCreatedAt(), i.getResolvedAt()).toMinutes())
                        .collect(Collectors.toList());

                Double avgMttr = mttrList.isEmpty() ? null :
                        Math.round((mttrList.stream().mapToDouble(Long::doubleValue).average().orElse(0.0)) * 10.0) / 10.0;

                List<UUID> sampleIds = clusterIncidents.stream()
                        .map(Incident::getId)
                        .limit(10)
                        .collect(Collectors.toList());

                String primaryRootCause = resolvePrimaryRootCause(clusterIncidents, pattern);
                boolean hasOpenActionItems = checkForOpenActionItems(sampleIds);

                clusters.add(new IncidentClusterResponse(
                        clusterId,
                        serviceName,
                        title,
                        clusterIncidents.size(),
                        latestAt,
                        avgMttr,
                        sampleIds,
                        primaryRootCause,
                        hasOpenActionItems
                ));
            }
        }

        clusters.sort(Comparator.comparing(IncidentClusterResponse::incidentCount).reversed()
                .thenComparing(IncidentClusterResponse::latestIncidentAt, Comparator.reverseOrder()));

        return new IncidentClustersResponse(clusters.size(), clusters);
    }

    private String extractFailurePattern(Incident incident) {
        // Try getting root cause from diagnosis
        List<AiDiagnosis> diagnoses = diagnosisRepository.findByIncidentIdOrderByCreatedAtDesc(incident.getId());
        if (!diagnoses.isEmpty() && diagnoses.get(0).getProbableRootCause() != null) {
            String rc = diagnoses.get(0).getProbableRootCause().toLowerCase(Locale.ROOT);
            return categorizeText(rc);
        }

        // Fallback to title and description keywords
        String combined = ((incident.getTitle() != null ? incident.getTitle() : "") + " "
                + (incident.getDescription() != null ? incident.getDescription() : "")).toLowerCase(Locale.ROOT);

        return categorizeText(combined);
    }

    private String categorizeText(String text) {
        if (text.contains("pool") || text.contains("connection") || text.contains("hikari") || text.contains("socket")) {
            return "connection-pool-exhaustion";
        }
        if (text.contains("timeout") || text.contains("504") || text.contains("latency") || text.contains("slow")) {
            return "request-timeout-latency";
        }
        if (text.contains("memory") || text.contains("oom") || text.contains("heap") || text.contains("garbage collection")) {
            return "memory-exhaustion-oom";
        }
        if (text.contains("cpu") || text.contains("spike") || text.contains("thread")) {
            return "cpu-saturation";
        }
        if (text.contains("auth") || text.contains("jwt") || text.contains("token") || text.contains("permission") || text.contains("401") || text.contains("403")) {
            return "auth-failure";
        }
        if (text.contains("disk") || text.contains("storage") || text.contains("space") || text.contains("full")) {
            return "disk-exhaustion";
        }
        if (text.contains("deadlock") || text.contains("lock") || text.contains("transaction")) {
            return "database-deadlock";
        }
        if (text.contains("circuit") || text.contains("rate limit") || text.contains("429") || text.contains("throttle")) {
            return "rate-limiting-circuit-breaker";
        }
        if (text.contains("cache") || text.contains("redis")) {
            return "cache-invalidation";
        }

        return "operational-failure";
    }

    private String formatClusterTitle(String service, String pattern) {
        String friendlyPattern = switch (pattern) {
            case "connection-pool-exhaustion" -> "Connection Pool Exhaustion";
            case "request-timeout-latency" -> "Request Latency & Gateway Timeouts";
            case "memory-exhaustion-oom" -> "Memory Exhaustion / OOM";
            case "cpu-saturation" -> "CPU Saturation & Thread Starvation";
            case "auth-failure" -> "Authentication & Authorization Failures";
            case "disk-exhaustion" -> "Disk Space Full";
            case "database-deadlock" -> "Database Lock Contention";
            case "rate-limiting-circuit-breaker" -> "Rate Limiting & Circuit Breakers";
            case "cache-invalidation" -> "Cache Invalidation & Miss Spike";
            default -> "Recurring Operational Issues";
        };
        return service + ": " + friendlyPattern;
    }

    private String resolvePrimaryRootCause(List<Incident> clusterIncidents, String pattern) {
        for (Incident inc : clusterIncidents) {
            List<AiDiagnosis> diagnoses = diagnosisRepository.findByIncidentIdOrderByCreatedAtDesc(inc.getId());
            if (!diagnoses.isEmpty() && diagnoses.get(0).getProbableRootCause() != null
                    && !diagnoses.get(0).getProbableRootCause().isBlank()) {
                return diagnoses.get(0).getProbableRootCause();
            }
        }
        return "Recurring " + pattern.replace('-', ' ') + " observed in service telemetry.";
    }

    private boolean checkForOpenActionItems(List<UUID> incidentIds) {
        for (UUID incId : incidentIds) {
            var postmortemOpt = postmortemRepository.findByIncidentIdWithActionItems(incId);
            if (postmortemOpt.isPresent()) {
                Postmortem pm = postmortemOpt.get();
                if (pm.getActionItems() != null) {
                    for (PostmortemActionItem item : pm.getActionItems()) {
                        if (item.getStatus() == ActionItemStatus.OPEN || item.getStatus() == ActionItemStatus.IN_PROGRESS) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private String slugify(String text) {
        if (text == null) return "general";
        return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
    }
}
