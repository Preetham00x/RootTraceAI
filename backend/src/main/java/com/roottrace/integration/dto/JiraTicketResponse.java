package com.roottrace.integration.dto;

import java.util.UUID;

public record JiraTicketResponse(
        UUID id,
        UUID incidentId,
        UUID actionItemId,
        String provider,
        String externalTicketId,
        String externalUrl,
        String status
) {
}
