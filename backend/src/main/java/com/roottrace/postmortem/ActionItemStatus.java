package com.roottrace.postmortem;

public enum ActionItemStatus {
    OPEN,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED;

    public boolean canTransitionTo(ActionItemStatus target) {
        if (target == null || this == target) {
            return true;
        }
        return switch (this) {
            case OPEN -> target == IN_PROGRESS || target == COMPLETED || target == CANCELLED;
            case IN_PROGRESS -> target == COMPLETED || target == CANCELLED || target == OPEN;
            case COMPLETED -> target == IN_PROGRESS || target == OPEN;
            case CANCELLED -> target == OPEN || target == IN_PROGRESS;
        };
    }
}
