package com.roottrace.intelligence;

import com.roottrace.ai.diagnosis.AiDiagnosis;
import com.roottrace.ai.diagnosis.DiagnosisException;
import com.roottrace.ai.exception.AiServiceException;
import com.roottrace.incident.Incident;
import com.roottrace.intelligence.dto.CorrelatedIncidentResponse;
import com.roottrace.intelligence.dto.IncidentBriefingAiResponse;
import com.roottrace.investigation.InvestigationPlan;
import com.roottrace.postmortem.Postmortem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GeminiBriefingService {

    private static final Logger log = LoggerFactory.getLogger(GeminiBriefingService.class);

    private final ChatClient chatClient;
    private final BriefingPromptBuilder promptBuilder;

    public GeminiBriefingService(
            @Autowired(required = false) ChatClient chatClient,
            BriefingPromptBuilder promptBuilder) {
        this.chatClient = chatClient;
        this.promptBuilder = promptBuilder;
    }

    public IncidentBriefingAiResponse generateBriefing(
            Incident targetIncident,
            List<CorrelatedIncidentResponse> correlatedIncidents,
            List<AiDiagnosis> historicalDiagnoses,
            List<InvestigationPlan> historicalPlans,
            List<Postmortem> historicalPostmortems) {

        if (chatClient == null) {
            throw new DiagnosisException("AI chat client is not configured");
        }

        BeanOutputConverter<IncidentBriefingAiResponse> converter =
                new BeanOutputConverter<>(IncidentBriefingAiResponse.class);

        String prompt = promptBuilder.buildPrompt(
                targetIncident,
                correlatedIncidents,
                historicalDiagnoses,
                historicalPlans,
                historicalPostmortems,
                converter.getFormat()
        );

        long startTime = System.currentTimeMillis();
        log.info("Sending SRE Briefing prompt to Gemini for incident: {}", targetIncident.getId());

        try {
            String rawResponse = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            long duration = System.currentTimeMillis() - startTime;
            log.info("Received Gemini SRE Briefing response in {} ms for incident: {}", duration, targetIncident.getId());

            if (rawResponse == null || rawResponse.isBlank()) {
                throw new AiServiceException("Gemini returned an empty response for incident briefing");
            }

            IncidentBriefingAiResponse response = converter.convert(rawResponse);

            if (response == null || response.executiveSummary() == null || response.executiveSummary().isBlank()) {
                throw new AiServiceException("Failed to parse valid executive summary from AI briefing output");
            }

            return response;
        } catch (AiServiceException | DiagnosisException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Failed to generate incident briefing with Gemini for incident: {}", targetIncident.getId(), ex);
            throw new AiServiceException("Failed to generate incident briefing from AI: " + ex.getMessage(), ex);
        }
    }
}
