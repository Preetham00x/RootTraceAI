package com.roottrace.integration;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RunbookExecutionRepository extends JpaRepository<RunbookExecution, UUID> {

    List<RunbookExecution> findByIncidentIdOrderByCreatedAtDesc(UUID incidentId);
}
