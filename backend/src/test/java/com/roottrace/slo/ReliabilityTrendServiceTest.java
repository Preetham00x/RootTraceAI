package com.roottrace.slo;

import com.roottrace.incident.IncidentRepository;
import com.roottrace.slo.dto.ReliabilityTrendResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReliabilityTrendServiceTest {

    private SloRepository sloRepository;
    private SliMeasurementRepository measurementRepository;
    private IncidentRepository incidentRepository;
    private ReliabilityTrendService trendService;

    @BeforeEach
    void setUp() {
        sloRepository = mock(SloRepository.class);
        measurementRepository = mock(SliMeasurementRepository.class);
        incidentRepository = mock(IncidentRepository.class);

        trendService = new ReliabilityTrendService(
                sloRepository,
                measurementRepository,
                incidentRepository
        );
    }

    @Test
    @DisplayName("Should generate daily reliability trend data points over window")
    void testGetReliabilityTrends_Daily() {
        when(sloRepository.findByServiceNameAndEnabledTrue("payment-service")).thenReturn(List.of());
        when(incidentRepository.findAllNotDeleted(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        ReliabilityTrendResponse response = trendService.getReliabilityTrends("payment-service", 7, "daily");

        assertThat(response).isNotNull();
        assertThat(response.interval()).isEqualTo("daily");
        assertThat(response.dataPoints()).isNotEmpty();
    }

    @Test
    @DisplayName("Should generate weekly reliability trend data points over window")
    void testGetReliabilityTrends_Weekly() {
        when(sloRepository.findByServiceNameAndEnabledTrue("payment-service")).thenReturn(List.of());
        when(incidentRepository.findAllNotDeleted(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        ReliabilityTrendResponse response = trendService.getReliabilityTrends("payment-service", 30, "weekly");

        assertThat(response).isNotNull();
        assertThat(response.interval()).isEqualTo("weekly");
        assertThat(response.dataPoints()).isNotEmpty();
    }

    @Test
    @DisplayName("Should throw BadRequestException for invalid days parameter")
    void testGetReliabilityTrends_InvalidDays() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> trendService.getReliabilityTrends("payment-service", 120, "daily"))
                .isInstanceOf(com.roottrace.common.exception.BadRequestException.class)
                .hasMessageContaining("days parameter must be between 1 and 90");
    }
}
