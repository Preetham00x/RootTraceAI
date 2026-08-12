package com.roottrace.postmortem;

import com.roottrace.ai.diagnosis.AiDiagnosis;
import com.roottrace.incident.Incident;
import com.roottrace.incident.IncidentSeverity;
import com.roottrace.incident.IncidentStatus;
import com.roottrace.investigation.InvestigationPlan;
import com.roottrace.investigation.InvestigationStep;
import com.roottrace.postmortem.dto.PostmortemTimelineEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class PostmortemPromptBuilderTest {

    private PostmortemPromptBuilder promptBuilder;

    @BeforeEach
    void setUp() {
        promptBuilder = new PostmortemPromptBuilder();
    }

    @Test
    @DisplayName("Should assemble full prompt with incident, diagnosis, plans, timeline, and blameless instructions")
    void testBuildPrompt_FullContext() {
        Incident incident = Mockito.mock(Incident.class);
        when(incident.getTitle()).thenReturn("Payment Gateway 504 Gateway Timeout");
        when(incident.getService()).thenReturn("payment-service");
        when(incident.getEnvironment()).thenReturn("production");
        when(incident.getSeverity()).thenReturn(IncidentSeverity.CRITICAL);
        when(incident.getStatus()).thenReturn(IncidentStatus.RESOLVED);
        when(incident.getCreatedAt()).thenReturn(Instant.parse("2026-08-12T10:00:00Z"));
        when(incident.getResolvedAt()).thenReturn(Instant.parse("2026-08-12T10:45:00Z"));
        when(incident.getDescription()).thenReturn("Payment processing latency spiked to 30s.");
        when(incident.getResolution()).thenReturn("Scaled payment worker pods and rotated Stripe API token.");

        AiDiagnosis diagnosis = Mockito.mock(AiDiagnosis.class);
        when(diagnosis.getProbableRootCause()).thenReturn("Connection leak in outbound HTTP client connection pool.");
        when(diagnosis.getConfidence()).thenReturn(0.95);
        when(diagnosis.getSummary()).thenReturn("Outbound payment connections were blocked.");
        when(diagnosis.getContributingFactors()).thenReturn(List.of("Missing socket timeout", "High traffic spike"));
        when(diagnosis.getRecommendedActions()).thenReturn(List.of("Set connectTimeout=2000ms", "Enable circuit breaker"));

        InvestigationPlan plan = Mockito.mock(InvestigationPlan.class);
        when(plan.getTitle()).thenReturn("Mitigation Runbook");
        InvestigationStep step = Mockito.mock(InvestigationStep.class);
        when(step.getStepOrder()).thenReturn(1);
        when(step.getTitle()).thenReturn("Check TCP connections");
        when(step.getDescription()).thenReturn("Run netstat in pod");
        when(step.getStatus()).thenReturn(com.roottrace.investigation.InvestigationStepStatus.COMPLETED);
        when(step.getEvidence()).thenReturn("1000 connections in CLOSE_WAIT state");
        when(plan.getSteps()).thenReturn(List.of(step));

        List<PostmortemTimelineEntry> timeline = List.of(
                new PostmortemTimelineEntry(Instant.parse("2026-08-12T10:00:00Z"), "Incident opened", "INCIDENT_CREATION"),
                new PostmortemTimelineEntry(Instant.parse("2026-08-12T10:45:00Z"), "Incident resolved", "RESOLUTION")
        );

        String formatInstructions = "{\"title\": \"...\"}";

        String prompt = promptBuilder.buildPrompt(incident, diagnosis, List.of(plan), timeline, formatInstructions);

        assertThat(prompt).contains("Payment Gateway 504 Gateway Timeout");
        assertThat(prompt).contains("payment-service");
        assertThat(prompt).contains("production");
        assertThat(prompt).contains("CRITICAL");
        assertThat(prompt).contains("Scaled payment worker pods and rotated Stripe API token.");
        assertThat(prompt).contains("Connection leak in outbound HTTP client connection pool.");
        assertThat(prompt).contains("0.95");
        assertThat(prompt).contains("Missing socket timeout");
        assertThat(prompt).contains("1000 connections in CLOSE_WAIT state");
        assertThat(prompt).contains("Incident opened");
        assertThat(prompt).contains("Incident resolved");
        assertThat(prompt).contains("BLAMELESS");
        assertThat(prompt).contains(formatInstructions);
    }

    @Test
    @DisplayName("Should handle missing diagnosis and investigation plans gracefully")
    void testBuildPrompt_MissingDiagnosisAndPlans() {
        Incident incident = Mockito.mock(Incident.class);
        when(incident.getTitle()).thenReturn("Frontend Cache Invalidation Bug");
        when(incident.getService()).thenReturn("web-ui");
        when(incident.getEnvironment()).thenReturn("staging");
        when(incident.getSeverity()).thenReturn(IncidentSeverity.MEDIUM);
        when(incident.getStatus()).thenReturn(IncidentStatus.RESOLVED);
        when(incident.getCreatedAt()).thenReturn(Instant.parse("2026-08-12T12:00:00Z"));
        when(incident.getResolvedAt()).thenReturn(Instant.parse("2026-08-12T12:15:00Z"));
        when(incident.getDescription()).thenReturn("Static asset cache headers expired.");
        when(incident.getResolution()).thenReturn("Purged Cloudflare CDN cache.");

        String prompt = promptBuilder.buildPrompt(incident, null, null, null, "{\"title\": \"...\"}");

        assertThat(prompt).contains("Frontend Cache Invalidation Bug");
        assertThat(prompt).contains("No AI Diagnosis available for this incident.");
        assertThat(prompt).contains("No Investigation Plans recorded for this incident.");
        assertThat(prompt).contains("No timeline events recorded.");
    }
}
