package com.roottrace.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Service
public class MockInfrastructureExecutionService implements InfrastructureExecutionService {

    private static final Logger log = LoggerFactory.getLogger(MockInfrastructureExecutionService.class);

    private static final List<String> FORBIDDEN_KEYWORDS = Arrays.asList(
            "rm ", "rm -rf", "shutdown", "reboot", "format ", "mkfs",
            "drop table", "drop database", "delete from", "truncate ",
            ":(){ :|:& };:", "dd if=", "> /dev/sda", "chmod -R 777 /"
    );

    @Override
    public boolean isCommandSafe(String command) {
        if (command == null || command.isBlank()) {
            return false;
        }
        String lower = command.toLowerCase(Locale.ROOT).trim();
        for (String forbidden : FORBIDDEN_KEYWORDS) {
            if (lower.contains(forbidden)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public CommandResult executeCommand(String command, String targetService) {
        if (!isCommandSafe(command)) {
            log.warn("Rejected potentially unsafe command execution: {}", command);
            return new CommandResult(false, null, "Execution rejected: command contains unsafe or prohibited operations.");
        }

        String lower = command.toLowerCase(Locale.ROOT).trim();
        log.info("Executing safe runbook command in simulated environment for service [{}]: {}", targetService, command);

        // Generate realistic diagnostic SRE output based on command type
        if (lower.startsWith("kubectl get pods")) {
            return new CommandResult(true,
                    """
                    NAME                               READY   STATUS    RESTARTS   AGE
                    %s-worker-7c48f87b89-j4k2x         1/1     Running   2 (5m ago) 4d2h
                    %s-worker-7c48f87b89-p9z1w         1/1     Running   0          4d2h
                    %s-api-6b79d6b9bc-m8n3q            1/1     Running   0          6d1h
                    """.formatted(targetService != null ? targetService : "app",
                            targetService != null ? targetService : "app",
                            targetService != null ? targetService : "app"),
                    null);
        }

        if (lower.startsWith("netstat") || lower.startsWith("ss")) {
            return new CommandResult(true,
                    """
                    Active Internet connections (servers and established)
                    Proto Recv-Q Send-Q Local Address           Foreign Address         State
                    tcp        0      0 0.0.0.0:8080            0.0.0.0:*               LISTEN
                    tcp        0      0 10.244.1.45:8080        10.244.2.112:48392      ESTABLISHED
                    tcp        0      0 10.244.1.45:54320       10.0.4.15:5432          ESTABLISHED
                    tcp        0      0 10.244.1.45:54322       10.0.4.15:5432          CLOSE_WAIT
                    tcp        0      0 10.244.1.45:54324       10.0.4.15:5432          CLOSE_WAIT
                    """,
                    null);
        }

        if (lower.startsWith("curl") || lower.startsWith("wget") || lower.startsWith("http")) {
            return new CommandResult(true,
                    """
                    HTTP/1.1 200 OK
                    Date: Wed, 12 Aug 2026 22:30:00 GMT
                    Content-Type: application/json;charset=UTF-8
                    Transfer-Encoding: chunked
                    Connection: keep-alive
                    {"status":"UP","components":{"db":{"status":"UP"},"diskSpace":{"status":"UP"}}}
                    """,
                    null);
        }

        if (lower.startsWith("df") || lower.startsWith("du")) {
            return new CommandResult(true,
                    """
                    Filesystem     1K-blocks      Used Available Use% Mounted on
                    /dev/root       30308008  18451200  11840424  61% /
                    /dev/sda1        4062912    823100   3023244  22% /var/log
                    tmpfs            4041728         0   4041728   0% /dev/shm
                    """,
                    null);
        }

        // Generic safe command simulation
        return new CommandResult(true,
                "Command executed successfully in controlled container environment.\nOutput: OK\nExit Code: 0",
                null);
    }
}
