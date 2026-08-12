package com.roottrace.integration.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public class KubernetesPodStatus {
    public record PodInfo(
            String name,
            String namespace,
            String phase, // Running, Pending, Failed, Succeeded
            int restartCount,
            String nodeName,
            String ip,
            Instant startTime,
            Map<String, String> labels
    ) {}

    public record DeploymentInfo(
            String name,
            String namespace,
            int replicas,
            int readyReplicas,
            int availableReplicas,
            int updatedReplicas,
            Map<String, String> labels
    ) {}

    public record K8sEvent(
            String type, // Normal, Warning
            String reason,
            String message,
            String objectKind,
            String objectName,
            int count,
            Instant firstTimestamp,
            Instant lastTimestamp
    ) {}
}
