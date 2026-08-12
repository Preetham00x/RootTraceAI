package com.roottrace.postmortem.dto;

import java.time.Instant;

public record PostmortemTimelineEntry(
        Instant timestamp,
        String description,
        String source
) {
}
