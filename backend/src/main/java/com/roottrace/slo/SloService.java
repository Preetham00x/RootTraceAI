package com.roottrace.slo;

import com.roottrace.common.audit.AuditEventType;
import com.roottrace.common.audit.AuditService;
import com.roottrace.common.exception.BadRequestException;
import com.roottrace.common.exception.ResourceNotFoundException;
import com.roottrace.common.security.CurrentUserService;
import com.roottrace.incident.Incident;
import com.roottrace.incident.IncidentRepository;
import com.roottrace.postmortem.ActionItemStatus;
import com.roottrace.postmortem.PostmortemActionItemRepository;
import com.roottrace.slo.dto.BurnRateResponse;
import com.roottrace.slo.dto.CreateSloRequest;
import com.roottrace.slo.dto.ErrorBudgetResponse;
import com.roottrace.slo.dto.RecordSliMeasurementRequest;
import com.roottrace.slo.dto.ReliabilityDashboardResponse;
import com.roottrace.slo.dto.ReliabilityRiskResponse;
import com.roottrace.slo.dto.SliMeasurementResponse;
import com.roottrace.slo.dto.SloEvaluationResponse;
import com.roottrace.slo.dto.SloResponse;
import com.roottrace.slo.dto.UpdateSloRequest;
import com.roottrace.user.User;
import com.roottrace.user.dto.UserDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SloService {

    private static final Logger log = LoggerFactory.getLogger(SloService.class);

    private final SloRepository sloRepository;
    private final SliMeasurementRepository measurementRepository;
    private final SloEvaluationService sloEvaluationService;
    private final ErrorBudgetService errorBudgetService;
    private final BurnRateService burnRateService;
    private final ReliabilityRiskService reliabilityRiskService;
    private final IncidentRepository incidentRepository;
    private final PostmortemActionItemRepository actionItemRepository;
    private final CurrentUserService currentUserService;
    private final AuditService auditService;

    public SloService(
            SloRepository sloRepository,
            SliMeasurementRepository measurementRepository,
            SloEvaluationService sloEvaluationService,
            ErrorBudgetService errorBudgetService,
            BurnRateService burnRateService,
            ReliabilityRiskService reliabilityRiskService,
            IncidentRepository incidentRepository,
            PostmortemActionItemRepository actionItemRepository,
            CurrentUserService currentUserService,
            AuditService auditService) {
        this.sloRepository = sloRepository;
        this.measurementRepository = measurementRepository;
        this.sloEvaluationService = sloEvaluationService;
        this.errorBudgetService = errorBudgetService;
        this.burnRateService = burnRateService;
        this.reliabilityRiskService = reliabilityRiskService;
        this.incidentRepository = incidentRepository;
        this.actionItemRepository = actionItemRepository;
        this.currentUserService = currentUserService;
        this.auditService = auditService;
    }

    @Transactional
    public SloResponse createSlo(String serviceName, CreateSloRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw new BadRequestException("SLO name is required");
        }
        String svc = (serviceName != null && !serviceName.isBlank()) ? serviceName.trim() : "default";

        if (sloRepository.findByServiceNameAndName(svc, request.name().trim()).isPresent()) {
            throw new BadRequestException("SLO with name '" + request.name() + "' already exists for service '" + svc + "'");
        }

        User currentUser = currentUserService.getCurrentUser();

        Slo slo = new Slo(
                svc,
                request.name().trim(),
                request.description(),
                request.sloType(),
                request.targetPercentage(),
                request.windowDays(),
                request.warningThresholdPercentage(),
                request.criticalThresholdPercentage(),
                currentUser
        );

        Slo saved = sloRepository.save(slo);

        auditService.record(
                AuditEventType.SLO_CREATED,
                "Slo",
                saved.getId().toString(),
                currentUser.getEmail(),
                String.format("Created SLO '%s' [%s] for service '%s' (Target: %s%%)",
                        saved.getName(), saved.getSloType(), svc, saved.getTargetPercentage())
        );

        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<SloResponse> listSlos(String serviceName) {
        String svc = (serviceName != null && !serviceName.isBlank()) ? serviceName.trim() : "default";
        return sloRepository.findByServiceName(svc).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public SloResponse getSlo(String serviceName, UUID sloId) {
        Slo slo = sloRepository.findByIdAndServiceName(sloId, serviceName)
                .orElseThrow(() -> new ResourceNotFoundException("Slo", sloId));
        return mapToResponse(slo);
    }

    @Transactional
    public SloResponse updateSlo(String serviceName, UUID sloId, UpdateSloRequest request) {
        Slo slo = sloRepository.findByIdAndServiceName(sloId, serviceName)
                .orElseThrow(() -> new ResourceNotFoundException("Slo", sloId));

        if (request.name() != null && !request.name().isBlank()) {
            String newName = request.name().trim();
            if (!newName.equalsIgnoreCase(slo.getName()) && sloRepository.findByServiceNameAndName(serviceName, newName).isPresent()) {
                throw new BadRequestException("SLO with name '" + newName + "' already exists for service '" + serviceName + "'");
            }
            slo.setName(newName);
        }

        if (request.description() != null) slo.setDescription(request.description());
        if (request.sloType() != null) slo.setSloType(request.sloType());
        if (request.targetPercentage() != null) slo.setTargetPercentage(request.targetPercentage());
        if (request.windowDays() != null) slo.setWindowDays(request.windowDays());
        if (request.warningThresholdPercentage() != null) slo.setWarningThresholdPercentage(request.warningThresholdPercentage());
        if (request.criticalThresholdPercentage() != null) slo.setCriticalThresholdPercentage(request.criticalThresholdPercentage());
        if (request.enabled() != null) slo.setEnabled(request.enabled());

        Slo saved = sloRepository.save(slo);
        User currentUser = currentUserService.getCurrentUser();

        auditService.record(
                AuditEventType.SLO_UPDATED,
                "Slo",
                saved.getId().toString(),
                currentUser != null ? currentUser.getEmail() : "system",
                "Updated SLO: " + saved.getName()
        );

        return mapToResponse(saved);
    }

    @Transactional
    public void disableSlo(String serviceName, UUID sloId) {
        Slo slo = sloRepository.findByIdAndServiceName(sloId, serviceName)
                .orElseThrow(() -> new ResourceNotFoundException("Slo", sloId));

        slo.setEnabled(false);
        sloRepository.save(slo);

        User currentUser = currentUserService.getCurrentUser();
        auditService.record(
                AuditEventType.SLO_DISABLED,
                "Slo",
                slo.getId().toString(),
                currentUser != null ? currentUser.getEmail() : "system",
                "Disabled SLO: " + slo.getName()
        );
    }

    @Transactional
    public SliMeasurementResponse recordMeasurement(String serviceName, UUID sloId, RecordSliMeasurementRequest request) {
        Slo slo = sloRepository.findByIdAndServiceName(sloId, serviceName)
                .orElseThrow(() -> new ResourceNotFoundException("Slo", sloId));

        if (request.totalEvents() == null || request.totalEvents() < 0) {
            throw new BadRequestException("totalEvents must be >= 0");
        }
        if (request.goodEvents() == null || request.goodEvents() < 0) {
            throw new BadRequestException("goodEvents must be >= 0");
        }
        if (request.badEvents() == null || request.badEvents() < 0) {
            throw new BadRequestException("badEvents must be >= 0");
        }
        if (request.goodEvents() + request.badEvents() > request.totalEvents()) {
            throw new BadRequestException("goodEvents + badEvents cannot exceed totalEvents");
        }

        BigDecimal val = request.value();
        if (val == null) {
            if (request.totalEvents() > 0) {
                double pct = (request.goodEvents() / (double) request.totalEvents()) * 100.0;
                val = BigDecimal.valueOf(pct).setScale(6, RoundingMode.HALF_UP);
            } else {
                val = slo.getTargetPercentage();
            }
        }

        SliMeasurement measurement = new SliMeasurement(
                slo,
                request.measurementTime() != null ? request.measurementTime() : Instant.now(),
                request.totalEvents(),
                request.goodEvents(),
                request.badEvents(),
                val,
                request.source() != null ? request.source() : "API"
        );

        SliMeasurement saved = measurementRepository.save(measurement);

        return new SliMeasurementResponse(
                saved.getId(),
                slo.getId(),
                saved.getMeasurementTime(),
                saved.getTotalEvents(),
                saved.getGoodEvents(),
                saved.getBadEvents(),
                saved.getValue(),
                saved.getSource(),
                saved.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public ReliabilityDashboardResponse getReliabilityDashboard(String serviceName) {
        String svc = (serviceName != null && !serviceName.isBlank()) ? serviceName.trim() : "default";

        List<Slo> slos = sloRepository.findByServiceNameAndEnabledTrue(svc);
        List<SloEvaluationResponse> sloEvals = new ArrayList<>();

        int activeBreaches = 0;
        double totalBudgetConsumed = 0;
        double highestBurnRate = 0.0;

        for (Slo slo : slos) {
            SloEvaluationResponse eval = sloEvaluationService.evaluateSlo(slo);
            sloEvals.add(eval);
            if (eval.status() == SloStatus.BREACHED) {
                activeBreaches++;
            }
            ErrorBudgetResponse budget = errorBudgetService.calculateErrorBudget(slo);
            totalBudgetConsumed += budget.budgetConsumedPercentage();

            BurnRateResponse burn = burnRateService.calculateBurnRate(slo, 60);
            if (burn.burnRate() > highestBurnRate) {
                highestBurnRate = burn.burnRate();
            }
        }

        double avgBudgetConsumed = !slos.isEmpty() ? (totalBudgetConsumed / slos.size()) : 0.0;

        ReliabilityRiskResponse risk = reliabilityRiskService.evaluateReliabilityRisk(svc);

        // Recent incident count and recurrence rate
        Instant thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);
        List<Incident> recentIncidents = incidentRepository.findAllNotDeleted(Pageable.unpaged()).getContent()
                .stream()
                .filter(i -> i.getService() != null && i.getService().equalsIgnoreCase(svc)
                        && i.getCreatedAt() != null && i.getCreatedAt().isAfter(thirtyDaysAgo))
                .toList();

        int recentIncidentsCount = recentIncidents.size();
        long uniqueTitles = recentIncidents.stream().map(Incident::getTitle).distinct().count();
        double recurrenceRate = (recentIncidentsCount > 1) ? Math.max(0.0, (recentIncidentsCount - uniqueTitles) / (double) recentIncidentsCount) : 0.0;

        int unresolvedActionItems = risk.unresolvedActionItems();

        return new ReliabilityDashboardResponse(
                svc,
                risk.riskScore(),
                risk.riskTier(),
                sloEvals,
                activeBreaches,
                Math.round(avgBudgetConsumed * 10.0) / 10.0,
                highestBurnRate,
                recentIncidentsCount,
                Math.round(recurrenceRate * 100.0) / 100.0,
                unresolvedActionItems,
                Instant.now()
        );
    }

    private SloResponse mapToResponse(Slo slo) {
        UserDto creator = (slo.getCreatedBy() != null)
                ? new UserDto(slo.getCreatedBy().getId(), slo.getCreatedBy().getEmail(), slo.getCreatedBy().getFirstName(), slo.getCreatedBy().getLastName(), slo.getCreatedBy().getRole().name())
                : null;

        return new SloResponse(
                slo.getId(),
                slo.getServiceName(),
                slo.getName(),
                slo.getDescription(),
                slo.getSloType(),
                slo.getTargetPercentage(),
                slo.getWindowDays(),
                slo.getWarningThresholdPercentage(),
                slo.getCriticalThresholdPercentage(),
                slo.getEnabled(),
                creator,
                slo.getCreatedAt(),
                slo.getUpdatedAt()
        );
    }
}
