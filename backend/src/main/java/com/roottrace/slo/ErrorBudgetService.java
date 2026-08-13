package com.roottrace.slo;

import com.roottrace.common.exception.ResourceNotFoundException;
import com.roottrace.slo.dto.ErrorBudgetResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class ErrorBudgetService {

    private final SloRepository sloRepository;
    private final SliMeasurementRepository measurementRepository;

    public ErrorBudgetService(SloRepository sloRepository, SliMeasurementRepository measurementRepository) {
        this.sloRepository = sloRepository;
        this.measurementRepository = measurementRepository;
    }

    @Transactional(readOnly = true)
    public ErrorBudgetResponse calculateErrorBudget(UUID sloId) {
        Slo slo = sloRepository.findById(sloId)
                .orElseThrow(() -> new ResourceNotFoundException("Slo", sloId));
        return calculateErrorBudget(slo);
    }

    @Transactional(readOnly = true)
    public ErrorBudgetResponse calculateErrorBudget(Slo slo) {
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
        double errorBudgetPercentage = Math.round((100.0 - target) * 1000.0) / 1000.0;

        long allowedBadEvents = Math.round(totalEvents * (errorBudgetPercentage / 100.0));
        long remainingBadEvents = Math.max(0, allowedBadEvents - badEvents);

        double budgetConsumedPercentage;
        if (allowedBadEvents > 0) {
            budgetConsumedPercentage = Math.min(100.0, (badEvents / (double) allowedBadEvents) * 100.0);
        } else {
            budgetConsumedPercentage = (badEvents > 0) ? 100.0 : 0.0;
        }

        double budgetRemainingPercentage = Math.max(0.0, 100.0 - budgetConsumedPercentage);

        SloStatus status;
        if (budgetRemainingPercentage <= 0.0 || (allowedBadEvents > 0 && badEvents > allowedBadEvents)) {
            status = SloStatus.BREACHED;
        } else if (budgetRemainingPercentage < 25.0) {
            status = SloStatus.WARNING;
        } else {
            status = SloStatus.HEALTHY;
        }

        return new ErrorBudgetResponse(
                slo.getId(),
                slo.getServiceName(),
                slo.getName(),
                target,
                errorBudgetPercentage,
                totalEvents,
                allowedBadEvents,
                badEvents,
                remainingBadEvents,
                Math.round(budgetConsumedPercentage * 100.0) / 100.0,
                Math.round(budgetRemainingPercentage * 100.0) / 100.0,
                status
        );
    }
}
