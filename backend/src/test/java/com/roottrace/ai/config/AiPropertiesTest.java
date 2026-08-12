package com.roottrace.ai.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(classes = AiPropertiesTest.TestConfig.class)
@TestPropertySource(properties = {
        "app.ai.diagnosis.max-context-chunks=5",
        "app.ai.retrieval.top-k=15",
        "app.ai.ingestion.chunk-size=500",
        "app.ai.ingestion.chunk-overlap=50",
        "app.ai.ingestion.max-document-size=1024"
})
class AiPropertiesTest {

    @Autowired
    private AiProperties aiProperties;

    @Test
    void testAiPropertiesBinding() {
        assertEquals(5, aiProperties.getDiagnosis().getMaxContextChunks());
        assertEquals(15, aiProperties.getRetrieval().getTopK());
        assertEquals(500, aiProperties.getIngestion().getChunkSize());
        assertEquals(50, aiProperties.getIngestion().getChunkOverlap());
        assertEquals(1024L, aiProperties.getIngestion().getMaxDocumentSize());
    }

    @EnableConfigurationProperties(AiProperties.class)
    static class TestConfig {
    }
}
