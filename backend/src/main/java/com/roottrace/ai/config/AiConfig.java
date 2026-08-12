package com.roottrace.ai.config;

import com.roottrace.ai.chat.AiChatService;
import com.roottrace.ai.chat.GeminiChatService;
import com.roottrace.ai.embedding.AiEmbeddingService;
import com.roottrace.ai.embedding.GeminiEmbeddingService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfig {

    @Bean
    @ConditionalOnBean(ChatModel.class)
    public AiChatService aiChatService(ChatModel chatModel) {
        return new GeminiChatService(chatModel);
    }

    @Bean
    @ConditionalOnBean(EmbeddingModel.class)
    public AiEmbeddingService aiEmbeddingService(EmbeddingModel embeddingModel) {
        return new GeminiEmbeddingService(embeddingModel);
    }

    @Bean
    @ConditionalOnBean(ChatModel.class)
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }
}
