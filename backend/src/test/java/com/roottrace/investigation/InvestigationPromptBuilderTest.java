package com.roottrace.investigation;

import com.roottrace.ai.diagnosis.AiDiagnosis;
import com.roottrace.ai.diagnosis.AiDiagnosisCitation;
import com.roottrace.ai.diagnosis.AiDiagnosisEvidence;
import com.roottrace.incident.Incident;
import com.roottrace.incident.IncidentSeverity;
import com.roottrace.incident.IncidentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class InvestigationPromptBuilderTest {

    private InvestigationPromptBuilder promptBuilder;

    @BeforeEach
    void setUp() {
        promptBuilder = new InvestigationPromptBuilder();
    }

    @Test
    @DisplayName("Should include incident context, diagnosis root cause, and recommendations in prompt")
    void testBuildPrompt_FullContext() {
        Incident incident = Mockito.mock(Incident.class);
        when(incident.getTitle()).thenReturn("Database Connection Pool Exhausted");
        when(incident.getService()).thenReturn("order-service");
        when(incident.getEnvironment()).thenReturn("production");
        when(incident.getSeverity()).thenReturn(IncidentSeverity.CRITICAL);
        when(incident.getStatus()).thenReturn(IncidentStatus.INVESTIGATING);
        when(incident.getDescription()).thenReturn("Order service experiencing HikariPool-1 connection timeouts.");

        AiDiagnosis diagnosis = Mockito.mock(AiDiagnosis.class);
        when(diagnosis.getProbableRootCause()).thenReturn("HikariCP maximumPoolSize is too low for current traffic spikes.");
        when(diagnosis.getConfidence()).thenReturn(0.92);
        when(diagnosis.getSummary()).thenReturn("Database pool leak or exhaustion under heavy load.");
        when(diagnosis.getRecommendedActions()).thenReturn(List.of(
                "Increase maximumPoolSize to 50 in application.yml",
                "Enable leakDetectionThreshold=2000ms"
        ));

        AiDiagnosisEvidence evidence = Mockito.mock(AiDiagnosisEvidence.class);
        when(evidence.getReason()).thenReturn("HikariPool-1 timeout log match");
        when(evidence.getRelevanceScore()).thenReturn(0.88);
        when(diagnosis.getEvidence()).thenReturn(List.of(evidence));

        AiDiagnosisCitation citation = Mockito.mock(AiDiagnosisCitation.class);
        when(citation.getDocumentTitle()).thenReturn("Database Runbook");
        when(citation.getSectionPath()).thenReturn("Connection Pooling > Tuning");
        when(diagnosis.getCitations()).thenReturn(List.of(citation));

        String formatInstructions = "{\"title\": \"...\", \"steps\": []}";

        String prompt = promptBuilder.buildPrompt(incident, diagnosis, formatInstructions);

        assertThat(prompt).contains("Database Connection Pool Exhausted");
        assertThat(prompt).contains("order-service");
        assertThat(prompt).contains("production");
        assertThat(prompt).contains("CRITICAL");
        assertThat(prompt).contains("HikariCP maximumPoolSize is too low for current traffic spikes.");
        assertThat(prompt).contains("0.92");
        assertThat(prompt).contains("Increase maximumPoolSize to 50 in application.yml");
        assertThat(prompt).contains("Enable leakDetectionThreshold=2000ms");
        assertThat(prompt).contains("HikariPool-1 timeout log match");
        assertThat(prompt).contains("Database Runbook");
        assertThat(prompt).contains(formatInstructions);
    }
}
