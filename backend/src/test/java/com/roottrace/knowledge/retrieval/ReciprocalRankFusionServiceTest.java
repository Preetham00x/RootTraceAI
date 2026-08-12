package com.roottrace.knowledge.retrieval;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReciprocalRankFusionServiceTest {

    private final ReciprocalRankFusionService service = new ReciprocalRankFusionService();

    @Test
    void fuse_ShouldCombineAndDeduplicateResults() {
        UUID chunk1 = UUID.randomUUID();
        UUID chunk2 = UUID.randomUUID();
        UUID chunk3 = UUID.randomUUID();

        List<RetrievedChunk> semantic = List.of(
                new RetrievedChunk(chunk1, UUID.randomUUID(), "Doc 1", "Sec 1", "Content 1", 0.9),
                new RetrievedChunk(chunk2, UUID.randomUUID(), "Doc 2", "Sec 2", "Content 2", 0.8)
        );

        List<RetrievedChunk> keyword = List.of(
                new RetrievedChunk(chunk2, UUID.randomUUID(), "Doc 2", "Sec 2", "Content 2", 1.0),
                new RetrievedChunk(chunk3, UUID.randomUUID(), "Doc 3", "Sec 3", "Content 3", 0.9)
        );

        List<RankedChunk> fused = service.fuse(semantic, keyword, 60, 5);

        assertThat(fused).hasSize(3);
        
        // chunk2 is #2 in semantic, #1 in keyword -> score: 1/62 + 1/61
        // chunk1 is #1 in semantic, missing in keyword -> score: 1/61
        // chunk3 is #2 in keyword, missing in semantic -> score: 1/62
        // So order should be: chunk2, chunk1, chunk3
        
        assertThat(fused.get(0).chunkId()).isEqualTo(chunk2);
        assertThat(fused.get(1).chunkId()).isEqualTo(chunk1);
        assertThat(fused.get(2).chunkId()).isEqualTo(chunk3);

        assertThat(fused.get(0).semanticScore()).isEqualTo(0.8);
        assertThat(fused.get(0).semanticRank()).isEqualTo(2);
        assertThat(fused.get(0).keywordRank()).isEqualTo(1);
    }
}
