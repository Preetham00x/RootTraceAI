package com.roottrace.postmortem;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PostmortemRepository extends JpaRepository<Postmortem, UUID> {

    Optional<Postmortem> findByIncidentId(UUID incidentId);

    @Query("SELECT p FROM Postmortem p LEFT JOIN FETCH p.actionItems WHERE p.incident.id = :incidentId")
    Optional<Postmortem> findByIncidentIdWithActionItems(@Param("incidentId") UUID incidentId);

    @Query("SELECT p FROM Postmortem p LEFT JOIN FETCH p.actionItems WHERE p.id = :id")
    Optional<Postmortem> findByIdWithActionItems(@Param("id") UUID id);
}
