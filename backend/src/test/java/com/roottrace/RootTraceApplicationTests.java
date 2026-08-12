package com.roottrace;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.roottrace.ai.embedding.AiEmbeddingService;
import com.roottrace.ai.chat.AiChatService;

@SpringBootTest
@ActiveProfiles("test")
class RootTraceApplicationTests {

    @MockitoBean
    private AiEmbeddingService aiEmbeddingService;

    @MockitoBean
    private AiChatService aiChatService;

    @Test
    void contextLoads() {
    }
}
