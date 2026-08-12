package com.roottrace.postmortem;

public enum PostmortemStatus {
    DRAFT,
    IN_REVIEW,
    PUBLISHED;

    public boolean canTransitionTo(PostmortemStatus target) {
        if (target == null || this == target) {
            return true;
        }
        return switch (this) {
            case DRAFT -> target == IN_REVIEW || target == PUBLISHED;
            case IN_REVIEW -> target == DRAFT || target == PUBLISHED;
            case PUBLISHED -> target == IN_REVIEW || target == DRAFT;
        };
    }
}
