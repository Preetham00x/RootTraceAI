package com.roottrace.investigation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roottrace.common.exception.GlobalExceptionHandler;
import com.roottrace.common.exception.ResourceNotFoundException;
import com.roottrace.common.security.JwtAuthenticationFilter;
import com.roottrace.common.security.JwtService;
import com.roottrace.investigation.dto.CreateInvestigationPlanRequest;
import com.roottrace.investigation.dto.GenerateInvestigationPlanRequest;
import com.roottrace.investigation.dto.InvestigationPlanResponse;
import com.roottrace.investigation.dto.InvestigationStepResponse;
import com.roottrace.investigation.dto.UpdateInvestigationStepRequest;
import com.roottrace.user.dto.UserDto;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = InvestigationController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class InvestigationControllerTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class TestSecurityConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private InvestigationPlanService planService;

    @MockitoBean
    private JwtService jwtService;

    private final UUID incidentId = UUID.randomUUID();
    private final UUID planId = UUID.randomUUID();
    private final UUID stepId = UUID.randomUUID();
    private final UUID diagnosisId = UUID.randomUUID();

    private final UserDto testUserDto = new UserDto(
            UUID.randomUUID(),
            "engineer@roottrace.com",
            "Jane",
            "Doe",
            "ENGINEER"
    );

    @Test
    @DisplayName("Viewer can GET investigation plans")
    @WithMockUser(roles = "VIEWER")
    void testGetPlans_Viewer_Success() throws Exception {
        InvestigationPlanResponse planResponse = new InvestigationPlanResponse(
                planId,
                incidentId,
                diagnosisId,
                "Plan Alpha",
                testUserDto,
                List.of(new InvestigationStepResponse(
                        stepId,
                        1,
                        "Check Logs",
                        "Inspect logs",
                        InvestigationStepStatus.PENDING,
                        null,
                        null,
                        null,
                        Instant.now(),
                        Instant.now()
                )),
                Instant.now(),
                Instant.now()
        );

        when(planService.getPlans(incidentId)).thenReturn(List.of(planResponse));

        mockMvc.perform(get("/api/incidents/{incidentId}/investigation-plans", incidentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(planId.toString()))
                .andExpect(jsonPath("$[0].title").value("Plan Alpha"))
                .andExpect(jsonPath("$[0].steps[0].title").value("Check Logs"));
    }

    @Test
    @DisplayName("Viewer gets 403 Forbidden on POST /generate")
    @WithMockUser(roles = "VIEWER")
    void testGeneratePlan_Viewer_Forbidden() throws Exception {
        GenerateInvestigationPlanRequest request = new GenerateInvestigationPlanRequest(diagnosisId);

        mockMvc.perform(post("/api/incidents/{incidentId}/investigation-plans/generate", incidentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Viewer gets 403 Forbidden on PATCH step")
    @WithMockUser(roles = "VIEWER")
    void testUpdateStep_Viewer_Forbidden() throws Exception {
        UpdateInvestigationStepRequest request = new UpdateInvestigationStepRequest(
                InvestigationStepStatus.COMPLETED,
                "Evidence",
                null
        );

        mockMvc.perform(patch("/api/incidents/{incidentId}/investigation-plans/{planId}/steps/{stepId}", incidentId, planId, stepId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Engineer can generate investigation plan (201 Created)")
    @WithMockUser(roles = "ENGINEER")
    void testGeneratePlan_Engineer_Success() throws Exception {
        GenerateInvestigationPlanRequest request = new GenerateInvestigationPlanRequest(diagnosisId);

        InvestigationPlanResponse planResponse = new InvestigationPlanResponse(
                planId,
                incidentId,
                diagnosisId,
                "Generated Remediation Plan",
                testUserDto,
                List.of(new InvestigationStepResponse(
                        stepId,
                        1,
                        "Verify Health Endpoint",
                        "Curl /health",
                        InvestigationStepStatus.PENDING,
                        null,
                        null,
                        null,
                        Instant.now(),
                        Instant.now()
                )),
                Instant.now(),
                Instant.now()
        );

        when(planService.generatePlan(eq(incidentId), any(GenerateInvestigationPlanRequest.class)))
                .thenReturn(planResponse);

        mockMvc.perform(post("/api/incidents/{incidentId}/investigation-plans/generate", incidentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(planId.toString()))
                .andExpect(jsonPath("$.title").value("Generated Remediation Plan"))
                .andExpect(jsonPath("$.steps[0].title").value("Verify Health Endpoint"));
    }

    @Test
    @DisplayName("Admin can create manual investigation plan (201 Created)")
    @WithMockUser(roles = "ADMIN")
    void testCreatePlan_Admin_Success() throws Exception {
        CreateInvestigationPlanRequest request = new CreateInvestigationPlanRequest(
                "Manual Plan",
                null,
                List.of(new CreateInvestigationPlanRequest.CreateInvestigationStepRequest(
                        "Step 1",
                        "Description 1",
                        null
                ))
        );

        InvestigationPlanResponse planResponse = new InvestigationPlanResponse(
                planId,
                incidentId,
                null,
                "Manual Plan",
                testUserDto,
                List.of(),
                Instant.now(),
                Instant.now()
        );

        when(planService.createPlan(eq(incidentId), any(CreateInvestigationPlanRequest.class)))
                .thenReturn(planResponse);

        mockMvc.perform(post("/api/incidents/{incidentId}/investigation-plans", incidentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Manual Plan"));
    }

    @Test
    @DisplayName("Engineer can update step status and evidence (200 OK)")
    @WithMockUser(roles = "ENGINEER")
    void testUpdateStep_Engineer_Success() throws Exception {
        UpdateInvestigationStepRequest request = new UpdateInvestigationStepRequest(
                InvestigationStepStatus.COMPLETED,
                "Checked pod logs, fix confirmed",
                null
        );

        InvestigationStepResponse stepResponse = new InvestigationStepResponse(
                stepId,
                1,
                "Check Pod Logs",
                "Inspect pod logs",
                InvestigationStepStatus.COMPLETED,
                "Checked pod logs, fix confirmed",
                testUserDto,
                Instant.now(),
                Instant.now(),
                Instant.now()
        );

        when(planService.updateStep(eq(incidentId), eq(planId), eq(stepId), any(UpdateInvestigationStepRequest.class)))
                .thenReturn(stepResponse);

        mockMvc.perform(patch("/api/incidents/{incidentId}/investigation-plans/{planId}/steps/{stepId}", incidentId, planId, stepId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.evidence").value("Checked pod logs, fix confirmed"));
    }

    @Test
    @DisplayName("Should return 404 when plan or step is not found")
    @WithMockUser(roles = "ADMIN")
    void testUpdateStep_NotFound() throws Exception {
        UpdateInvestigationStepRequest request = new UpdateInvestigationStepRequest(
                InvestigationStepStatus.IN_PROGRESS,
                null,
                null
        );

        when(planService.updateStep(eq(incidentId), eq(planId), eq(stepId), any(UpdateInvestigationStepRequest.class)))
                .thenThrow(new ResourceNotFoundException("InvestigationPlan", planId));

        mockMvc.perform(patch("/api/incidents/{incidentId}/investigation-plans/{planId}/steps/{stepId}", incidentId, planId, stepId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }
}
