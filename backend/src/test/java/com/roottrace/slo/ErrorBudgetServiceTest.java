package com.roottrace.slo;

import com.roottrace.slo.dto.ErrorBudgetResponse;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ErrorBudgetServiceTest {

    private SloRepository sloRepository;
    private SliMeasurementRepository measurementRepository;
    private ErrorBudgetService errorBudgetService;

    private Slo slo;
    private final UUID sloId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        sloRepository = mock(SloRepository.class);
        measurementRepository = mock(SliMeasurementRepository.class);
        errorBudgetService = new ErrorBudgetService(sloRepository, measurementRepository);

        User user = new User("eng@test.com", "h", "E", "U", Role.ENGINEER);
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
    @DisplayName("Should evaluate HEALTHY error budget when consumed is under 75%")
    void testCalculateErrorBudget_Healthy() {
        // 1,000,000 total events, 0.1% budget = 1,000 allowed bad events. 300 actual bad events = 30% consumed, 70% remaining.
        SliMeasurement m = new SliMeasurement(slo, Instant.now(), 1000000L, 999700L, 300L, BigDecimal.valueOf(99.97), "TEST");
        when(measurementRepository.findBySloIdAndMeasurementTimeBetweenOrderByMeasurementTimeAsc(eq(sloId), any(), any()))
                .thenReturn(List.of(m));

        ErrorBudgetResponse response = errorBudgetService.calculateErrorBudget(sloId);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(SloStatus.HEALTHY);
        assertThat(response.totalEvents()).isEqualTo(1000000L);
        assertThat(response.allowedBadEvents()).isEqualTo(1000L);
        assertThat(response.actualBadEvents()).isEqualTo(300L);
        assertThat(response.remainingBadEvents()).isEqualTo(700L);
        assertThat(response.budgetConsumedPercentage()).isEqualTo(30.0);
        assertThat(response.budgetRemainingPercentage()).isEqualTo(70.0);
    }

    @Test
    @DisplayName("Should evaluate WARNING error budget when remaining is between 0% and 25%")
    void testCalculateErrorBudget_Warning() {
        // 1,000 allowed bad events, 850 actual bad events = 85% consumed, 15% remaining -> WARNING
        SliMeasurement m = new SliMeasurement(slo, Instant.now(), 1000000L, 999150L, 850L, BigDecimal.valueOf(99.915), "TEST");
        when(measurementRepository.findBySloIdAndMeasurementTimeBetweenOrderByMeasurementTimeAsc(eq(sloId), any(), any()))
                .thenReturn(List.of(m));

        ErrorBudgetResponse response = errorBudgetService.calculateErrorBudget(sloId);

        assertThat(response.status()).isEqualTo(SloStatus.WARNING);
        assertThat(response.budgetConsumedPercentage()).isEqualTo(85.0);
        assertThat(response.budgetRemainingPercentage()).isEqualTo(15.0);
    }

    @Test
    @DisplayName("Should evaluate BREACHED error budget when actual bad events exceed allowed")
    void testCalculateErrorBudget_Breached() {
        // 1,000 allowed bad events, 1,200 actual bad events = 100% consumed, 0% remaining -> BREACHED
        SliMeasurement m = new SliMeasurement(slo, Instant.now(), 1000000L, 998800L, 1200L, BigDecimal.valueOf(99.88), "TEST");
        when(measurementRepository.findBySloIdAndMeasurementTimeBetweenOrderByMeasurementTimeAsc(eq(sloId), any(), any()))
                .thenReturn(List.of(m));

        ErrorBudgetResponse response = errorBudgetService.calculateErrorBudget(sloId);

        assertThat(response.status()).isEqualTo(SloStatus.BREACHED);
        assertThat(response.remainingBadEvents()).isEqualTo(0L);
        assertThat(response.budgetRemainingPercentage()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Should handle zero events gracefully with 100% remaining budget")
    void testCalculateErrorBudget_ZeroEvents() {
        when(measurementRepository.findBySloIdAndMeasurementTimeBetweenOrderByMeasurementTimeAsc(eq(sloId), any(), any()))
                .thenReturn(List.of());

        ErrorBudgetResponse response = errorBudgetService.calculateErrorBudget(sloId);

        assertThat(response.totalEvents()).isEqualTo(0L);
        assertThat(response.status()).isEqualTo(SloStatus.HEALTHY);
        assertThat(response.budgetRemainingPercentage()).isEqualTo(100.0);
    }
}
