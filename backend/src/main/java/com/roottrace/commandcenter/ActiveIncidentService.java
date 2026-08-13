package com.roottrace.commandcenter;

import com.roottrace.commandcenter.dto.ActiveIncidentResponse;
import com.roottrace.commandcenter.dto.ActiveIncidentsResponse;
import com.roottrace.common.exception.BadRequestException;
import com.roottrace.incident.Incident;
import com.roottrace.incident.IncidentRepository;
import com.roottrace.incident.IncidentSeverity;
import com.roottrace.incident.IncidentStatus;
import com.roottrace.slo.BurnRateService;
import com.roottrace.slo.ReliabilityRiskService;
import com.roottrace.slo.Slo;
import com.roottrace.slo.SloEvaluationService;
import com.roottrace.slo.SloRepository;
import com.roottrace.slo.SloStatus;
import com.roottrace.slo.dto.BurnRateResponse;
import com.roottrace.slo.dto.ReliabilityRiskResponse;
import com.roottrace.slo.dto.SloEvaluationResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ActiveIncidentService {

    private final IncidentRepository incidentRepository;
    private final SloRepository sloRepository;
    private final SloEvaluationService sloEvaluationService;
    private final BurnRateService burnRateService;
    private final ReliabilityRiskService reliabilityRiskService;

    public ActiveIncidentService(
            IncidentRepository incidentRepository,
            SloRepository sloRepository,
            SloEvaluationService sloEvaluationService,
            BurnRateService burnRateService,
            ReliabilityRiskService reliabilityRiskService) {
        this.incidentRepository = incidentRepository;
        this.sloRepository = sloRepository;
        this.sloEvaluationService = sloEvaluationService;
        this.burnRateService = burnRateService;
        this.reliabilityRiskService = reliabilityRiskService;
    }

    @Transactional(readOnly = true)
    public ActiveIncidentsResponse getActiveIncidents(String severity, String service, Integer limit) {
        int maxLimit = (limit != null) ? limit : 50;
        if (maxLimit < 1 || maxLimit > 100) {
            throw new BadRequestException("limit must be between 1 and 100. Provided: " + maxLimit);
        }

        IncidentSeverity targetSev = null;
        if (severity != null && !severity.isBlank()) {
            try {
                targetSev = IncidentSeverity.valueOf(severity.toUpperCase().trim());
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid severity: " + severity);
            }
        }

        String targetService = (service != null && !service.isBlank()) ? service.trim() : null;

        final IncidentSeverity finalTargetSev = targetSev;
        final String finalTargetService = targetService;
        List<Incident> activeIncidents = incidentRepository.findAllNotDeleted(Pageable.unpaged()).getContent()
                .stream()
                .filter(i -> i.getStatus() == IncidentStatus.OPEN || i.getStatus() == IncidentStatus.INVESTIGATING)
                .filter(i -> finalTargetSev == null || i.getSeverity() == finalTargetSev)
                .filter(i -> finalTargetService == null || (i.getService() != null && i.getService().equalsIgnoreCase(finalTargetService)))
                .collect(Collectors.toList());

        Instant now = Instant.now();
        List<ActiveIncidentResponse> responseList = new ArrayList<>();

        for (Incident inc : activeIncidents) {
            long ageMinutes = (inc.getCreatedAt() != null)
                    ? Math.max(0, Duration.between(inc.getCreatedAt(), now).toMinutes())
                    : 0;

            String svc = inc.getService();

            // 1. Check if service has active SLO breach
            boolean sloBreached = false;
            String highestBurnSeverity = "NORMAL";
            List<Slo> slos = sloRepository.findByServiceNameAndEnabledTrue(svc);

            for (Slo slo : slos) {
                SloEvaluationResponse eval = sloEvaluationService.evaluateSlo(slo);
                if (eval.status() == SloStatus.BREACHED) {
                    sloBreached = true;
                }
                BurnRateResponse burn = burnRateService.calculateBurnRate(slo, 60);
                if ("CRITICAL".equalsIgnoreCase(burn.severity())) {
                    highestBurnSeverity = "CRITICAL";
                } else if ("HIGH".equalsIgnoreCase(burn.severity()) && !"CRITICAL".equalsIgnoreCase(highestBurnSeverity)) {
                    highestBurnSeverity = "HIGH";
                } else if ("ELEVATED".equalsIgnoreCase(burn.severity()) && "NORMAL".equalsIgnoreCase(highestBurnSeverity)) {
                    highestBurnSeverity = "ELEVATED";
                }
            }

            // 2. Service Risk Tier
            ReliabilityRiskResponse risk = reliabilityRiskService.evaluateReliabilityRisk(svc);
            String serviceRiskTier = (risk != null && risk.riskTier() != null) ? risk.riskTier() : "LOW";

            // 3. Compute deterministic priority score (0 - 100)
            double priorityScore = 0.0;

            // Base severity points
            switch (inc.getSeverity()) {
                case CRITICAL -> priorityScore += 40.0;
                case HIGH -> priorityScore += 25.0;
                case MEDIUM -> priorityScore += 15.0;
                case LOW -> priorityScore += 5.0;
            }

            // SLO breach points (+20)
            if (sloBreached) {
                priorityScore += 20.0;
            }

            // Burn rate points (+15 for critical, +10 for high, +5 for elevated)
            switch (highestBurnSeverity) {
                case "CRITICAL" -> priorityScore += 15.0;
                case "HIGH" -> priorityScore += 10.0;
                case "ELEVATED" -> priorityScore += 5.0;
            }

            // Service risk tier points (+15 for critical, +10 for high, +5 for medium)
            switch (serviceRiskTier) {
                case "CRITICAL" -> priorityScore += 15.0;
                case "HIGH" -> priorityScore += 10.0;
                case "MEDIUM" -> priorityScore += 5.0;
            }

            // Age factor (+1 pt per hour, max 10 pts)
            double ageFactor = Math.min(10.0, ageMinutes / 60.0);
            priorityScore += ageFactor;

            double clampedScore = Math.min(100.0, Math.max(0.0, priorityScore));
            double roundedScore = Math.round(clampedScore * 10.0) / 10.0;

            String recommendedAttention;
            if (roundedScore >= 75.0) {
                recommendedAttention = "IMMEDIATE";
            } else if (roundedScore >= 50.0) {
                recommendedAttention = "URGENT";
            } else {
                recommendedAttention = "NORMAL";
            }

            responseList.add(new ActiveIncidentResponse(
                    inc.getId(),
                    inc.getTitle(),
                    inc.getService(),
                    inc.getSeverity(),
                    inc.getStatus(),
                    ageMinutes,
                    sloBreached,
                    highestBurnSeverity,
                    serviceRiskTier,
                    roundedScore,
                    recommendedAttention,
                    inc.getCreatedAt()
            ));
        }

        // Sort descending by priorityScore
        List<ActiveIncidentResponse> sorted = responseList.stream()
                .sorted(Comparator.comparing(ActiveIncidentResponse::priorityScore).reversed())
                .limit(maxLimit)
                .collect(Collectors.toList());

        return new ActiveIncidentsResponse(sorted.size(), sorted);
    }
}
