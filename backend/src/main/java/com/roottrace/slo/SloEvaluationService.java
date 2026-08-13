package com.roottrace.slo;

import com.roottrace.common.audit.AuditEventType;
import com.roottrace.common.audit.AuditService;
import com.roottrace.common.exception.ResourceNotFoundException;
import com.roottrace.slo.dto.ErrorBudgetResponse;
import com.roottrace.slo.dto.SloEvaluationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class SloEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(SloEvaluationService.class);

    private final SloRepository sloRepository;
    private final SliMeasurementRepository measurementRepository;
    private final ErrorBudgetService errorBudgetService;
    private final AuditService auditService;

    public SloEvaluationService(
            SloRepository sloRepository,
            SliMeasurementRepository measurementRepository,
            ErrorBudgetService errorBudgetService,
            AuditService auditService) {
        this.sloRepository = sloRepository;
        this.measurementRepository = measurementRepository;
        this.errorBudgetService = errorBudgetService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public SloEvaluationResponse evaluateSlo(UUID sloId) {
        Slo slo = sloRepository.findById(sloId)
                .orElseThrow(() -> new ResourceNotFoundException("Slo", sloId));
        return evaluateSlo(slo);
    }

    @Transactional(readOnly = true)
    public SloEvaluationResponse evaluateSlo(Slo slo) {
        int windowDays = slo.getWindowDays() != null ? slo.getWindowDays() : 30;
        Instant start = Instant.now().minus(windowDays, ChronoUnit.DAYS);
        Instant end = Instant.now();

        List<SliMeasurement> measurements = measurementRepository
                .findBySloIdAndMeasurementTimeBetweenOrderByMeasurementTimeAsc(slo.getId(), start, end);

        long totalEvents = 0;
        long goodEvents = 0;
        long badEvents = 0;

        for (SliMeasurement m : measurements) {
            totalEvents += m.getTotalEvents() != null ? m.getTotalEvents() : 0;
            goodEvents += m.getGoodEvents() != null ? m.getGoodEvents() : 0;
            badEvents += m.getBadEvents() != null ? m.getBadEvents() : 0;
        }

        double target = slo.getTargetPercentage().doubleValue();
        double actualPercentage = (totalEvents > 0)
                ? (goodEvents / (double) totalEvents) * 100.0
                : target;

        double difference = actualPercentage - target;

        ErrorBudgetResponse errorBudget = errorBudgetService.calculateErrorBudget(slo);
        SloStatus status = errorBudget.status();

        return new SloEvaluationResponse(
                slo.getId(),
                slo.getServiceName(),
                slo.getName(),
                target,
                Math.round(actualPercentage * 1000.0) / 1000.0,
                Math.round(difference * 1000.0) / 1000.0,
                status,
                totalEvents,
                goodEvents,
                badEvents,
                errorBudget.budgetRemainingPercentage(),
                errorBudget.budgetConsumedPercentage(),
                Instant.now()
        );
    }

    @Transactional
    public SloEvaluationResponse evaluateAndDetectBreach(UUID sloId, String callerEmail) {
        Slo slo = sloRepository.findById(sloId)
                .orElseThrow(() -> new ResourceNotFoundException("Slo", sloId));

        SloEvaluationResponse evaluation = evaluateSlo(slo);

        if (evaluation.status() == SloStatus.BREACHED) {
            log.warn("SLO breach detected for [{}] in service [{}]! Actual: {}%, Target: {}%",
                    slo.getName(), slo.getServiceName(), evaluation.actualPercentage(), evaluation.targetPercentage());

            auditService.record(
                    AuditEventType.SLO_BREACH_DETECTED,
                    "Slo",
                    slo.getId().toString(),
                    callerEmail != null ? callerEmail : "system",
                    String.format("SLO breach detected for %s [%s]: actual=%.3f%%, target=%.3f%%",
                            slo.getName(), slo.getServiceName(), evaluation.actualPercentage(), evaluation.targetPercentage())
            );
        }

        return evaluation;
    }
}
