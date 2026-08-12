package com.roottrace.ai.diagnosis;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * Structured AI response from Gemini diagnosis.
 * This record is used with Spring AI BeanOutputConverter for structured JSON output.
 *
 * Field-level descriptions are used by BeanOutputConverter to generate JSON schema
 * instructions that guide the model's output format.
 */
public record DiagnosisAiResponse(
        @JsonPropertyDescription("Concise 2-3 sentence summary of the incident and its AI-assessed probable cause")
        String summary,

        @JsonPropertyDescription("The most probable root cause based solely on the provided evidence. Do not invent causes not supported by the evidence.")
        String probableRootCause,

        @JsonPropertyDescription("Confidence score between 0.0 and 1.0. Use low values (< 0.4) when evidence is sparse or contradictory.")
        Double confidence,

        @JsonPropertyDescription("List of contributing factors identified in the evidence. May be empty if insufficient evidence.")
        List<String> contributingFactors,

        @JsonPropertyDescription("List of concrete recommended actions for the engineering team. Based only on evidence, not generic advice.")
        List<String> recommendedActions,

        @JsonPropertyDescription("Evidence items citing specific retrieved chunks that support this diagnosis")
        List<EvidenceItem> evidence,

        @JsonPropertyDescription("Citations of source documents used")
        List<CitationItem> citations
) {
    /**
     * Individual evidence item referencing a retrieved chunk by its ID.
     */
    public record EvidenceItem(
            @JsonPropertyDescription("The exact chunk ID (UUID string) from the retrieved evidence")
            String chunkId,

            @JsonPropertyDescription("Explanation of how this chunk supports the diagnosis")
            String reason
    ) {
    }

    /**
     * Citation of a source document used in the diagnosis.
     */
    public record CitationItem(
            @JsonPropertyDescription("The document ID (UUID string)")
            String documentId,

            @JsonPropertyDescription("The document title")
            String documentTitle,

            @JsonPropertyDescription("The section path within the document (may be null)")
            String sectionPath
    ) {
    }

    /**
     * Returns a sanitized confidence value clamped to [0.0, 1.0].
     */
    public double clampedConfidence() {
        if (confidence == null) return 0.0;
        return Math.max(0.0, Math.min(1.0, confidence));
    }
}
