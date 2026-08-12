package com.roottrace.ai.diagnosis;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AiDiagnosisFeedbackRepository extends JpaRepository<AiDiagnosisFeedback, UUID> {
    boolean existsByDiagnosisIdAndUserId(UUID diagnosisId, UUID userId);

    @Query("SELECT COUNT(f) FROM AiDiagnosisFeedback f WHERE f.helpful = true")
    long countHelpful();

    @Query("SELECT COUNT(f) FROM AiDiagnosisFeedback f WHERE f.helpful = false")
    long countUnhelpful();
}
