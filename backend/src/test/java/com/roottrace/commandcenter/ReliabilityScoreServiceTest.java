package com.roottrace.commandcenter;

import com.roottrace.commandcenter.dto.ReliabilityScoreResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReliabilityScoreServiceTest {

    private ReliabilityScoreService scoreService;

    @BeforeEach
    void setUp() {
        scoreService = new ReliabilityScoreService();
    }

    @Test
    @DisplayName("Should return 100.0 and LOW risk tier when all dimensions are healthy")
    void testCalculateReliabilityScore_PerfectScore() {
        ReliabilityScoreResponse response = scoreService.calculateReliabilityScore(
                0, 0, 10.0, 0, 0, 0.0, 0, 0
        );

        assertThat(response).isNotNull();
        assertThat(response.score()).isEqualTo(100.0);
        assertThat(response.baseScore()).isEqualTo(100.0);
        assertThat(response.riskTier()).isEqualTo("LOW");
        assertThat(response.penalties()).hasSize(1);
        assertThat(response.penalties().get(0).category()).isEqualTo("NONE");
    }

    @Test
    @DisplayName("Should assess penalties for active SLO breaches and warning SLOs")
    void testCalculateReliabilityScore_SloPenalties() {
        // 2 breached SLOs (10 pts), 2 warning SLOs (3 pts)
        ReliabilityScoreResponse response = scoreService.calculateReliabilityScore(
                2, 2, 20.0, 0, 0, 0.0, 0, 0
        );

        assertThat(response.score()).isEqualTo(87.0);
        assertThat(response.riskTier()).isEqualTo("LOW");
        assertThat(response.penalties()).hasSize(2);
    }

    @Test
    @DisplayName("Should assess penalties for high error budget consumption and critical incidents")
    void testCalculateReliabilityScore_ErrorBudgetAndIncidents() {
        // avg budget consumed = 80% (15 pts), critical incidents = 2 (12 pts), high incidents = 1 (3 pts)
        ReliabilityScoreResponse response = scoreService.calculateReliabilityScore(
                0, 0, 80.0, 2, 1, 0.0, 0, 0
        );

        assertThat(response.score()).isEqualTo(70.0);
        assertThat(response.riskTier()).isEqualTo("MEDIUM");
    }

    @Test
    @DisplayName("Should assess penalties for overdue actions and failed runbooks")
    void testCalculateReliabilityScore_ActionsAndRunbooks() {
        // 3 overdue actions (6 pts), 2 failed runbooks (5 pts)
        ReliabilityScoreResponse response = scoreService.calculateReliabilityScore(
                0, 0, 10.0, 0, 0, 0.0, 3, 2
        );

        assertThat(response.score()).isEqualTo(89.0);
        assertThat(response.penalties()).hasSize(2);
    }

    @Test
    @DisplayName("Should clamp score to [0.0, 100.0] and assign CRITICAL tier under extreme penalties")
    void testCalculateReliabilityScore_Clamping() {
        // Max penalties across all dimensions
        ReliabilityScoreResponse response = scoreService.calculateReliabilityScore(
                10, 10, 95.0, 10, 10, 0.8, 20, 10
        );

        assertThat(response.score()).isLessThanOrEqualTo(40.0);
        assertThat(response.score()).isGreaterThanOrEqualTo(0.0);
        assertThat(response.riskTier()).isEqualTo("CRITICAL");
    }
}
