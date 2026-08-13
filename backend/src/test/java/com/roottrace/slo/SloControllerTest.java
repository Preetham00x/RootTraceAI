package com.roottrace.slo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roottrace.common.exception.GlobalExceptionHandler;
import com.roottrace.common.security.CurrentUserService;
import com.roottrace.common.security.JwtAuthenticationFilter;
import com.roottrace.common.security.JwtService;
import com.roottrace.slo.dto.BurnRateResponse;
import com.roottrace.slo.dto.CreateSloRequest;
import com.roottrace.slo.dto.ErrorBudgetResponse;
import com.roottrace.slo.dto.RecordSliMeasurementRequest;
import com.roottrace.slo.dto.ReliabilityAdvisorResponse;
import com.roottrace.slo.dto.ReliabilityDashboardResponse;
import com.roottrace.slo.dto.ReliabilityRiskResponse;
import com.roottrace.slo.dto.ReliabilityTrendResponse;
import com.roottrace.slo.dto.SliMeasurementResponse;
import com.roottrace.slo.dto.SloEvaluationResponse;
import com.roottrace.slo.dto.SloResponse;
import com.roottrace.user.Role;
import com.roottrace.user.User;
import com.roottrace.user.dto.UserDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {SloController.class, ReliabilityController.class},
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class SloControllerTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class TestSecurityConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private SloService sloService;

    @MockitoBean
    private SloEvaluationService sloEvaluationService;

    @MockitoBean
    private ErrorBudgetService errorBudgetService;

    @MockitoBean
    private BurnRateService burnRateService;

    @MockitoBean
    private ReliabilityRiskService reliabilityRiskService;

    @MockitoBean
    private ReliabilityTrendService reliabilityTrendService;

    @MockitoBean
    private ReliabilityAdvisorService reliabilityAdvisorService;

    @MockitoBean
    private CurrentUserService currentUserService;

    @MockitoBean
    private JwtService jwtService;

    private final UUID sloId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        User adminUser = new User("admin@roottrace.com", "h", "A", "U", Role.ADMIN);
        try {
            var f = User.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(adminUser, UUID.randomUUID());
        } catch (Exception e) {}
        when(currentUserService.getCurrentUser()).thenReturn(adminUser);
    }

    @Test
    @DisplayName("Engineer can create SLO (201 Created)")
    @WithMockUser(roles = "ENGINEER")
    void testCreateSlo_Engineer_Success() throws Exception {
        CreateSloRequest request = new CreateSloRequest(
                "Payment Availability", "Desc", SloType.AVAILABILITY,
                BigDecimal.valueOf(99.9), 30, BigDecimal.valueOf(99.95), BigDecimal.valueOf(99.0)
        );

        UserDto userDto = new UserDto(UUID.randomUUID(), "engineer@roottrace.com", "Eng", "Ineer", "ENGINEER");
        SloResponse response = new SloResponse(
                sloId, "payment-service", "Payment Availability", "Desc", SloType.AVAILABILITY,
                BigDecimal.valueOf(99.9), 30, BigDecimal.valueOf(99.95), BigDecimal.valueOf(99.0), true,
                userDto, Instant.now(), Instant.now()
        );

        when(sloService.createSlo(eq("payment-service"), any())).thenReturn(response);

        mockMvc.perform(post("/api/services/payment-service/slos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Payment Availability"))
                .andExpect(jsonPath("$.targetPercentage").value(99.9));
    }

    @Test
    @DisplayName("Viewer cannot create SLO (403 Forbidden)")
    @WithMockUser(roles = "VIEWER")
    void testCreateSlo_Viewer_Forbidden() throws Exception {
        CreateSloRequest request = new CreateSloRequest(
                "Payment Availability", "Desc", SloType.AVAILABILITY,
                BigDecimal.valueOf(99.9), 30, null, null
        );

        mockMvc.perform(post("/api/services/payment-service/slos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Viewer can list SLOs (200 OK)")
    @WithMockUser(roles = "VIEWER")
    void testListSlos_Viewer_Success() throws Exception {
        when(sloService.listSlos("payment-service")).thenReturn(List.of());

        mockMvc.perform(get("/api/services/payment-service/slos"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Admin can disable SLO (204 No Content)")
    @WithMockUser(roles = "ADMIN")
    void testDisableSlo_Admin_Success() throws Exception {
        mockMvc.perform(delete("/api/services/payment-service/slos/" + sloId))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Engineer cannot disable SLO (403 Forbidden)")
    @WithMockUser(roles = "ENGINEER")
    void testDisableSlo_Engineer_Forbidden() throws Exception {
        mockMvc.perform(delete("/api/services/payment-service/slos/" + sloId))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Engineer can record SLI measurement (201 Created)")
    @WithMockUser(roles = "ENGINEER")
    void testRecordMeasurement_Engineer_Success() throws Exception {
        RecordSliMeasurementRequest request = new RecordSliMeasurementRequest(
                Instant.now(), 1000L, 999L, 1L, BigDecimal.valueOf(99.9), "PROMETHEUS"
        );

        SliMeasurementResponse response = new SliMeasurementResponse(
                UUID.randomUUID(), sloId, Instant.now(), 1000L, 999L, 1L, BigDecimal.valueOf(99.9), "PROMETHEUS", Instant.now()
        );

        when(sloService.recordMeasurement(eq("payment-service"), eq(sloId), any())).thenReturn(response);

        mockMvc.perform(post("/api/services/payment-service/slos/" + sloId + "/measurements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.goodEvents").value(999));
    }

    @Test
    @DisplayName("Viewer can get error budget evaluation (200 OK)")
    @WithMockUser(roles = "VIEWER")
    void testGetErrorBudget_Viewer_Success() throws Exception {
        ErrorBudgetResponse response = new ErrorBudgetResponse(
                sloId, "payment-service", "Payment Availability", 99.9, 0.1, 10000L, 10L, 4L, 6L, 40.0, 60.0, SloStatus.HEALTHY
        );

        when(errorBudgetService.calculateErrorBudget(sloId)).thenReturn(response);

        mockMvc.perform(get("/api/services/payment-service/slos/" + sloId + "/error-budget"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("HEALTHY"))
                .andExpect(jsonPath("$.budgetRemainingPercentage").value(60.0));
    }

    @Test
    @DisplayName("Viewer can get burn rate (200 OK)")
    @WithMockUser(roles = "VIEWER")
    void testGetBurnRate_Viewer_Success() throws Exception {
        BurnRateResponse response = new BurnRateResponse(
                sloId, "payment-service", "Payment Availability", 1.5, "ELEVATED", 60, 0.15, 0.1, Instant.now()
        );

        when(burnRateService.calculateBurnRate(sloId, 60)).thenReturn(response);

        mockMvc.perform(get("/api/services/payment-service/slos/" + sloId + "/burn-rate?windowMinutes=60"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.burnRate").value(1.5))
                .andExpect(jsonPath("$.severity").value("ELEVATED"));
    }

    @Test
    @DisplayName("Viewer can get reliability dashboard (200 OK)")
    @WithMockUser(roles = "VIEWER")
    void testGetReliabilityDashboard_Viewer_Success() throws Exception {
        ReliabilityDashboardResponse dashboard = new ReliabilityDashboardResponse(
                "payment-service", 45.0, "MEDIUM", List.of(), 0, 30.0, 1.2, 2, 0.1, 1, Instant.now()
        );

        when(sloService.getReliabilityDashboard("payment-service")).thenReturn(dashboard);

        mockMvc.perform(get("/api/services/payment-service/reliability"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.riskTier").value("MEDIUM"))
                .andExpect(jsonPath("$.overallRiskScore").value(45.0));
    }
}
