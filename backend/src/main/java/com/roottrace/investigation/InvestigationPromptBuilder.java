package com.roottrace.investigation;

import com.roottrace.ai.diagnosis.AiDiagnosis;
import com.roottrace.incident.Incident;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class InvestigationPromptBuilder {

    public String buildPrompt(Incident incident, AiDiagnosis diagnosis, String formatInstructions) {
        String recommendations = diagnosis.getRecommendedActions() != null && !diagnosis.getRecommendedActions().isEmpty()
                ? String.join("\n- ", diagnosis.getRecommendedActions())
                : "None provided";

        String evidenceSummary = diagnosis.getEvidence() != null && !diagnosis.getEvidence().isEmpty()
                ? diagnosis.getEvidence().stream()
                    .map(e -> "- Reason: " + e.getReason() + " (Relevance: " + e.getRelevanceScore() + ")")
                    .collect(Collectors.joining("\n"))
                : "None provided";

        String citationSummary = diagnosis.getCitations() != null && !diagnosis.getCitations().isEmpty()
                ? diagnosis.getCitations().stream()
                    .map(c -> "- Document: " + c.getDocumentTitle() + " | Section: " + c.getSectionPath())
                    .collect(Collectors.joining("\n"))
                : "None provided";

        return """
                SYSTEM INSTRUCTIONS:
                You are an expert site reliability engineer (SRE) and incident response lead.
                Your task is to convert an incident diagnosis and its recommended actions into a structured, step-by-step investigation and remediation plan.

                CRITICAL RULES:
                1. Break down recommendations into discrete, ordered, and concrete operational steps.
                2. Each step must have a clear title (action-oriented) and a detailed description outlining what commands, metrics, or logs to inspect or what actions to take.
                3. Order the steps logically: verification/triage first, mitigation second, deep diagnosis third, and permanent fix/post-verification last.
                4. Give the overall plan a concise, descriptive title.
                5. Do not include sensitive information like passwords or credentials.

                INCIDENT CONTEXT:
                Title: %s
                Service: %s
                Environment: %s
                Severity: %s
                Status: %s
                Description:
                %s

                DIAGNOSIS CONTEXT:
                Probable Root Cause: %s
                Confidence: %.2f
                Summary: %s

                RECOMMENDED ACTIONS FROM DIAGNOSIS:
                - %s

                SUPPORTING EVIDENCE & CITATIONS:
                Evidence:
                %s
                Citations:
                %s

                FORMAT INSTRUCTIONS:
                %s
                """.formatted(
                incident.getTitle(),
                incident.getService(),
                incident.getEnvironment() != null ? incident.getEnvironment() : "N/A",
                incident.getSeverity(),
                incident.getStatus(),
                incident.getDescription(),
                diagnosis.getProbableRootCause(),
                diagnosis.getConfidence() != null ? diagnosis.getConfidence() : 0.0,
                diagnosis.getSummary(),
                recommendations,
                evidenceSummary,
                citationSummary,
                formatInstructions
        );
    }
}
