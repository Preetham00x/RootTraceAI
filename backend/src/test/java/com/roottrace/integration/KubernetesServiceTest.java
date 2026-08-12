package com.roottrace.integration;

import com.roottrace.integration.dto.KubernetesPodStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KubernetesServiceTest {

    private KubernetesService kubernetesService;

    @BeforeEach
    void setUp() {
        kubernetesService = new KubernetesService();
    }

    @Test
    @DisplayName("Should retrieve active pod status for a service")
    void testGetPods() {
        List<KubernetesPodStatus.PodInfo> pods = kubernetesService.getPods("production", "payment-service");
        assertThat(pods).isNotEmpty();
        assertThat(pods.get(0).phase()).isEqualTo("Running");
        assertThat(pods.get(0).name()).contains("payment-service");
    }

    @Test
    @DisplayName("Should retrieve deployment info and recent events")
    void testGetDeploymentAndEvents() {
        var deployment = kubernetesService.getDeployment("production", "payment-service");
        assertThat(deployment.replicas()).isEqualTo(3);
        assertThat(deployment.readyReplicas()).isEqualTo(3);

        var events = kubernetesService.getEvents("production", "payment-service");
        assertThat(events).hasSize(2);
    }

    @Test
    @DisplayName("Should fetch pod logs")
    void testGetPodLogs() {
        String logs = kubernetesService.getPodLogs("production", "payment-service-pod", 100);
        assertThat(logs).contains("Starting payment processing worker");
        assertThat(logs).contains("504 Gateway Timeout");
    }
}
