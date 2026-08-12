package com.roottrace.integration;

public interface InfrastructureExecutionService {

    record CommandResult(
            boolean successful,
            String stdout,
            String stderr
    ) {}

    CommandResult executeCommand(String command, String targetService);

    boolean isCommandSafe(String command);
}
