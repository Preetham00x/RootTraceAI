package com.roottrace.intelligence;

import com.roottrace.ai.diagnosis.AiDiagnosis;
import com.roottrace.incident.Incident;
import com.roottrace.intelligence.dto.CorrelatedIncidentResponse;
import com.roottrace.investigation.InvestigationPlan;
import com.roottrace.investigation.InvestigationStep;
import com.roottrace.postmortem.Postmortem;
import com.roottrace.postmortem.PostmortemActionItem;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BriefingPromptBuilder {

    public String buildPrompt(
            Incident targetIncident,
            List<CorrelatedIncidentResponse> correlatedIncidents,
            List<AiDiagnosis> historicalDiagnoses,
            List<InvestigationPlan> historicalPlans,
            List<Postmortem> historicalPostmortems,
            String formatInstructions) {

        StringBuilder correlatedSb = new StringBuilder();
        if (correlatedIncidents != null && !correlatedIncidents.isEmpty()) {
            for (CorrelatedIncidentResponse r : correlatedIncidents) {
                correlatedSb.append(String.format("- [%s] '%s' (%s, %s) - Sim: %.2f, Comp: %.2f. Resolution: %s\n",
                        r.id(), r.title(), r.service(), r.severity(),
                        r.semanticSimilarity(), r.compositeScore(),
                        r.resolution() != null ? r.resolution() : "N/A"));
            }
        } else {
            correlatedSb.append("No correlated historical incidents found.\n");
        }

        StringBuilder diagnosisSb = new StringBuilder();
        if (historicalDiagnoses != null && !historicalDiagnoses.isEmpty()) {
            for (AiDiagnosis d : historicalDiagnoses) {
                diagnosisSb.append(String.format("- Probable Cause: %s (Confidence: %.2f)\n  Summary: %s\n",
                        d.getProbableRootCause(),
                        d.getConfidence() != null ? d.getConfidence() : 0.0,
                        d.getSummary()));
            }
        } else {
            diagnosisSb.append("No historical diagnoses available.\n");
        }

        StringBuilder investigationSb = new StringBuilder();
        if (historicalPlans != null && !historicalPlans.isEmpty()) {
            for (InvestigationPlan p : historicalPlans) {
                if (p.getSteps() != null) {
                    for (InvestigationStep s : p.getSteps()) {
                        investigationSb.append(String.format("- Step [%s]: %s - %s",
                                s.getStatus(), s.getTitle(), s.getDescription()));
                        if (s.getEvidence() != null && !s.getEvidence().isBlank()) {
                            investigationSb.append(" (Evidence: ").append(s.getEvidence()).append(")");
                        }
                        investigationSb.append("\n");
                    }
                }
            }
        } else {
            investigationSb.append("No historical investigation steps available.\n");
        }

        StringBuilder postmortemSb = new StringBuilder();
        StringBuilder actionItemsSb = new StringBuilder();

        if (historicalPostmortems != null && !historicalPostmortems.isEmpty()) {
            for (Postmortem pm : historicalPostmortems) {
                if (pm.getLessonsLearned() != null) {
                    for (String lesson : pm.getLessonsLearned()) {
                        postmortemSb.append("- ").append(lesson).append("\n");
                    }
                }
                if (pm.getActionItems() != null) {
                    for (PostmortemActionItem item : pm.getActionItems()) {
                        actionItemsSb.append(String.format("- [%s / %s] %s: %s\n",
                                item.getStatus(), item.getPriority(), item.getTitle(), item.getDescription()));
                    }
                }
            }
        }

        if (postmortemSb.length() == 0) {
            postmortemSb.append("No historical postmortem lessons recorded.\n");
        }
        if (actionItemsSb.length() == 0) {
            actionItemsSb.append("No open action items recorded.\n");
        }

        return """
                SYSTEM INSTRUCTIONS:
                You are a Principal Site Reliability Engineer (SRE) generating a high-priority Pre-Investigation Incident Briefing.
                Your task is to synthesize the target incident with historical correlations to assist the on-call engineer in rapid triage.

                STRICT GROUNDING & ANTI-HALLUCINATION RULES:
                1. Use ONLY the operational facts and historical data explicitly provided below.
                2. Do NOT invent logs, metrics, alerts, root causes, external events, or past actions.
                3. If data is absent, state clearly that no historical data exists for that aspect.
                4. Ground all recommended triage actions in the successful past investigation steps and verified root causes.
                5. Keep the briefing blameless, crisp, and directly actionable.

                TARGET INCIDENT:
                Title: %s
                Service: %s
                Severity: %s
                Status: %s
                Created At: %s
                Description:
                %s

                CORRELATED HISTORICAL INCIDENTS:
                %s

                HISTORICAL ROOT CAUSES & DIAGNOSES:
                %s

                HISTORICAL INVESTIGATION STEPS & EVIDENCE:
                %s

                PAST POSTMORTEM LESSONS LEARNED:
                %s

                PAST POSTMORTEM ACTION ITEMS:
                %s

                FORMAT INSTRUCTIONS:
                %s
                """.formatted(
                targetIncident.getTitle(),
                targetIncident.getService(),
                targetIncident.getSeverity(),
                targetIncident.getStatus(),
                targetIncident.getCreatedAt(),
                targetIncident.getDescription(),
                correlatedSb.toString(),
                diagnosisSb.toString(),
                investigationSb.toString(),
                postmortemSb.toString(),
                actionItemsSb.toString(),
                formatInstructions
        );
    }
}
