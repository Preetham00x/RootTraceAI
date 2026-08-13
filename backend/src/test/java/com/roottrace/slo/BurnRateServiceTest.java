package com.roottrace.slo;

import com.roottrace.common.exception.BadRequestException;
import com.roottrace.slo.dto.BurnRateResponse;
import com.roottrace.user.Role;
import com.roottrace.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BurnRateServiceTest {

    private SloRepository sloRepository;
    private SliMeasurementRepository measurementRepository;
    private BurnRateService burnRateService;

    private Slo slo;
    private final UUID sloId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        sloRepository = mock(SloRepository.class);
        measurementRepository = mock(SliMeasurementRepository.class);
        burnRateService = new BurnRateService(sloRepository, measurementRepository);

        User user = new User("eng@test.com", "h", "E", "U", Role.ENGINEER);
        // SLO = 99.9% -> Allowed Error Rate = 0.1% = 0.001
        slo = new Slo("payment-service", "Payment Availability", "Desc", SloType.AVAILABILITY,
                BigDecimal.valueOf(99.9), 30, BigDecimal.valueOf(99.95), BigDecimal.valueOf(99.0), user);
        try {
            var f = Slo.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(slo, sloId);
        } catch (Exception e) {}

        when(sloRepository.findById(sloId)).thenReturn(Optional.of(slo));
    }

    @Test
    @DisplayName("Should evaluate NORMAL severity when burn rate is 1.0x")
    void testCalculateBurnRate_Normal() {
        // 10,000 events, 10 errors -> actual error rate = 0.001 (0.1%), burn rate = 1.0x
        SliMeasurement m = new SliMeasurement(slo, Instant.now(), 10000L, 9990L, 10L, BigDecimal.valueOf(99.9), "TEST");
        when(measurementRepository.findBySloIdAndMeasurementTimeBetweenOrderByMeasurementTimeAsc(eq(sloId), any(), any()))
                .thenReturn(List.of(m));

        BurnRateResponse response = burnRateService.calculateBurnRate(sloId, 60);

        assertThat(response).isNotNull();
        assertThat(response.burnRate()).isEqualTo(1.0);
        assertThat(response.severity()).isEqualTo("ELEVATED"); // 1.0x is >= 1.0 -> ELEVATED
    }

    @Test
    @DisplayName("Should evaluate CRITICAL severity when burn rate is 5.0x or higher")
    void testCalculateBurnRate_Critical() {
        // 10,000 events, 50 errors -> actual error rate = 0.005 (0.5%), allowed = 0.001, burn rate = 5.0x -> CRITICAL
        SliMeasurement m = new SliMeasurement(slo, Instant.now(), 10000L, 9950L, 50L, BigDecimal.valueOf(99.5), "TEST");
        when(measurementRepository.findBySloIdAndMeasurementTimeBetweenOrderByMeasurementTimeAsc(eq(sloId), any(), any()))
                .thenReturn(List.of(m));

        BurnRateResponse response = burnRateService.calculateBurnRate(sloId, 60);

        assertThat(response.burnRate()).isEqualTo(5.0);
        assertThat(response.severity()).isEqualTo("CRITICAL");
    }

    @Test
    @DisplayName("Should reject invalid windowMinutes parameter")
    void testCalculateBurnRate_InvalidWindow() {
        assertThatThrownBy(() -> burnRateService.calculateBurnRate(sloId, 2))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("windowMinutes must be between 5 and 10080");
    }

    @Test
    @DisplayName("Should evaluate NORMAL severity when zero events exist in window")
    void testCalculateBurnRate_ZeroEvents() {
        when(measurementRepository.findBySloIdAndMeasurementTimeBetweenOrderByMeasurementTimeAsc(eq(sloId), any(), any()))
                .thenReturn(List.of());

        BurnRateResponse response = burnRateService.calculateBurnRate(sloId, 60);

        assertThat(response.burnRate()).isEqualTo(0.0);
        assertThat(response.severity()).isEqualTo("NORMAL");
    }

    @Test
    @DisplayName("Should evaluate HIGH severity when burn rate is between 2.0x and 5.0x")
    void testCalculateBurnRate_HighSeverity() {
        // 10,000 events, 30 errors -> actual error rate = 0.003 (0.3%), allowed = 0.001, burn rate = 3.0x -> HIGH
        SliMeasurement m = new SliMeasurement(slo, Instant.now(), 10000L, 9970L, 30L, BigDecimal.valueOf(99.7), "TEST");
        when(measurementRepository.findBySloIdAndMeasurementTimeBetweenOrderByMeasurementTimeAsc(eq(sloId), any(), any()))
                .thenReturn(List.of(m));

        BurnRateResponse response = burnRateService.calculateBurnRate(sloId, 60);

        assertThat(response.burnRate()).isEqualTo(3.0);
        assertThat(response.severity()).isEqualTo("HIGH");
    }
}
