package com.roottrace.postmortem;

import com.roottrace.ai.diagnosis.AiDiagnosis;
import com.roottrace.ai.diagnosis.DiagnosisException;
import com.roottrace.ai.exception.AiServiceException;
import com.roottrace.incident.Incident;
import com.roottrace.postmortem.dto.PostmortemAiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GeminiPostmortemServiceTest {

    private ChatClient chatClient;
    private PostmortemPromptBuilder promptBuilder;
    private GeminiPostmortemService service;
    private ChatClient.ChatClientRequestSpec requestSpec;
    private ChatClient.CallResponseSpec callSpec;

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class);
        promptBuilder = mock(PostmortemPromptBuilder.class);
        requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        callSpec = mock(ChatClient.CallResponseSpec.class);

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);

        service = new GeminiPostmortemService(chatClient, promptBuilder);
    }

    @Test
    @DisplayName("Should parse structured AI response successfully")
    void testGeneratePostmortem_Success() {
        Incident incident = mock(Incident.class);
        AiDiagnosis diagnosis = mock(AiDiagnosis.class);

        when(promptBuilder.buildPrompt(any(), any(), any(), any(), anyString())).thenReturn("Built prompt");

        String validJson = """
                {
                  "title": "Postmortem: Payment Gateway Outage",
                  "summary": "Payment gateway experienced 45 minutes of downtime due to socket pool exhaustion.",
                  "impactSummary": "2,400 checkout transactions failed with HTTP 504.",
                  "rootCauseAnalysis": "Default connection timeout allowed idle connections to starve the pool.",
                  "resolutionSummary": "Updated socket timeout to 2s and deployed hotfix.",
                  "lessonsLearned": [
                    "Need automated alerts for connection pool saturation > 80%",
                    "Circuit breakers prevented cascading failure to cart service"
                  ],
                  "actionItems": [
                    {
                      "title": "Configure Connection Pool Alert",
                      "description": "Create Datadog monitor for payment-service pool size.",
                      "category": "DETECT",
                      "priority": "HIGH"
                    },
                    {
                      "title": "Implement Circuit Breaker",
                      "description": "Add Resilience4j circuit breaker on Stripe client.",
                      "category": "PREVENT",
                      "priority": "CRITICAL"
                    }
                  ]
                }
                """;

        when(callSpec.content()).thenReturn(validJson);

        PostmortemAiResponse response = service.generatePostmortem(incident, diagnosis, List.of(), List.of());

        assertThat(response).isNotNull();
        assertThat(response.title()).isEqualTo("Postmortem: Payment Gateway Outage");
        assertThat(response.summary()).contains("45 minutes of downtime");
        assertThat(response.rootCauseAnalysis()).contains("Default connection timeout");
        assertThat(response.lessonsLearned()).hasSize(2);
        assertThat(response.actionItems()).hasSize(2);
        assertThat(response.actionItems().get(0).category()).isEqualTo("DETECT");
        assertThat(response.actionItems().get(1).priority()).isEqualTo("CRITICAL");
    }

    @Test
    @DisplayName("Should throw AiServiceException when AI returns empty or null response")
    void testGeneratePostmortem_EmptyResponse() {
        Incident incident = mock(Incident.class);
        AiDiagnosis diagnosis = mock(AiDiagnosis.class);

        when(promptBuilder.buildPrompt(any(), any(), any(), any(), anyString())).thenReturn("Built prompt");
        when(callSpec.content()).thenReturn("");

        assertThatThrownBy(() -> service.generatePostmortem(incident, diagnosis, List.of(), List.of()))
                .isInstanceOf(AiServiceException.class)
                .hasMessageContaining("Gemini returned an empty response");
    }

    @Test
    @DisplayName("Should throw AiServiceException when AI returns malformed JSON with missing required fields")
    void testGeneratePostmortem_MalformedJson() {
        Incident incident = mock(Incident.class);
        AiDiagnosis diagnosis = mock(AiDiagnosis.class);

        when(promptBuilder.buildPrompt(any(), any(), any(), any(), anyString())).thenReturn("Built prompt");
        when(callSpec.content()).thenReturn("{\"title\": \"Incomplete Postmortem\"}");

        assertThatThrownBy(() -> service.generatePostmortem(incident, diagnosis, List.of(), List.of()))
                .isInstanceOf(AiServiceException.class)
                .hasMessageContaining("Failed to parse valid postmortem summary or root cause");
    }

    @Test
    @DisplayName("Should throw DiagnosisException when ChatClient is not configured")
    void testGeneratePostmortem_ChatClientNull() {
        GeminiPostmortemService unconfiguredService = new GeminiPostmortemService(null, promptBuilder);
        Incident incident = mock(Incident.class);
        AiDiagnosis diagnosis = mock(AiDiagnosis.class);

        assertThatThrownBy(() -> unconfiguredService.generatePostmortem(incident, diagnosis, List.of(), List.of()))
                .isInstanceOf(DiagnosisException.class)
                .hasMessageContaining("AI chat client is not configured");
    }
}
