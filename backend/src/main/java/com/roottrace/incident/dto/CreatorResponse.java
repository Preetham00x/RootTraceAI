package com.roottrace.incident.dto;

import java.util.UUID;

public record CreatorResponse(
        UUID id,
        String name
) {
}
