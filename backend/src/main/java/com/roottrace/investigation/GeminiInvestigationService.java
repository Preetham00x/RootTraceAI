package com.roottrace.investigation;

import com.roottrace.ai.diagnosis.AiDiagnosis;
import com.roottrace.ai.diagnosis.DiagnosisException;
import com.roottrace.ai.exception.AiServiceException;
import com.roottrace.incident.Incident;
import com.roottrace.investigation.dto.InvestigationPlanAiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class GeminiInvestigationService {

    private static final Logger log = LoggerFactory.getLogger(GeminiInvestigationService.class);

    private final ChatClient chatClient;
    private final InvestigationPromptBuilder promptBuilder;

    public GeminiInvestigationService(
            @Autowired(required = false) ChatClient chatClient,
            InvestigationPromptBuilder promptBuilder) {
        this.chatClient = chatClient;
        this.promptBuilder = promptBuilder;
    }

    public InvestigationPlanAiResponse generatePlan(Incident incident, AiDiagnosis diagnosis) {
        if (chatClient == null) {
            throw new DiagnosisException("AI chat client is not configured");
        }

        BeanOutputConverter<InvestigationPlanAiResponse> converter =
                new BeanOutputConverter<>(InvestigationPlanAiResponse.class);

        String prompt = promptBuilder.buildPrompt(incident, diagnosis, converter.getFormat());

        log.info("Sending investigation plan prompt to Gemini for incident: {}", incident.getId());

        try {
            String rawResponse = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            if (rawResponse == null || rawResponse.isBlank()) {
                throw new AiServiceException("Gemini returned an empty response for investigation plan");
            }

            InvestigationPlanAiResponse response = converter.convert(rawResponse);

            if (response == null || response.steps() == null || response.steps().isEmpty()) {
                throw new AiServiceException("Failed to parse valid investigation steps from AI output");
            }

            return response;
        } catch (AiServiceException | DiagnosisException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Failed to generate investigation plan with Gemini", ex);
            throw new AiServiceException("Failed to generate investigation plan from AI: " + ex.getMessage(), ex);
        }
    }
}
