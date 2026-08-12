package com.roottrace.integration;

public enum RunbookExecutionStatus {
    REQUESTED,
    APPROVED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED;

    public boolean canTransitionTo(RunbookExecutionStatus target) {
        if (target == null || this == target) {
            return true;
        }
        return switch (this) {
            case REQUESTED -> target == APPROVED || target == CANCELLED;
            case APPROVED -> target == RUNNING || target == CANCELLED;
            case RUNNING -> target == SUCCEEDED || target == FAILED || target == CANCELLED;
            case SUCCEEDED, FAILED, CANCELLED -> false;
        };
    }
}
