package com.roottrace.ai.diagnosis;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AiDiagnosisCitationRepository extends JpaRepository<AiDiagnosisCitation, UUID> {
}
