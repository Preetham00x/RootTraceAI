package com.roottrace.ai.diagnosis;

import com.roottrace.knowledge.retrieval.RankedChunk;
import com.roottrace.knowledge.retrieval.RetrievalResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DiagnosisContextBuilderTest {

    private final DiagnosisContextBuilder builder = new DiagnosisContextBuilder();

    @Test
    void buildContext_ShouldFormatChunksAndRespectLimit() {
        RankedChunk chunk1 = new RankedChunk(UUID.randomUUID(), UUID.randomUUID(), "Doc 1", "Sec 1", "Content 1", 1.0, 1, 1, 0.5);
        RankedChunk chunk2 = new RankedChunk(UUID.randomUUID(), UUID.randomUUID(), "Doc 2", "Sec 2", "Content 2", 1.0, 2, 2, 0.4);
        
        RetrievalResult result = new RetrievalResult("test", List.of(chunk1, chunk2), 2, 2, 2, true, true);

        // Limit to 1 chunk
        String context = builder.buildContext(result, 1);

        assertThat(context).contains("EVIDENCE ITEM [1]");
        assertThat(context).contains("Doc 1 (Sec 1)");
        assertThat(context).contains("Content 1");
        
        assertThat(context).doesNotContain("EVIDENCE ITEM [2]");
        assertThat(context).doesNotContain("Content 2");
    }

    @Test
    void buildContext_ShouldHandleEmptyResults() {
        RetrievalResult result = RetrievalResult.empty("test");
        String context = builder.buildContext(result, 5);
        assertThat(context).contains("No relevant context found");
    }
}
