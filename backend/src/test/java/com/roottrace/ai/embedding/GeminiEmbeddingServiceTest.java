package com.roottrace.ai.embedding;

import com.roottrace.ai.exception.AiServiceException;
import com.roottrace.ai.exception.AiUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class GeminiEmbeddingServiceTest {

    private EmbeddingModel embeddingModel;
    private GeminiEmbeddingService geminiEmbeddingService;

    @BeforeEach
    void setUp() {
        embeddingModel = Mockito.mock(EmbeddingModel.class);
        geminiEmbeddingService = new GeminiEmbeddingService(embeddingModel);
    }

    @Test
    void testEmbed_Success() {
        String text = "test text";
        float[] expectedResult = new float[]{0.1f, 0.2f, 0.3f};
        when(embeddingModel.embed(text)).thenReturn(expectedResult);

        float[] result = geminiEmbeddingService.embed(text);

        assertArrayEquals(expectedResult, result);
    }

    @Test
    void testEmbed_Timeout_ThrowsAiUnavailableException() {
        String text = "test text";
        when(embeddingModel.embed(anyString())).thenThrow(new RuntimeException("Connection timeout"));

        assertThrows(AiUnavailableException.class, () -> geminiEmbeddingService.embed(text));
    }

    @Test
    void testEmbed_GenericError_ThrowsAiServiceException() {
        String text = "test text";
        when(embeddingModel.embed(anyString())).thenThrow(new RuntimeException("Something went wrong"));

        assertThrows(AiServiceException.class, () -> geminiEmbeddingService.embed(text));
    }

    @Test
    void testEmbedAll_Success() {
        List<String> texts = List.of("test 1", "test 2");
        List<float[]> expectedResult = List.of(new float[]{0.1f}, new float[]{0.2f});
        when(embeddingModel.embed(texts)).thenReturn(expectedResult);

        List<float[]> result = geminiEmbeddingService.embedAll(texts);

        assertEquals(expectedResult.size(), result.size());
        for (int i = 0; i < expectedResult.size(); i++) {
            assertArrayEquals(expectedResult.get(i), result.get(i));
        }
    }

    @Test
    void testEmbedAll_Timeout_ThrowsAiUnavailableException() {
        List<String> texts = List.of("test 1", "test 2");
        when(embeddingModel.embed(texts)).thenThrow(new RuntimeException("Connection timeout"));

        assertThrows(AiUnavailableException.class, () -> geminiEmbeddingService.embedAll(texts));
    }
}
