package com.roottrace.intelligence;

import com.roottrace.common.exception.ResourceNotFoundException;
import com.roottrace.incident.Incident;
import com.roottrace.incident.IncidentRepository;
import com.roottrace.incident.IncidentSeverity;
import com.roottrace.incident.IncidentStatus;
import com.roottrace.intelligence.dto.ServiceRiskResponse;
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
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ServiceRiskService {

    private final IncidentRepository incidentRepository;
    private final PostmortemRepository postmortemRepository;

    public ServiceRiskService(
            IncidentRepository incidentRepository,
            PostmortemRepository postmortemRepository) {
        this.incidentRepository = incidentRepository;
        this.postmortemRepository = postmortemRepository;
    }

    @Transactional(readOnly = true)
    public ServiceRiskResponse evaluateServiceRisk(String serviceName) {
        if (serviceName == null || serviceName.isBlank()) {
            throw new ResourceNotFoundException("Service", serviceName);
        }

        String targetService = serviceName.trim();
        Instant cutoff = Instant.now().minus(Duration.ofDays(30));

        List<Incident> allServiceIncidents = incidentRepository.findAllNotDeleted(Pageable.unpaged()).getContent()
                .stream()
                .filter(i -> i.getService() != null && i.getService().equalsIgnoreCase(targetService))
                .collect(Collectors.toList());

        List<Incident> recentIncidents = allServiceIncidents.stream()
                .filter(i -> i.getCreatedAt() != null && !i.getCreatedAt().isBefore(cutoff))
                .collect(Collectors.toList());

        long total30d = recentIncidents.size();
        long critical30d = recentIncidents.stream().filter(i -> i.getSeverity() == IncidentSeverity.CRITICAL).count();
        long high30d = recentIncidents.stream().filter(i -> i.getSeverity() == IncidentSeverity.HIGH).count();
        long openCount = allServiceIncidents.stream().filter(i -> i.getStatus() == IncidentStatus.OPEN || i.getStatus() == IncidentStatus.INVESTIGATING).count();

        // Calculate MTTR for service
        List<Long> mttrList = recentIncidents.stream()
                .filter(i -> (i.getStatus() == IncidentStatus.RESOLVED || i.getStatus() == IncidentStatus.CLOSED)
                        && i.getResolvedAt() != null && i.getCreatedAt() != null)
                .map(i -> Duration.between(i.getCreatedAt(), i.getResolvedAt()).toMinutes())
                .collect(Collectors.toList());

        Double avgMttr = mttrList.isEmpty() ? null :
                Math.round((mttrList.stream().mapToDouble(Long::doubleValue).average().orElse(0.0)) * 10.0) / 10.0;

        // Find unresolved action items for postmortems on this service's incidents
        long unresolvedActionItems = 0;
        for (Incident inc : allServiceIncidents) {
            var pmOpt = postmortemRepository.findByIncidentIdWithActionItems(inc.getId());
            if (pmOpt.isPresent()) {
                Postmortem pm = pmOpt.get();
                if (pm.getActionItems() != null) {
                    for (PostmortemActionItem item : pm.getActionItems()) {
                        if (item.getStatus() == ActionItemStatus.OPEN || item.getStatus() == ActionItemStatus.IN_PROGRESS) {
                            unresolvedActionItems++;
                        }
                    }
                }
            }
        }

        // Recurrence rate calculation
        double recurrenceRate = 0.0;
        if (total30d > 1) {
            recurrenceRate = Math.round(((double) (total30d - 1) / total30d) * 100.0) / 100.0;
        }

        // Calculate score & risk factors
        List<String> riskFactors = new ArrayList<>();
        double score = 0.0;

        if (total30d == 0 && allServiceIncidents.isEmpty()) {
            riskFactors.add("No incidents recorded for service (0 pts)");
        } else {
            // 1. Incident Velocity (up to 25 pts)
            double velocityPts = Math.min(total30d * 5.0, 25.0);
            if (velocityPts > 0) {
                score += velocityPts;
                riskFactors.add(String.format("%d incidents in the last 30 days (+%.1f pts)", total30d, velocityPts));
            }

            // 2. Critical Incidents (up to 30 pts)
            double critPts = Math.min(critical30d * 15.0, 30.0);
            if (critPts > 0) {
                score += critPts;
                riskFactors.add(String.format("%d CRITICAL incidents in the last 30 days (+%.1f pts)", critical30d, critPts));
            }

            // 3. High Incidents (up to 15 pts)
            double highPts = Math.min(high30d * 7.5, 15.0);
            if (highPts > 0) {
                score += highPts;
                riskFactors.add(String.format("%d HIGH severity incidents in the last 30 days (+%.1f pts)", high30d, highPts));
            }

            // 4. Open Incidents (up to 20 pts)
            double openPts = Math.min(openCount * 10.0, 20.0);
            if (openPts > 0) {
                score += openPts;
                riskFactors.add(String.format("Currently has %d active OPEN/INVESTIGATING incidents (+%.1f pts)", openCount, openPts));
            }

            // 5. Recurrence Rate (up to 20 pts)
            double recPts = Math.min(recurrenceRate * 20.0, 20.0);
            if (recPts > 0) {
                score += recPts;
                riskFactors.add(String.format("%.0f%% recurrence rate indicates persistent failure modes (+%.1f pts)", recurrenceRate * 100, recPts));
            }

            // 6. Unresolved Action Items (up to 20 pts)
            double actionPts = Math.min(unresolvedActionItems * 5.0, 20.0);
            if (actionPts > 0) {
                score += actionPts;
                riskFactors.add(String.format("%d unresolved postmortem action items (+%.1f pts)", unresolvedActionItems, actionPts));
            }
        }

        // Cap score at 100
        score = Math.min(score, 100.0);
        score = Math.round(score * 10.0) / 10.0;

        String riskTier = determineRiskTier(score);

        return new ServiceRiskResponse(
                targetService,
                score,
                riskTier,
                total30d,
                critical30d,
                high30d,
                openCount,
                recurrenceRate,
                avgMttr,
                unresolvedActionItems,
                riskFactors,
                Instant.now()
        );
    }

    private String determineRiskTier(double score) {
        if (score >= 75.0) return "CRITICAL";
        if (score >= 50.0) return "HIGH";
        if (score >= 25.0) return "MEDIUM";
        return "LOW";
    }
}
