package com.roottrace.integration.dto;

public record SlackCommandRequest(
        String command,
        String text,
        String userId,
        String userName,
        String channelId,
        String channelName,
        String responseUrl
) {
}
