package com.roottrace.slo;

import com.roottrace.common.exception.BadRequestException;
import com.roottrace.common.exception.ResourceNotFoundException;
import com.roottrace.slo.dto.BurnRateResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class BurnRateService {

    private final SloRepository sloRepository;
    private final SliMeasurementRepository measurementRepository;

    public BurnRateService(SloRepository sloRepository, SliMeasurementRepository measurementRepository) {
        this.sloRepository = sloRepository;
        this.measurementRepository = measurementRepository;
    }

    @Transactional(readOnly = true)
    public BurnRateResponse calculateBurnRate(UUID sloId, Integer windowMinutes) {
        Slo slo = sloRepository.findById(sloId)
                .orElseThrow(() -> new ResourceNotFoundException("Slo", sloId));
        return calculateBurnRate(slo, windowMinutes);
    }

    @Transactional(readOnly = true)
    public BurnRateResponse calculateBurnRate(Slo slo, Integer windowMinutes) {
        int window = (windowMinutes != null) ? windowMinutes : 60;
        if (window < 5 || window > 10080) {
            throw new BadRequestException("windowMinutes must be between 5 and 10080 (7 days)");
        }

        Instant start = Instant.now().minus(window, ChronoUnit.MINUTES);
        Instant end = Instant.now();

        List<SliMeasurement> measurements = measurementRepository
                .findBySloIdAndMeasurementTimeBetweenOrderByMeasurementTimeAsc(slo.getId(), start, end);

        long totalEvents = 0;
        long badEvents = 0;

        for (SliMeasurement m : measurements) {
            totalEvents += m.getTotalEvents() != null ? m.getTotalEvents() : 0;
            badEvents += m.getBadEvents() != null ? m.getBadEvents() : 0;
        }

        double target = slo.getTargetPercentage().doubleValue();
        double allowedErrorRate = Math.max(0.000001, (100.0 - target) / 100.0);
        double actualErrorRate = (totalEvents > 0) ? (badEvents / (double) totalEvents) : 0.0;

        double burnRate = actualErrorRate / allowedErrorRate;
        double roundedBurnRate = Math.round(burnRate * 100.0) / 100.0;

        String severity;
        if (roundedBurnRate >= 5.0) {
            severity = "CRITICAL";
        } else if (roundedBurnRate >= 2.0) {
            severity = "HIGH";
        } else if (roundedBurnRate >= 1.0) {
            severity = "ELEVATED";
        } else {
            severity = "NORMAL";
        }

        return new BurnRateResponse(
                slo.getId(),
                slo.getServiceName(),
                slo.getName(),
                roundedBurnRate,
                severity,
                window,
                Math.round(actualErrorRate * 10000.0) / 100.0,
                Math.round(allowedErrorRate * 10000.0) / 100.0,
                Instant.now()
        );
    }
}
