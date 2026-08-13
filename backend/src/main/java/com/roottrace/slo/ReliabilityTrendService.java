package com.roottrace.slo;

import com.roottrace.common.exception.BadRequestException;
import com.roottrace.incident.Incident;
import com.roottrace.incident.IncidentRepository;
import com.roottrace.slo.dto.ReliabilityTrendResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReliabilityTrendService {

    private final SloRepository sloRepository;
    private final SliMeasurementRepository measurementRepository;
    private final IncidentRepository incidentRepository;

    public ReliabilityTrendService(
            SloRepository sloRepository,
            SliMeasurementRepository measurementRepository,
            IncidentRepository incidentRepository) {
        this.sloRepository = sloRepository;
        this.measurementRepository = measurementRepository;
        this.incidentRepository = incidentRepository;
    }

    @Transactional(readOnly = true)
    public ReliabilityTrendResponse getReliabilityTrends(String serviceName, Integer days, String interval) {
        String svc = (serviceName != null && !serviceName.isBlank()) ? serviceName.trim() : "default";
        int windowDays = (days != null) ? days : 30;
        if (windowDays < 1 || windowDays > 90) {
            throw new BadRequestException("days parameter must be between 1 and 90");
        }

        String intvl = (interval != null && interval.equalsIgnoreCase("weekly")) ? "weekly" : "daily";

        Instant start = Instant.now().minus(windowDays, ChronoUnit.DAYS);
        Instant end = Instant.now();

        List<Slo> slos = sloRepository.findByServiceNameAndEnabledTrue(svc);

        // Fetch measurements for all enabled SLOs of this service
        List<SliMeasurement> allMeasurements = new ArrayList<>();
        for (Slo slo : slos) {
            allMeasurements.addAll(measurementRepository
                    .findBySloIdAndMeasurementTimeBetweenOrderByMeasurementTimeAsc(slo.getId(), start, end));
        }

        // Fetch incidents in window
        List<Incident> incidents = incidentRepository.findAllNotDeleted(Pageable.unpaged()).getContent()
                .stream()
                .filter(i -> i.getService() != null && i.getService().equalsIgnoreCase(svc)
                        && i.getCreatedAt() != null && i.getCreatedAt().isAfter(start))
                .toList();

        // Bucket by day or week
        Map<String, BucketStats> buckets = new LinkedHashMap<>();

        LocalDate startDate = start.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate endDate = end.atZone(ZoneOffset.UTC).toLocalDate();

        LocalDate cur = startDate;
        while (!cur.isAfter(endDate)) {
            String key = intvl.equals("weekly")
                    ? cur.with(java.time.DayOfWeek.MONDAY).toString()
                    : cur.toString();
            buckets.putIfAbsent(key, new BucketStats());
            cur = cur.plusDays(1);
        }

        for (SliMeasurement m : allMeasurements) {
            if (m.getMeasurementTime() == null) continue;
            LocalDate d = m.getMeasurementTime().atZone(ZoneOffset.UTC).toLocalDate();
            String key = intvl.equals("weekly")
                    ? d.with(java.time.DayOfWeek.MONDAY).toString()
                    : d.toString();
            BucketStats b = buckets.get(key);
            if (b != null) {
                b.totalEvents += (m.getTotalEvents() != null ? m.getTotalEvents() : 0);
                b.goodEvents += (m.getGoodEvents() != null ? m.getGoodEvents() : 0);
                b.badEvents += (m.getBadEvents() != null ? m.getBadEvents() : 0);
            }
        }

        for (Incident inc : incidents) {
            if (inc.getCreatedAt() == null) continue;
            LocalDate d = inc.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate();
            String key = intvl.equals("weekly")
                    ? d.with(java.time.DayOfWeek.MONDAY).toString()
                    : d.toString();
            BucketStats b = buckets.get(key);
            if (b != null) {
                b.incidentCount++;
            }
        }

        List<ReliabilityTrendResponse.ReliabilityDataPoint> dataPoints = new ArrayList<>();
        for (Map.Entry<String, BucketStats> entry : buckets.entrySet()) {
            BucketStats b = entry.getValue();
            double compliance = (b.totalEvents > 0)
                    ? (b.goodEvents / (double) b.totalEvents) * 100.0
                    : 100.0;

            double budgetConsumed = (b.totalEvents > 0)
                    ? Math.min(100.0, (b.badEvents / (double) Math.max(1, (long)(b.totalEvents * 0.001))) * 100.0)
                    : 0.0;

            dataPoints.add(new ReliabilityTrendResponse.ReliabilityDataPoint(
                    entry.getKey(),
                    Math.round(compliance * 100.0) / 100.0,
                    Math.round(budgetConsumed * 100.0) / 100.0,
                    b.incidentCount
            ));
        }

        return new ReliabilityTrendResponse(
                svc,
                windowDays,
                intvl,
                dataPoints
        );
    }

    private static class BucketStats {
        long totalEvents = 0;
        long goodEvents = 0;
        long badEvents = 0;
        int incidentCount = 0;
    }
}
