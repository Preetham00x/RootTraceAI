package com.roottrace.ai.diagnosis;

import com.roottrace.incident.Incident;
import com.roottrace.incident.IncidentSeverity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DiagnosisPromptBuilderTest {

    private final DiagnosisPromptBuilder builder = new DiagnosisPromptBuilder();

    @Test
    void buildPrompt_ShouldIncludeAllComponents() {
        Incident incident = mock(Incident.class);
        when(incident.getTitle()).thenReturn("DB Crash");
        when(incident.getService()).thenReturn("db-cluster");
        when(incident.getEnvironment()).thenReturn("prod");
        when(incident.getSeverity()).thenReturn(IncidentSeverity.CRITICAL);
        when(incident.getDescription()).thenReturn("The database ran out of memory.");

        String context = "--- EVIDENCE ITEM [1] ---\nContent:\nOOM killer invoked.";
        String formatInstructions = "{\"type\": \"object\"}";

        String prompt = builder.buildPrompt(incident, context, formatInstructions);

        assertThat(prompt)
                .contains("DB Crash")
                .contains("db-cluster")
                .contains("prod")
                .contains("CRITICAL")
                .contains("The database ran out of memory.")
                .contains("OOM killer invoked.")
                .contains("{\"type\": \"object\"}");
    }
}
