package com.roottrace.postmortem;

import com.roottrace.ai.diagnosis.AiDiagnosis;
import com.roottrace.ai.diagnosis.DiagnosisException;
import com.roottrace.ai.exception.AiServiceException;
import com.roottrace.incident.Incident;
import com.roottrace.investigation.InvestigationPlan;
import com.roottrace.postmortem.dto.PostmortemAiResponse;
import com.roottrace.postmortem.dto.PostmortemTimelineEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GeminiPostmortemService {

    private static final Logger log = LoggerFactory.getLogger(GeminiPostmortemService.class);

    private final ChatClient chatClient;
    private final PostmortemPromptBuilder promptBuilder;

    public GeminiPostmortemService(
            @Autowired(required = false) ChatClient chatClient,
            PostmortemPromptBuilder promptBuilder) {
        this.chatClient = chatClient;
        this.promptBuilder = promptBuilder;
    }

    public PostmortemAiResponse generatePostmortem(
            Incident incident,
            AiDiagnosis diagnosis,
            List<InvestigationPlan> investigationPlans,
            List<PostmortemTimelineEntry> timeline) {

        if (chatClient == null) {
            throw new DiagnosisException("AI chat client is not configured");
        }

        BeanOutputConverter<PostmortemAiResponse> converter =
                new BeanOutputConverter<>(PostmortemAiResponse.class);

        String prompt = promptBuilder.buildPrompt(incident, diagnosis, investigationPlans, timeline, converter.getFormat());

        long startTime = System.currentTimeMillis();
        log.info("Sending postmortem prompt to Gemini for incident: {}", incident.getId());

        try {
            String rawResponse = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            long duration = System.currentTimeMillis() - startTime;
            log.info("Received Gemini postmortem response in {} ms for incident: {}", duration, incident.getId());

            if (rawResponse == null || rawResponse.isBlank()) {
                throw new AiServiceException("Gemini returned an empty response for postmortem");
            }

            PostmortemAiResponse response = converter.convert(rawResponse);

            if (response == null || response.summary() == null || response.summary().isBlank()
                    || response.rootCauseAnalysis() == null || response.rootCauseAnalysis().isBlank()) {
                throw new AiServiceException("Failed to parse valid postmortem summary or root cause from AI output");
            }

            return response;
        } catch (AiServiceException | DiagnosisException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Failed to generate postmortem with Gemini for incident: {}", incident.getId(), ex);
            throw new AiServiceException("Failed to generate postmortem from AI: " + ex.getMessage(), ex);
        }
    }
}
