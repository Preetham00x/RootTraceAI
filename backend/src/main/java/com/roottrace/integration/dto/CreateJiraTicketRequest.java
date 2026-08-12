package com.roottrace.integration.dto;

public record CreateJiraTicketRequest(
        String projectKey,
        String issueType,
        String summary,
        String description
) {
}
