package com.roottrace.slo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface SliMeasurementRepository extends JpaRepository<SliMeasurement, UUID> {

    List<SliMeasurement> findBySloIdAndMeasurementTimeBetweenOrderByMeasurementTimeAsc(
            UUID sloId,
            Instant start,
            Instant end
    );

    List<SliMeasurement> findBySloIdOrderByMeasurementTimeDesc(UUID sloId);
}
