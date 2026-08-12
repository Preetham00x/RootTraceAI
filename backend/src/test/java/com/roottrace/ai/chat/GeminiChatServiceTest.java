package com.roottrace.ai.chat;

import com.roottrace.ai.exception.AiServiceException;
import com.roottrace.ai.exception.AiUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.model.ChatModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class GeminiChatServiceTest {

    private ChatModel chatModel;
    private GeminiChatService geminiChatService;

    @BeforeEach
    void setUp() {
        chatModel = Mockito.mock(ChatModel.class);
        geminiChatService = new GeminiChatService(chatModel);
    }

    @Test
    void testGenerate_Success() {
        String prompt = "Hello, world!";
        String expectedResponse = "Hello! How can I assist you today?";
        when(chatModel.call(prompt)).thenReturn(expectedResponse);

        String result = geminiChatService.generate(prompt);

        assertEquals(expectedResponse, result);
    }

    @Test
    void testGenerate_Timeout_ThrowsAiUnavailableException() {
        String prompt = "Hello, world!";
        when(chatModel.call(anyString())).thenThrow(new RuntimeException("Connection timeout"));

        assertThrows(AiUnavailableException.class, () -> geminiChatService.generate(prompt));
    }

    @Test
    void testGenerate_GenericError_ThrowsAiServiceException() {
        String prompt = "Hello, world!";
        when(chatModel.call(anyString())).thenThrow(new RuntimeException("Something went wrong"));

        assertThrows(AiServiceException.class, () -> geminiChatService.generate(prompt));
    }
}
