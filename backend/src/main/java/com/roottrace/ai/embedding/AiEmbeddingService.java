package com.roottrace.ai.embedding;

import java.util.List;

public interface AiEmbeddingService {
    float[] embed(String text);
    List<float[]> embedAll(List<String> texts);
}
