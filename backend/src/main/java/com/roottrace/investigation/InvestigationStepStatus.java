package com.roottrace.investigation;

public enum InvestigationStepStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    SKIPPED;

    public boolean canTransitionTo(InvestigationStepStatus target) {
        if (target == null || this == target) {
            return true;
        }
        return switch (this) {
            case PENDING -> target == IN_PROGRESS || target == COMPLETED || target == SKIPPED || target == FAILED;
            case IN_PROGRESS -> target == COMPLETED || target == FAILED || target == SKIPPED || target == PENDING;
            case COMPLETED -> target == IN_PROGRESS || target == PENDING;
            case FAILED -> target == IN_PROGRESS || target == PENDING || target == SKIPPED;
            case SKIPPED -> target == PENDING || target == IN_PROGRESS;
        };
    }
}
