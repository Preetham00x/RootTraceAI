package com.roottrace.postmortem;

import com.roottrace.ai.diagnosis.AiDiagnosis;
import com.roottrace.incident.Incident;
import com.roottrace.investigation.InvestigationPlan;
import com.roottrace.investigation.InvestigationStep;
import com.roottrace.postmortem.dto.PostmortemTimelineEntry;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class PostmortemPromptBuilder {

    public String buildPrompt(
            Incident incident,
            AiDiagnosis diagnosis,
            List<InvestigationPlan> investigationPlans,
            List<PostmortemTimelineEntry> timeline,
            String formatInstructions) {

        String diagnosisSection = "No AI Diagnosis available for this incident.";
        if (diagnosis != null) {
            String factors = (diagnosis.getContributingFactors() != null && !diagnosis.getContributingFactors().isEmpty())
                    ? String.join("\n  - ", diagnosis.getContributingFactors())
                    : "None recorded";
            String recommendations = (diagnosis.getRecommendedActions() != null && !diagnosis.getRecommendedActions().isEmpty())
                    ? String.join("\n  - ", diagnosis.getRecommendedActions())
                    : "None recorded";

            diagnosisSection = """
                    - Probable Root Cause: %s
                    - Confidence Score: %.2f
                    - Summary: %s
                    - Contributing Factors:
                      - %s
                    - Recommended Actions:
                      - %s
                    """.formatted(
                    diagnosis.getProbableRootCause(),
                    diagnosis.getConfidence() != null ? diagnosis.getConfidence() : 0.0,
                    diagnosis.getSummary(),
                    factors,
                    recommendations
            );
        }

        String investigationSection = "No Investigation Plans recorded for this incident.";
        if (investigationPlans != null && !investigationPlans.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (InvestigationPlan plan : investigationPlans) {
                sb.append("Plan: ").append(plan.getTitle()).append("\n");
                if (plan.getSteps() != null) {
                    for (InvestigationStep step : plan.getSteps()) {
                        sb.append("  Step ").append(step.getStepOrder()).append(" [").append(step.getStatus()).append("]: ")
                                .append(step.getTitle()).append(" - ").append(step.getDescription());
                        if (step.getEvidence() != null && !step.getEvidence().isBlank()) {
                            sb.append(" (Evidence: ").append(step.getEvidence()).append(")");
                        }
                        sb.append("\n");
                    }
                }
            }
            investigationSection = sb.toString();
        }

        String timelineSection = "No timeline events recorded.";
        if (timeline != null && !timeline.isEmpty()) {
            timelineSection = timeline.stream()
                    .map(t -> "- " + t.timestamp() + " [" + t.source() + "]: " + t.description())
                    .collect(Collectors.joining("\n"));
        }

        return """
                SYSTEM INSTRUCTIONS:
                You are a Staff Site Reliability Engineer (SRE) and Postmortem facilitator.
                Your task is to write a comprehensive, highly technical, and strictly BLAMELESS postmortem report for a resolved production incident.

                CRITICAL BLAMELESS SRE PRINCIPLES:
                1. Assume people did the best they could with the information they had at the time.
                2. Do NOT assign blame to individuals (e.g., never say 'developer X made an error' or 'operator neglected').
                3. Focus on systemic, architectural, telemetry, and process vulnerabilities that allowed the incident to occur and take time to resolve.
                4. Distinguish confirmed facts supported by evidence from hypotheses.
                5. Do NOT invent or fabricate facts, metrics, timeline entries, or log outputs that are not in the context.
                6. If data or evidence is missing, state clearly that it is unavailable.
                7. Categorize each proposed action item strictly as PREVENT, DETECT, MITIGATE, or PROCESS with priority LOW, MEDIUM, HIGH, or CRITICAL.

                INCIDENT DETAILS:
                Title: %s
                Service: %s
                Environment: %s
                Severity: %s
                Status: %s
                Created At: %s
                Resolved At: %s
                Description:
                %s
                Resolution Summary:
                %s

                DIAGNOSIS CONTEXT:
                %s

                INVESTIGATION HISTORY & EVIDENCE:
                %s

                RECONSTRUCTED TIMELINE:
                %s

                FORMAT INSTRUCTIONS:
                %s
                """.formatted(
                incident.getTitle(),
                incident.getService(),
                incident.getEnvironment() != null ? incident.getEnvironment() : "N/A",
                incident.getSeverity(),
                incident.getStatus(),
                incident.getCreatedAt(),
                incident.getResolvedAt() != null ? incident.getResolvedAt() : "N/A",
                incident.getDescription(),
                incident.getResolution() != null ? incident.getResolution() : "None provided",
                diagnosisSection,
                investigationSection,
                timelineSection,
                formatInstructions
        );
    }
}
