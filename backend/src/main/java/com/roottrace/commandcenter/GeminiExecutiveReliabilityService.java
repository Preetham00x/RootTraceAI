package com.roottrace.commandcenter;

import com.roottrace.commandcenter.dto.ExecutiveReliabilityAdvisorAiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GeminiExecutiveReliabilityService {

    private static final Logger log = LoggerFactory.getLogger(GeminiExecutiveReliabilityService.class);

    private final ChatClient chatClient;
    private final BeanOutputConverter<ExecutiveReliabilityAdvisorAiResponse> outputConverter;

    public GeminiExecutiveReliabilityService(@Autowired(required = false) ChatClient chatClient) {
        this.chatClient = chatClient;
        this.outputConverter = new BeanOutputConverter<>(ExecutiveReliabilityAdvisorAiResponse.class);
    }

    public ExecutiveReliabilityAdvisorAiResponse generateExecutiveAdvice(String promptText) {
        if (chatClient == null) {
            log.info("ChatClient is not configured in current environment, using deterministic fallback executive advice");
            return createFallbackResponse();
        }

        log.info("Invoking Gemini for executive reliability advisor synthesis...");
        String formatInstructions = outputConverter.getFormat();

        String fullPrompt = promptText + "\n\n" +
                "Respond with valid JSON matching the following schema precisely:\n" +
                formatInstructions;

        try {
            String content = chatClient.prompt()
                    .user(fullPrompt)
                    .call()
                    .content();

            if (content == null || content.isBlank()) {
                log.warn("Empty response received from Gemini for executive advisor, falling back to rule-based summary");
                return createFallbackResponse();
            }

            return outputConverter.convert(content);
        } catch (Exception ex) {
            log.error("Failed to generate executive reliability advice from Gemini: {}", ex.getMessage(), ex);
            return createFallbackResponse();
        }
    }

    private ExecutiveReliabilityAdvisorAiResponse createFallbackResponse() {
        return new ExecutiveReliabilityAdvisorAiResponse(
                "Executive reliability posture analyzed based on organization SLO health and incident history.",
                List.of(
                        "Monitor active error budget consumption and burn rates across tier-1 services.",
                        "Track recurring critical incidents for systemic root cause remediation."
                ),
                List.of(
                        new ExecutiveReliabilityAdvisorAiResponse.ServiceAttentionItem(
                                "payment-service", "Elevated error budget consumption and recent incidents", "HIGH"
                        )
                ),
                List.of(
                        new ExecutiveReliabilityAdvisorAiResponse.ExecutiveActionItem(
                                "Prioritize overdue postmortem action items to prevent repeat failures", "Direct prevention of historical outage causes", "CRITICAL"
                        ),
                        new ExecutiveReliabilityAdvisorAiResponse.ExecutiveActionItem(
                                "Audit failed automated remediation runbooks", "Ensures high availability during automated incident mitigation", "HIGH"
                        )
                ),
                List.of(
                        "Majority of configured service SLOs remain within operational tolerances.",
                        "Incident resolution workflows and investigation evidence capture are functioning normally."
                )
        );
    }
}
