package com.roottrace.ai.diagnosis;

import com.roottrace.incident.Incident;
import com.roottrace.knowledge.retrieval.RankedChunk;
import com.roottrace.knowledge.retrieval.RetrievalResult;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Builds the bounded context string from retrieved knowledge chunks for the AI prompt.
 * Limits the context size to avoid exceeding LLM context windows.
 */
@Component
public class DiagnosisContextBuilder {

    /**
     * Builds a formatted context string from the hybrid retrieval result.
     *
     * @param result           the hybrid retrieval result
     * @param maxContextChunks maximum number of chunks to include
     * @return formatted context string
     */
    public String buildContext(RetrievalResult result, int maxContextChunks) {
        if (result == null || result.results() == null || result.results().isEmpty()) {
            return "No relevant context found in the knowledge base.";
        }

        StringBuilder context = new StringBuilder();
        List<RankedChunk> chunks = result.results();
        int limit = Math.min(chunks.size(), maxContextChunks);

        for (int i = 0; i < limit; i++) {
            RankedChunk chunk = chunks.get(i);
            context.append("--- EVIDENCE ITEM [").append(i + 1).append("] ---\n");
            context.append("Chunk ID: ").append(chunk.chunkId()).append("\n");
            context.append("Document: ").append(chunk.documentTitle());
            if (chunk.sectionPath() != null && !chunk.sectionPath().isBlank()) {
                context.append(" (").append(chunk.sectionPath()).append(")");
            }
            context.append("\n");
            context.append("Content:\n").append(chunk.content()).append("\n\n");
        }

        return context.toString();
    }
}
