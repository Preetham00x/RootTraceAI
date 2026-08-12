package com.roottrace.investigation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvestigationStepRepository extends JpaRepository<InvestigationStep, UUID> {

    Optional<InvestigationStep> findByIdAndPlanId(UUID id, UUID planId);
}
