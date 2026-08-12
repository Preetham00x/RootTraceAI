package com.roottrace.integration;

import com.roottrace.integration.dto.KubernetesPodStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class KubernetesService {

    private static final Logger log = LoggerFactory.getLogger(KubernetesService.class);

    public List<KubernetesPodStatus.PodInfo> getPods(String namespace, String service) {
        log.info("Fetching Kubernetes pods for namespace [{}], service [{}]", namespace, service);
        String ns = (namespace != null && !namespace.isBlank()) ? namespace : "default";
        String svc = (service != null && !service.isBlank()) ? service : "service";

        return List.of(
                new KubernetesPodStatus.PodInfo(
                        svc + "-deployment-6d9b4bf89-4k9l2",
                        ns,
                        "Running",
                        0,
                        "node-worker-pool-1",
                        "10.244.1.65",
                        Instant.now().minusSeconds(86400 * 3),
                        Map.of("app", svc, "environment", "production")
                ),
                new KubernetesPodStatus.PodInfo(
                        svc + "-deployment-6d9b4bf89-m8z3q",
                        ns,
                        "Running",
                        1,
                        "node-worker-pool-2",
                        "10.244.2.88",
                        Instant.now().minusSeconds(86400 * 3),
                        Map.of("app", svc, "environment", "production")
                )
        );
    }

    public KubernetesPodStatus.DeploymentInfo getDeployment(String namespace, String deploymentName) {
        String ns = (namespace != null && !namespace.isBlank()) ? namespace : "default";
        return new KubernetesPodStatus.DeploymentInfo(
                deploymentName,
                ns,
                3,
                3,
                3,
                3,
                Map.of("app", deploymentName)
        );
    }

    public List<KubernetesPodStatus.K8sEvent> getEvents(String namespace, String service) {
        String ns = (namespace != null && !namespace.isBlank()) ? namespace : "default";
        return List.of(
                new KubernetesPodStatus.K8sEvent(
                        "Normal",
                        "Pulled",
                        "Container image successfully pulled",
                        "Pod",
                        service + "-deployment-6d9b4bf89-4k9l2",
                        1,
                        Instant.now().minusSeconds(3600),
                        Instant.now().minusSeconds(3600)
                ),
                new KubernetesPodStatus.K8sEvent(
                        "Warning",
                        "Unhealthy",
                        "Liveness probe failed: HTTP probe failed with statuscode: 500",
                        "Pod",
                        service + "-deployment-6d9b4bf89-m8z3q",
                        3,
                        Instant.now().minusSeconds(1800),
                        Instant.now().minusSeconds(300)
                )
        );
    }

    public String getPodLogs(String namespace, String podName, int tailLines) {
        log.info("Fetching logs for pod [{}] in namespace [{}] (tail: {})", podName, namespace, tailLines);
        return """
                2026-08-12 22:15:00.123 [main] INFO  c.r.service.Worker - Starting payment processing worker
                2026-08-12 22:15:04.891 [pool-2-thread-1] ERROR c.r.client.HttpClient - ConnectTimeoutException: connection timed out after 30000ms
                2026-08-12 22:15:04.892 [pool-2-thread-1] WARN  c.r.pool.ConnectionPool - Pool size exhausted, active=50, idle=0, waiting=12
                2026-08-12 22:15:05.102 [http-nio-8080-exec-4] ERROR c.r.web.RestHandler - 504 Gateway Timeout returned to client
                """;
    }
}
