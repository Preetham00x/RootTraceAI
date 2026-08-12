package com.roottrace.integration.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SlackResponse(
        @JsonProperty("response_type")
        String responseType, // "in_channel" or "ephemeral"
        String text
) {
    public static SlackResponse inChannel(String text) {
        return new SlackResponse("in_channel", text);
    }

    public static SlackResponse ephemeral(String text) {
        return new SlackResponse("ephemeral", text);
    }
}
