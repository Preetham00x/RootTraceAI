package com.roottrace.knowledge.chunking;

import java.util.List;

public interface DocumentChunker {
    List<DocumentChunk> chunk(String content, String title);
}
