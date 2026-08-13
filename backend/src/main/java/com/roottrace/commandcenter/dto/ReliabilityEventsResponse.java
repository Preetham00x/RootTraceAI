package com.roottrace.commandcenter.dto;

import java.util.List;

public record ReliabilityEventsResponse(
        int count,
        List<ReliabilityEventResponse> events
) {}
