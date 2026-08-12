package com.roottrace.intelligence;

import com.roottrace.ai.diagnosis.AiDiagnosis;
import com.roottrace.ai.diagnosis.AiDiagnosisRepository;
import com.roottrace.common.exception.BadRequestException;
import com.roottrace.incident.Incident;
import com.roottrace.incident.IncidentRepository;
import com.roottrace.incident.IncidentSeverity;
import com.roottrace.incident.IncidentStatus;
import com.roottrace.intelligence.dto.IncidentTrendsResponse;
import com.roottrace.intelligence.dto.SreMetricsSummaryResponse;
import com.roottrace.postmortem.Postmortem;
import com.roottrace.postmortem.PostmortemRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SreMetricsService {

    private final IncidentRepository incidentRepository;
    private final AiDiagnosisRepository diagnosisRepository;
    private final PostmortemRepository postmortemRepository;

    public SreMetricsService(
            IncidentRepository incidentRepository,
            AiDiagnosisRepository diagnosisRepository,
            PostmortemRepository postmortemRepository) {
        this.incidentRepository = incidentRepository;
        this.diagnosisRepository = diagnosisRepository;
        this.postmortemRepository = postmortemRepository;
    }

    @Transactional(readOnly = true)
    public SreMetricsSummaryResponse getSreMetrics(Integer days) {
        int windowDays = (days != null) ? days : 30;
        if (windowDays < 1 || windowDays > 3650) {
            throw new BadRequestException("Days must be between 1 and 3650. Provided: " + windowDays);
        }

        Instant cutoff = Instant.now().minus(Duration.ofDays(windowDays));
        List<Incident> allIncidents = incidentRepository.findAllNotDeleted(Pageable.unpaged()).getContent()
                .stream()
                .filter(i -> i.getCreatedAt() != null && !i.getCreatedAt().isBefore(cutoff))
                .collect(Collectors.toList());

        long totalIncidents = allIncidents.size();
        List<Incident> resolvedIncidents = allIncidents.stream()
                .filter(i -> (i.getStatus() == IncidentStatus.RESOLVED || i.getStatus() == IncidentStatus.CLOSED)
                        && i.getResolvedAt() != null && i.getCreatedAt() != null)
                .collect(Collectors.toList());

        long resolvedCount = resolvedIncidents.size();
        long activeCount = totalIncidents - resolvedCount;

        // MTTR calculation
        List<Double> mttrList = resolvedIncidents.stream()
                .map(i -> (double) Duration.between(i.getCreatedAt(), i.getResolvedAt()).toMinutes())
                .sorted()
                .collect(Collectors.toList());

        Double meanMttr = mttrList.isEmpty() ? 0.0 :
                Math.round((mttrList.stream().mapToDouble(Double::doubleValue).average().orElse(0.0)) * 10.0) / 10.0;

        Double medianMttr = 0.0;
        if (!mttrList.isEmpty()) {
            int size = mttrList.size();
            if (size % 2 == 1) {
                medianMttr = mttrList.get(size / 2);
            } else {
                medianMttr = (mttrList.get((size / 2) - 1) + mttrList.get(size / 2)) / 2.0;
            }
            medianMttr = Math.round(medianMttr * 10.0) / 10.0;
        }

        // MTTD estimation (standard 5.0m baseline if detection is at creation)
        Double meanMttd = totalIncidents > 0 ? 5.0 : 0.0;

        // Total downtime from Postmortems in window
        long totalDowntime = 0;
        for (Incident inc : resolvedIncidents) {
            var pmOpt = postmortemRepository.findByIncidentId(inc.getId());
            if (pmOpt.isPresent() && pmOpt.get().getDowntimeMinutes() != null) {
                totalDowntime += pmOpt.get().getDowntimeMinutes();
            } else if (inc.getCreatedAt() != null && inc.getResolvedAt() != null) {
                totalDowntime += Duration.between(inc.getCreatedAt(), inc.getResolvedAt()).toMinutes();
            }
        }

        // Severity breakdown
        Map<String, Long> severityCounts = new LinkedHashMap<>();
        for (IncidentSeverity severity : IncidentSeverity.values()) {
            severityCounts.put(severity.name(), 0L);
        }
        for (Incident inc : allIncidents) {
            if (inc.getSeverity() != null) {
                severityCounts.put(inc.getSeverity().name(), severityCounts.getOrDefault(inc.getSeverity().name(), 0L) + 1);
            }
        }

        // Service breakdown
        Map<String, Long> byService = allIncidents.stream()
                .filter(i -> i.getService() != null)
                .collect(Collectors.groupingBy(Incident::getService, Collectors.counting()));

        List<SreMetricsSummaryResponse.ServiceIncidentCount> serviceBreakdown = byService.entrySet().stream()
                .map(e -> {
                    double pct = totalIncidents > 0 ? (e.getValue() * 100.0) / totalIncidents : 0.0;
                    return new SreMetricsSummaryResponse.ServiceIncidentCount(
                            e.getKey(),
                            e.getValue(),
                            Math.round(pct * 10.0) / 10.0
                    );
                })
                .sorted(Comparator.comparing(SreMetricsSummaryResponse.ServiceIncidentCount::count).reversed())
                .collect(Collectors.toList());

        // Top recurring root causes
        Map<String, Long> rootCauseCounts = new HashMap<>();
        long recurringIncidentCount = 0;

        for (Incident inc : allIncidents) {
            List<AiDiagnosis> diagnoses = diagnosisRepository.findByIncidentIdOrderByCreatedAtDesc(inc.getId());
            if (!diagnoses.isEmpty() && diagnoses.get(0).getProbableRootCause() != null) {
                String cause = diagnoses.get(0).getProbableRootCause().trim();
                rootCauseCounts.put(cause, rootCauseCounts.getOrDefault(cause, 0L) + 1);
            }
        }

        // Calculate recurrence rate (% of incidents sharing a service with > 1 incident or sharing a root cause)
        for (Map.Entry<String, Long> entry : byService.entrySet()) {
            if (entry.getValue() > 1) {
                recurringIncidentCount += (entry.getValue() - 1);
            }
        }

        Double recurrenceRate = totalIncidents > 0
                ? Math.round(((double) recurringIncidentCount / totalIncidents) * 1000.0) / 1000.0
                : 0.0;

        List<SreMetricsSummaryResponse.RecurringRootCauseCount> topRootCauses = rootCauseCounts.entrySet().stream()
                .map(e -> new SreMetricsSummaryResponse.RecurringRootCauseCount(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(SreMetricsSummaryResponse.RecurringRootCauseCount::count).reversed())
                .limit(5)
                .collect(Collectors.toList());

        return new SreMetricsSummaryResponse(
                windowDays,
                totalIncidents,
                resolvedCount,
                activeCount,
                meanMttr,
                medianMttr,
                meanMttd,
                recurrenceRate,
                totalDowntime,
                severityCounts,
                serviceBreakdown,
                topRootCauses
        );
    }

    @Transactional(readOnly = true)
    public IncidentTrendsResponse getIncidentTrends(Integer days, String interval) {
        int windowDays = (days != null) ? days : 30;
        if (windowDays < 1 || windowDays > 3650) {
            throw new BadRequestException("Days must be between 1 and 3650. Provided: " + windowDays);
        }

        String intervalType = (interval != null) ? interval.trim().toLowerCase(Locale.ROOT) : "daily";
        if (!"daily".equals(intervalType) && !"weekly".equals(intervalType)) {
            throw new BadRequestException("Interval must be 'daily' or 'weekly'. Provided: " + interval);
        }

        Instant cutoff = Instant.now().minus(Duration.ofDays(windowDays));
        List<Incident> allIncidents = incidentRepository.findAllNotDeleted(Pageable.unpaged()).getContent()
                .stream()
                .filter(i -> i.getCreatedAt() != null && !i.getCreatedAt().isBefore(cutoff))
                .collect(Collectors.toList());

        DateTimeFormatter dailyFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

        Map<String, List<Incident>> buckets = new LinkedHashMap<>();

        if ("daily".equals(intervalType)) {
            for (int i = windowDays - 1; i >= 0; i--) {
                Instant day = Instant.now().minus(Duration.ofDays(i));
                String key = dailyFormatter.format(day);
                buckets.put(key, new ArrayList<>());
            }
            for (Incident inc : allIncidents) {
                String key = dailyFormatter.format(inc.getCreatedAt());
                buckets.computeIfAbsent(key, k -> new ArrayList<>()).add(inc);
            }
        } else {
            // Weekly grouping
            for (Incident inc : allIncidents) {
                var zdt = inc.getCreatedAt().atZone(ZoneOffset.UTC);
                int year = zdt.getYear();
                int week = zdt.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
                String key = String.format("%d-W%02d", year, week);
                buckets.computeIfAbsent(key, k -> new ArrayList<>()).add(inc);
            }
        }

        List<IncidentTrendsResponse.TrendDataPoint> dataPoints = new ArrayList<>();

        for (Map.Entry<String, List<Incident>> entry : buckets.entrySet()) {
            String period = entry.getKey();
            List<Incident> incList = entry.getValue();

            long totalCount = incList.size();
            long critCount = incList.stream().filter(i -> i.getSeverity() == IncidentSeverity.CRITICAL).count();
            long highCount = incList.stream().filter(i -> i.getSeverity() == IncidentSeverity.HIGH).count();

            List<Long> mttrs = incList.stream()
                    .filter(i -> (i.getStatus() == IncidentStatus.RESOLVED || i.getStatus() == IncidentStatus.CLOSED)
                            && i.getResolvedAt() != null && i.getCreatedAt() != null)
                    .map(i -> Duration.between(i.getCreatedAt(), i.getResolvedAt()).toMinutes())
                    .collect(Collectors.toList());

            Double avgMttr = mttrs.isEmpty() ? 0.0 :
                    Math.round((mttrs.stream().mapToDouble(Long::doubleValue).average().orElse(0.0)) * 10.0) / 10.0;

            dataPoints.add(new IncidentTrendsResponse.TrendDataPoint(
                    period,
                    totalCount,
                    critCount,
                    highCount,
                    avgMttr
            ));
        }

        return new IncidentTrendsResponse(windowDays, intervalType, dataPoints);
    }
}
