package com.roottrace.ai.diagnosis;

import com.roottrace.ai.diagnosis.dto.DiagnosisDetailResponse;
import com.roottrace.ai.diagnosis.dto.DiagnosisResultResponse;
import com.roottrace.ai.diagnosis.dto.DiagnosisSummaryResponse;

import java.util.List;
import java.util.UUID;

/**
 * Service for managing AI incident diagnoses.
 */
public interface DiagnosisService {

    /**
     * Generates a new AI diagnosis for an incident.
     */
    DiagnosisResultResponse diagnose(UUID incidentId);

    /**
     * Retrieves all diagnoses for an incident.
     */
    List<DiagnosisSummaryResponse> getDiagnoses(UUID incidentId);

    /**
     * Retrieves the details of a specific diagnosis.
     */
    DiagnosisDetailResponse getDiagnosis(UUID incidentId, UUID diagnosisId);
}
