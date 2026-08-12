package com.roottrace.knowledge.chunking;

import com.roottrace.ai.config.AiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StructureAwareChunkerTest {

    private StructureAwareChunker chunker;

    @BeforeEach
    void setUp() {
        AiProperties aiProperties = new AiProperties();
        aiProperties.getIngestion().setChunkSize(100);
        aiProperties.getIngestion().setChunkOverlap(20);
        chunker = new StructureAwareChunker(aiProperties);
    }

    @Test
    void testChunking_PreservesHeadings() {
        String content = """
                # Title 1
                Some text under title 1
                ## Subtitle 1
                Text under subtitle 1
                ### Sub-subtitle
                Text here
                """;

        List<DocumentChunk> chunks = chunker.chunk(content, "Doc Title");
        
        // With size 100, we might get everything in one chunk or two depending on length
        // This content is ~104 chars, so it splits.
        assertEquals(3, chunks.size());
        assertEquals("Doc Title > Title 1 > Subtitle 1 > Sub-subtitle", chunks.get(2).sectionPath());
    }

    @Test
    void testChunking_CodeBlocksNotSplit() {
        String content = """
                Some text
                ```java
                public void test() {
                   // a very long line that exceeds the chunk size
                   // a very long line that exceeds the chunk size
                }
                ```
                More text
                """;
        
        List<DocumentChunk> chunks = chunker.chunk(content, "Doc");
        
        // Ensure code block didn't split in the middle
        assertEquals(2, chunks.size());
        assertEquals("Doc", chunks.get(0).sectionPath());
    }
}
