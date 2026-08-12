package com.roottrace.postmortem.dto;

import com.roottrace.postmortem.PostmortemStatus;
import java.util.List;

public record UpdatePostmortemRequest(
        String title,
        String summary,
        String impactSummary,
        String rootCauseAnalysis,
        String resolutionSummary,
        List<PostmortemTimelineEntry> timeline,
        List<String> lessonsLearned,
        PostmortemStatus status
) {
}
