package com.roottrace.slo;

import com.roottrace.slo.dto.ReliabilityAdvisorAiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GeminiReliabilityAdvisorService {

    private static final Logger log = LoggerFactory.getLogger(GeminiReliabilityAdvisorService.class);

    private final ChatClient chatClient;
    private final BeanOutputConverter<ReliabilityAdvisorAiResponse> outputConverter;

    public GeminiReliabilityAdvisorService(@Autowired(required = false) ChatClient chatClient) {
        this.chatClient = chatClient;
        this.outputConverter = new BeanOutputConverter<>(ReliabilityAdvisorAiResponse.class);
    }

    public ReliabilityAdvisorAiResponse generateAdvisorRecommendations(String promptText) {
        if (chatClient == null) {
            log.info("ChatClient is not configured in current environment, using rule-based fallback advisory");
            return createFallbackResponse();
        }

        log.info("Invoking Gemini for reliability advisor analysis...");
        String formatInstructions = outputConverter.getFormat();

        String fullPrompt = promptText + "\n\n" +
                "Respond with valid JSON matching the following schema precisely:\n" +
                formatInstructions;

        try {
            ChatResponse response = chatClient.prompt()
                    .user(fullPrompt)
                    .call()
                    .chatResponse();

            if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
                log.warn("Empty response received from Gemini for reliability advisor, falling back to rule-based summary");
                return createFallbackResponse();
            }

            String content = response.getResult().getOutput().getText();
            if (content == null || content.isBlank()) {
                return createFallbackResponse();
            }

            return outputConverter.convert(content);
        } catch (Exception ex) {
            log.error("Failed to generate reliability advisor recommendations from Gemini: {}", ex.getMessage(), ex);
            return createFallbackResponse();
        }
    }

    private ReliabilityAdvisorAiResponse createFallbackResponse() {
        return new ReliabilityAdvisorAiResponse(
                "Service reliability posture analyzed based on active SLOs and incident history.",
                List.of(
                        "Monitor active error budget consumption and burn rates.",
                        "Track recurring incidents for root cause resolution."
                ),
                List.of(
                        "Address open postmortem action items to prevent recurrence.",
                        "Implement automated alerting on 2x burn rate thresholds.",
                        "Review connection pool and capacity limits under load."
                ),
                "MEDIUM"
        );
    }
}
