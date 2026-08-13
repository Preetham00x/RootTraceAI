package com.roottrace.commandcenter.dto;

import java.time.Instant;

public record ReliabilityEventResponse(
        Instant timestamp,
        String type,
        String severity,
        String serviceName,
        String resourceId,
        String summary
) {}
