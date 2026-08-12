package com.roottrace.investigation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvestigationPlanRepository extends JpaRepository<InvestigationPlan, UUID> {

    @Query("SELECT p FROM InvestigationPlan p LEFT JOIN FETCH p.steps WHERE p.incident.id = :incidentId ORDER BY p.createdAt DESC")
    List<InvestigationPlan> findByIncidentIdWithSteps(@Param("incidentId") UUID incidentId);

    @Query("SELECT p FROM InvestigationPlan p LEFT JOIN FETCH p.steps WHERE p.id = :id AND p.incident.id = :incidentId")
    Optional<InvestigationPlan> findByIdAndIncidentIdWithSteps(@Param("id") UUID id, @Param("incidentId") UUID incidentId);
}
