package com.roottrace.slo;

import com.roottrace.common.audit.AuditEventType;
import com.roottrace.common.audit.AuditService;
import com.roottrace.common.exception.BadRequestException;
import com.roottrace.common.security.CurrentUserService;
import com.roottrace.incident.IncidentRepository;
import com.roottrace.postmortem.PostmortemActionItemRepository;
import com.roottrace.slo.dto.CreateSloRequest;
import com.roottrace.slo.dto.RecordSliMeasurementRequest;
import com.roottrace.slo.dto.SliMeasurementResponse;
import com.roottrace.slo.dto.SloResponse;
import com.roottrace.slo.dto.UpdateSloRequest;
import com.roottrace.user.Role;
import com.roottrace.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SloServiceTest {

    private SloRepository sloRepository;
    private SliMeasurementRepository measurementRepository;
    private SloEvaluationService sloEvaluationService;
    private ErrorBudgetService errorBudgetService;
    private BurnRateService burnRateService;
    private ReliabilityRiskService reliabilityRiskService;
    private IncidentRepository incidentRepository;
    private PostmortemActionItemRepository actionItemRepository;
    private CurrentUserService currentUserService;
    private AuditService auditService;
    private SloService sloService;

    private User testUser;

    @BeforeEach
    void setUp() {
        sloRepository = mock(SloRepository.class);
        measurementRepository = mock(SliMeasurementRepository.class);
        sloEvaluationService = mock(SloEvaluationService.class);
        errorBudgetService = mock(ErrorBudgetService.class);
        burnRateService = mock(BurnRateService.class);
        reliabilityRiskService = mock(ReliabilityRiskService.class);
        incidentRepository = mock(IncidentRepository.class);
        actionItemRepository = mock(PostmortemActionItemRepository.class);
        currentUserService = mock(CurrentUserService.class);
        auditService = mock(AuditService.class);

        testUser = new User("engineer@roottrace.com", "hash", "Eng", "Ineer", Role.ENGINEER);
        try {
            var f = User.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(testUser, UUID.randomUUID());
        } catch (Exception e) {}
        when(currentUserService.getCurrentUser()).thenReturn(testUser);

        sloService = new SloService(
                sloRepository,
                measurementRepository,
                sloEvaluationService,
                errorBudgetService,
                burnRateService,
                reliabilityRiskService,
                incidentRepository,
                actionItemRepository,
                currentUserService,
                auditService
        );
    }

    @Test
    @DisplayName("Should create SLO successfully and record audit event")
    void testCreateSlo_Success() {
        CreateSloRequest request = new CreateSloRequest(
                "Payment Availability", "Availability of checkout API", SloType.AVAILABILITY,
                BigDecimal.valueOf(99.9), 30, BigDecimal.valueOf(99.95), BigDecimal.valueOf(99.0)
        );

        when(sloRepository.findByServiceNameAndName("payment-service", "Payment Availability"))
                .thenReturn(Optional.empty());

        when(sloRepository.save(any(Slo.class))).thenAnswer(inv -> {
            Slo s = inv.getArgument(0);
            try {
                var f = Slo.class.getDeclaredField("id");
                f.setAccessible(true);
                f.set(s, UUID.randomUUID());
            } catch (Exception e) {}
            return s;
        });

        SloResponse response = sloService.createSlo("payment-service", request);

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("Payment Availability");
        assertThat(response.targetPercentage()).isEqualByComparingTo("99.9");
        assertThat(response.enabled()).isTrue();

        verify(auditService).record(
                eq(AuditEventType.SLO_CREATED),
                eq("Slo"),
                any(),
                eq("engineer@roottrace.com"),
                any()
        );
    }

    @Test
    @DisplayName("Should throw BadRequestException when creating SLO with duplicate name in service")
    void testCreateSlo_DuplicateName() {
        CreateSloRequest request = new CreateSloRequest(
                "Payment Availability", null, SloType.AVAILABILITY,
                BigDecimal.valueOf(99.9), 30, null, null
        );

        Slo existing = new Slo();
        when(sloRepository.findByServiceNameAndName("payment-service", "Payment Availability"))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> sloService.createSlo("payment-service", request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("Should update SLO successfully and emit SLO_UPDATED audit event")
    void testUpdateSlo_Success() {
        UUID sloId = UUID.randomUUID();
        Slo existing = new Slo("payment-service", "Old Name", "Desc", SloType.AVAILABILITY,
                BigDecimal.valueOf(99.5), 30, BigDecimal.valueOf(99.0), BigDecimal.valueOf(95.0), testUser);
        try {
            var f = Slo.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(existing, sloId);
        } catch (Exception e) {}

        when(sloRepository.findByIdAndServiceName(sloId, "payment-service")).thenReturn(Optional.of(existing));
        when(sloRepository.save(any(Slo.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateSloRequest request = new UpdateSloRequest(
                "New Name", "New Desc", null, BigDecimal.valueOf(99.9), null, null, null, true
        );

        SloResponse response = sloService.updateSlo("payment-service", sloId, request);

        assertThat(response.name()).isEqualTo("New Name");
        assertThat(response.targetPercentage()).isEqualByComparingTo("99.9");

        verify(auditService).record(
                eq(AuditEventType.SLO_UPDATED),
                eq("Slo"),
                eq(sloId.toString()),
                any(),
                any()
        );
    }

    @Test
    @DisplayName("Should soft-disable SLO and emit SLO_DISABLED audit event")
    void testDisableSlo_Success() {
        UUID sloId = UUID.randomUUID();
        Slo existing = new Slo("payment-service", "Payment Availability", "Desc", SloType.AVAILABILITY,
                BigDecimal.valueOf(99.9), 30, null, null, testUser);
        try {
            var f = Slo.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(existing, sloId);
        } catch (Exception e) {}

        when(sloRepository.findByIdAndServiceName(sloId, "payment-service")).thenReturn(Optional.of(existing));
        when(sloRepository.save(any(Slo.class))).thenAnswer(inv -> inv.getArgument(0));

        sloService.disableSlo("payment-service", sloId);

        assertThat(existing.getEnabled()).isFalse();
        verify(auditService).record(
                eq(AuditEventType.SLO_DISABLED),
                eq("Slo"),
                eq(sloId.toString()),
                any(),
                any()
        );
    }

    @Test
    @DisplayName("Should record valid SLI measurement and compute percentage value")
    void testRecordMeasurement_Success() {
        UUID sloId = UUID.randomUUID();
        Slo slo = new Slo("payment-service", "Payment Availability", null, SloType.AVAILABILITY,
                BigDecimal.valueOf(99.9), 30, null, null, testUser);
        try {
            var f = Slo.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(slo, sloId);
        } catch (Exception e) {}

        when(sloRepository.findByIdAndServiceName(sloId, "payment-service")).thenReturn(Optional.of(slo));
        when(measurementRepository.save(any(SliMeasurement.class))).thenAnswer(inv -> {
            SliMeasurement m = inv.getArgument(0);
            try {
                var f = SliMeasurement.class.getDeclaredField("id");
                f.setAccessible(true);
                f.set(m, UUID.randomUUID());
            } catch (Exception e) {}
            return m;
        });

        RecordSliMeasurementRequest request = new RecordSliMeasurementRequest(
                Instant.now(), 10000L, 9995L, 5L, null, "PROMETHEUS"
        );

        SliMeasurementResponse response = sloService.recordMeasurement("payment-service", sloId, request);

        assertThat(response).isNotNull();
        assertThat(response.totalEvents()).isEqualTo(10000L);
        assertThat(response.goodEvents()).isEqualTo(9995L);
        assertThat(response.badEvents()).isEqualTo(5L);
        assertThat(response.value()).isEqualByComparingTo("99.950000");
    }

    @Test
    @DisplayName("Should reject SLI measurement where good + bad events exceed total events")
    void testRecordMeasurement_InvalidEventSum() {
        UUID sloId = UUID.randomUUID();
        Slo slo = new Slo("payment-service", "Payment Availability", null, SloType.AVAILABILITY,
                BigDecimal.valueOf(99.9), 30, null, null, testUser);
        when(sloRepository.findByIdAndServiceName(sloId, "payment-service")).thenReturn(Optional.of(slo));

        RecordSliMeasurementRequest request = new RecordSliMeasurementRequest(
                Instant.now(), 1000L, 900L, 200L, null, "API"
        );

        assertThatThrownBy(() -> sloService.recordMeasurement("payment-service", sloId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("cannot exceed totalEvents");
    }
}
