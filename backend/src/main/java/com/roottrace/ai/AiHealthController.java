package com.roottrace.ai;

import com.roottrace.ai.chat.AiChatService;
import com.roottrace.ai.embedding.AiEmbeddingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiHealthController {

    private final AiChatService aiChatService;
    private final AiEmbeddingService aiEmbeddingService;

    public AiHealthController(
            @Autowired(required = false) AiChatService aiChatService,
            @Autowired(required = false) AiEmbeddingService aiEmbeddingService) {
        this.aiChatService = aiChatService;
        this.aiEmbeddingService = aiEmbeddingService;
    }

    @GetMapping("/health")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENGINEER')")
    public ResponseEntity<Map<String, Object>> health() {
        boolean chatConfigured = aiChatService != null;
        boolean embeddingConfigured = aiEmbeddingService != null;
        
        String status = (chatConfigured && embeddingConfigured) ? "UP" : "DEGRADED";
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", status);
        response.put("provider", "google-gemini");
        response.put("chatConfigured", chatConfigured);
        response.put("embeddingConfigured", embeddingConfigured);
        
        return ResponseEntity.ok(response);
    }
}
