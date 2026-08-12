package com.roottrace.integration.dto;

import java.util.UUID;

public record WebhookIngestionResponse(
        String status, // "CREATED", "UPDATED", "IGNORED", "DUPLICATE"
        String message,
        UUID incidentId,
        String externalEventId,
        boolean isDuplicate
) {
}
