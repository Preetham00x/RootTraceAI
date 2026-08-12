package com.roottrace.ai.embedding;

import com.roottrace.ai.exception.AiServiceException;
import com.roottrace.ai.exception.AiUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.List;

public class GeminiEmbeddingService implements AiEmbeddingService {

    private static final Logger logger = LoggerFactory.getLogger(GeminiEmbeddingService.class);
    private final EmbeddingModel embeddingModel;

    public GeminiEmbeddingService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @Override
    public float[] embed(String text) {
        try {
            logger.debug("Generating embeddings from Gemini for single text");
            float[] result = embeddingModel.embed(text);
            logger.debug("Gemini embedding request completed successfully");
            return result;
        } catch (Exception e) {
            handleException(e);
            return null; // unreachable
        }
    }

    @Override
    public List<float[]> embedAll(List<String> texts) {
        try {
            logger.debug("Generating embeddings from Gemini for {} texts", texts.size());
            List<float[]> result = embeddingModel.embed(texts);
            logger.debug("Gemini batch embedding request completed successfully");
            return result;
        } catch (Exception e) {
            handleException(e);
            return null; // unreachable
        }
    }

    private void handleException(Exception e) {
        logger.error("Gemini embedding request failed: {}", e.getMessage());
        
        if (e.getMessage() != null && (e.getMessage().contains("timeout") || e.getMessage().contains("Connection") || e.getMessage().contains("503"))) {
            throw new AiUnavailableException("AI provider is currently unavailable", e);
        }
        throw new AiServiceException("Failed to generate embedding from AI provider", e);
    }
}
