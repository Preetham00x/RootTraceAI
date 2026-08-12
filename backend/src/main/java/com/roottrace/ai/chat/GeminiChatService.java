package com.roottrace.ai.chat;

import com.roottrace.ai.exception.AiServiceException;
import com.roottrace.ai.exception.AiUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;

public class GeminiChatService implements AiChatService {

    private static final Logger logger = LoggerFactory.getLogger(GeminiChatService.class);
    private final ChatModel chatModel;

    public GeminiChatService(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public String generate(String prompt) {
        try {
            logger.debug("Generating chat response from Gemini");
            String result = chatModel.call(prompt);
            logger.debug("Gemini chat request completed successfully");
            return result;
        } catch (Exception e) {
            logger.error("Gemini chat request failed: {}", e.getMessage());
            
            // Note: Spring AI throws various exceptions depending on the client (e.g. NonTransientAiException)
            // But we can map common connection/timeout issues or let them all fall under AiServiceException
            if (e.getMessage() != null && (e.getMessage().contains("timeout") || e.getMessage().contains("Connection") || e.getMessage().contains("503"))) {
                throw new AiUnavailableException("AI provider is currently unavailable", e);
            }
            throw new AiServiceException("Failed to generate response from AI provider", e);
        }
    }
}
