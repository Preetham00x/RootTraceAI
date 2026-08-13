package com.roottrace.commandcenter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roottrace.commandcenter.dto.ExecutiveReliabilityAdvisorAiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GeminiExecutiveReliabilityServiceTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("Should return fallback synthesis when ChatClient is null")
    void testGenerateExecutiveAdvice_NullClient_Fallback() {
        GeminiExecutiveReliabilityService service = new GeminiExecutiveReliabilityService(null);

        ExecutiveReliabilityAdvisorAiResponse response = service.generateExecutiveAdvice("Sample Prompt with 78.5 score");

        assertThat(response).isNotNull();
        assertThat(response.executiveSummary()).contains("Executive reliability posture");
        assertThat(response.keyConcerns()).isNotEmpty();
        assertThat(response.recommendedActions()).isNotEmpty();
    }

    @Test
    @DisplayName("Should parse structured JSON response from ChatClient when available")
    void testGenerateExecutiveAdvice_WithChatClient_Success() {
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);

        String json = """
                {
                  "executiveSummary": "Overall system stability is strong.",
                  "keyConcerns": ["Elevated latency on auth-service"],
                  "servicesRequiringAttention": [
                    {
                      "serviceName": "auth-service",
                      "reason": "SLO warning",
                      "urgency": "MEDIUM"
                    }
                  ],
                  "recommendedActions": [
                    {
                      "action": "Scale database replicas",
                      "expectedImpact": "Reduces p99 latency",
                      "priority": "HIGH"
                    }
                  ],
                  "positiveSignals": ["Payment processing achieved 99.99% availability"]
                }
                """;
        when(callSpec.content()).thenReturn(json);

        GeminiExecutiveReliabilityService service = new GeminiExecutiveReliabilityService(chatClient);

        ExecutiveReliabilityAdvisorAiResponse response = service.generateExecutiveAdvice("Test prompt");

        assertThat(response).isNotNull();
        assertThat(response.executiveSummary()).isEqualTo("Overall system stability is strong.");
        assertThat(response.keyConcerns()).contains("Elevated latency on auth-service");
        assertThat(response.servicesRequiringAttention()).hasSize(1);
        assertThat(response.recommendedActions()).hasSize(1);
        assertThat(response.positiveSignals()).contains("Payment processing achieved 99.99% availability");
    }

    @Test
    @DisplayName("Should gracefully fallback if ChatClient throws exception")
    void testGenerateExecutiveAdvice_Exception_Fallback() {
        ChatClient chatClient = mock(ChatClient.class);
        when(chatClient.prompt()).thenThrow(new RuntimeException("API error"));

        GeminiExecutiveReliabilityService service = new GeminiExecutiveReliabilityService(chatClient);

        ExecutiveReliabilityAdvisorAiResponse response = service.generateExecutiveAdvice("Test prompt");

        assertThat(response).isNotNull();
        assertThat(response.executiveSummary()).contains("Executive reliability posture");
    }
}
