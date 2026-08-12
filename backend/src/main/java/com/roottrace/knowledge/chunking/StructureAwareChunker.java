package com.roottrace.knowledge.chunking;

import com.roottrace.ai.config.AiProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class StructureAwareChunker implements DocumentChunker {

    private final int chunkSize;
    private final int chunkOverlap;
    
    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.*)$");

    public StructureAwareChunker(AiProperties aiProperties) {
        this.chunkSize = aiProperties.getIngestion().getChunkSize();
        this.chunkOverlap = aiProperties.getIngestion().getChunkOverlap();
    }

    @Override
    public List<DocumentChunk> chunk(String content, String title) {
        List<DocumentChunk> chunks = new ArrayList<>();
        if (content == null || content.trim().isEmpty()) {
            return chunks;
        }

        String[] lines = content.split("\n");
        StringBuilder currentChunkContent = new StringBuilder();
        
        List<String> currentPath = new ArrayList<>();
        currentPath.add(title != null ? title : "Document");
        
        boolean inCodeBlock = false;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            
            if (line.trim().startsWith("```")) {
                inCodeBlock = !inCodeBlock;
            }

            if (!inCodeBlock) {
                Matcher matcher = HEADING_PATTERN.matcher(line.trim());
                if (matcher.matches()) {
                    int level = matcher.group(1).length();
                    String headingText = matcher.group(2).trim();
                    
                    // If we have accumulated content and we hit a new section, and it's getting large, flush it.
                    // Or if it's already above overlap size and we want to keep logical boundaries
                    if (currentChunkContent.length() > chunkOverlap) {
                        chunks.add(new DocumentChunk(currentChunkContent.toString().trim(), buildSectionPath(currentPath)));
                        
                        // Keep the overlap from the end of the previous chunk
                        String existingContent = currentChunkContent.toString();
                        int overlapStart = Math.max(0, existingContent.length() - chunkOverlap);
                        // Try to find a line break to start the overlap
                        int newlineIdx = existingContent.indexOf('\n', overlapStart);
                        if (newlineIdx != -1 && newlineIdx < existingContent.length() - 1) {
                            overlapStart = newlineIdx + 1;
                        }
                        
                        currentChunkContent = new StringBuilder();
                        if (overlapStart > 0 && overlapStart < existingContent.length()) {
                            currentChunkContent.append(existingContent.substring(overlapStart)).append("\n");
                        }
                    }
                    
                    updatePath(currentPath, level, headingText);
                }
            }

            currentChunkContent.append(line).append("\n");

            // If we exceed max chunk size, flush, unless we are in a code block.
            if (currentChunkContent.length() >= chunkSize && !inCodeBlock) {
                chunks.add(new DocumentChunk(currentChunkContent.toString().trim(), buildSectionPath(currentPath)));
                
                String existingContent = currentChunkContent.toString();
                int overlapStart = Math.max(0, existingContent.length() - chunkOverlap);
                int newlineIdx = existingContent.indexOf('\n', overlapStart);
                if (newlineIdx != -1 && newlineIdx < existingContent.length() - 1) {
                    overlapStart = newlineIdx + 1;
                }
                
                currentChunkContent = new StringBuilder();
                if (overlapStart > 0 && overlapStart < existingContent.length()) {
                    currentChunkContent.append(existingContent.substring(overlapStart)).append("\n");
                }
            }
        }

        if (!currentChunkContent.toString().trim().isEmpty()) {
            chunks.add(new DocumentChunk(currentChunkContent.toString().trim(), buildSectionPath(currentPath)));
        }

        return chunks;
    }

    private void updatePath(List<String> path, int level, String heading) {
        // level 1 heading -> index 1 in path (index 0 is title)
        // Ensure path list is large enough
        while (path.size() <= level) {
            path.add("");
        }
        path.set(level, heading);
        // Clear deeper levels
        path.subList(level + 1, path.size()).clear();
    }

    private String buildSectionPath(List<String> path) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < path.size(); i++) {
            if (path.get(i) != null && !path.get(i).isEmpty()) {
                if (sb.length() > 0) {
                    sb.append(" > ");
                }
                sb.append(path.get(i));
            }
        }
        return sb.toString();
    }
}
