package com.roottrace.investigation;

import com.roottrace.ai.diagnosis.AiDiagnosis;
import com.roottrace.ai.diagnosis.DiagnosisException;
import com.roottrace.ai.exception.AiServiceException;
import com.roottrace.incident.Incident;
import com.roottrace.investigation.dto.InvestigationPlanAiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GeminiInvestigationServiceTest {

    private ChatClient chatClient;
    private InvestigationPromptBuilder promptBuilder;
    private GeminiInvestigationService service;
    private ChatClient.ChatClientRequestSpec requestSpec;
    private ChatClient.CallResponseSpec callSpec;

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class);
        promptBuilder = mock(InvestigationPromptBuilder.class);
        requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        callSpec = mock(ChatClient.CallResponseSpec.class);

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);

        service = new GeminiInvestigationService(chatClient, promptBuilder);
    }

    @Test
    @DisplayName("Should parse structured AI response successfully")
    void testGeneratePlan_Success() {
        Incident incident = mock(Incident.class);
        AiDiagnosis diagnosis = mock(AiDiagnosis.class);

        when(promptBuilder.buildPrompt(any(), any(), anyString())).thenReturn("Built prompt");

        String validJson = """
                {
                  "title": "HikariCP Remediation Plan",
                  "steps": [
                    {
                      "title": "Check RDS Active Connections",
                      "description": "Inspect metrics in CloudWatch dashboard."
                    },
                    {
                      "title": "Increase Pool Size",
                      "description": "Update application.yml maximumPoolSize to 50."
                    }
                  ]
                }
                """;

        when(callSpec.content()).thenReturn(validJson);

        InvestigationPlanAiResponse response = service.generatePlan(incident, diagnosis);

        assertThat(response).isNotNull();
        assertThat(response.title()).isEqualTo("HikariCP Remediation Plan");
        assertThat(response.steps()).hasSize(2);
        assertThat(response.steps().get(0).title()).isEqualTo("Check RDS Active Connections");
        assertThat(response.steps().get(1).description()).isEqualTo("Update application.yml maximumPoolSize to 50.");
    }

    @Test
    @DisplayName("Should throw AiServiceException when AI returns empty or null response")
    void testGeneratePlan_EmptyResponse() {
        Incident incident = mock(Incident.class);
        AiDiagnosis diagnosis = mock(AiDiagnosis.class);

        when(promptBuilder.buildPrompt(any(), any(), anyString())).thenReturn("Built prompt");
        when(callSpec.content()).thenReturn("");

        assertThatThrownBy(() -> service.generatePlan(incident, diagnosis))
                .isInstanceOf(AiServiceException.class)
                .hasMessageContaining("Gemini returned an empty response");
    }

    @Test
    @DisplayName("Should throw AiServiceException when AI returns malformed JSON or empty steps")
    void testGeneratePlan_MalformedJson() {
        Incident incident = mock(Incident.class);
        AiDiagnosis diagnosis = mock(AiDiagnosis.class);

        when(promptBuilder.buildPrompt(any(), any(), anyString())).thenReturn("Built prompt");
        when(callSpec.content()).thenReturn("{\"title\": \"Plan with no steps\", \"steps\": []}");

        assertThatThrownBy(() -> service.generatePlan(incident, diagnosis))
                .isInstanceOf(AiServiceException.class)
                .hasMessageContaining("Failed to parse valid investigation steps");
    }

    @Test
    @DisplayName("Should throw DiagnosisException when ChatClient is not configured")
    void testGeneratePlan_ChatClientNull() {
        GeminiInvestigationService unconfiguredService = new GeminiInvestigationService(null, promptBuilder);
        Incident incident = mock(Incident.class);
        AiDiagnosis diagnosis = mock(AiDiagnosis.class);

        assertThatThrownBy(() -> unconfiguredService.generatePlan(incident, diagnosis))
                .isInstanceOf(DiagnosisException.class)
                .hasMessageContaining("AI chat client is not configured");
    }
}
