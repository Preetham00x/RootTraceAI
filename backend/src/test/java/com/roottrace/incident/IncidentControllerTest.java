package com.roottrace.incident;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roottrace.incident.dto.CreatorResponse;
import com.roottrace.incident.dto.CreateIncidentRequest;
import com.roottrace.incident.dto.IncidentResponse;
import com.roottrace.incident.dto.IncidentSummaryResponse;
import com.roottrace.incident.dto.ResolveIncidentRequest;
import com.roottrace.incident.dto.UpdateIncidentRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import com.roottrace.common.security.JwtService;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = IncidentController.class, excludeAutoConfiguration = {SecurityAutoConfiguration.class})
class IncidentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private IncidentService incidentService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    private static final UUID INCIDENT_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.now();

    @Test
    @DisplayName("POST /api/incidents — 201 Created with valid request")
    void shouldCreateIncident() throws Exception {
        CreateIncidentRequest request = new CreateIncidentRequest(
                "DB connection timeout", "Pool exhausted", "payment-service",
                IncidentSeverity.HIGH, "production");

        CreatorResponse creator = new CreatorResponse(UUID.randomUUID(), "engineer@test.com");
        IncidentResponse response = new IncidentResponse(
                INCIDENT_ID, "DB connection timeout", "Pool exhausted", "payment-service",
                IncidentSeverity.HIGH, IncidentStatus.OPEN, "production",
                creator, NOW, NOW, null, null);

        when(incidentService.create(any(CreateIncidentRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(INCIDENT_ID.toString()))
                .andExpect(jsonPath("$.title").value("DB connection timeout"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.severity").value("HIGH"));
    }

    @Test
    @DisplayName("POST /api/incidents — 422 with missing required fields")
    void shouldRejectInvalidCreate() throws Exception {
        CreateIncidentRequest request = new CreateIncidentRequest(
                "", null, "", null, null);

        mockMvc.perform(post("/api/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(3)));
    }

    @Test
    @DisplayName("GET /api/incidents/{id} — 200 OK")
    void shouldGetIncidentById() throws Exception {
        CreatorResponse creator = new CreatorResponse(UUID.randomUUID(), "user@test.com");
        IncidentResponse response = new IncidentResponse(
                INCIDENT_ID, "Test incident", "Description", "api-gateway",
                IncidentSeverity.MEDIUM, IncidentStatus.OPEN, "staging",
                creator, NOW, NOW, null, null);

        when(incidentService.getById(INCIDENT_ID)).thenReturn(response);

        mockMvc.perform(get("/api/incidents/{id}", INCIDENT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(INCIDENT_ID.toString()))
                .andExpect(jsonPath("$.title").value("Test incident"));
    }

    @Test
    @DisplayName("GET /api/incidents — 200 OK with pagination")
    void shouldListIncidents() throws Exception {
        CreatorResponse creator = new CreatorResponse(UUID.randomUUID(), "user@test.com");
        IncidentSummaryResponse summary = new IncidentSummaryResponse(
                INCIDENT_ID, "Test incident", "api-gateway",
                IncidentSeverity.MEDIUM, IncidentStatus.OPEN, "production",
                creator, NOW);

        Page<IncidentSummaryResponse> page = new PageImpl<>(List.of(summary));
        when(incidentService.list(any(Pageable.class), any(), any(), any(), any(), any(), any()))
                .thenReturn(page);

        mockMvc.perform(get("/api/incidents")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].id").value(INCIDENT_ID.toString()))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("PUT /api/incidents/{id} — 200 OK")
    void shouldUpdateIncident() throws Exception {
        UpdateIncidentRequest request = new UpdateIncidentRequest(
                "Updated title", null, null, IncidentSeverity.CRITICAL, null, null);

        CreatorResponse creator = new CreatorResponse(UUID.randomUUID(), "engineer@test.com");
        IncidentResponse response = new IncidentResponse(
                INCIDENT_ID, "Updated title", "Description", "payment-service",
                IncidentSeverity.CRITICAL, IncidentStatus.OPEN, "production",
                creator, NOW, NOW, null, null);

        when(incidentService.update(eq(INCIDENT_ID), any(UpdateIncidentRequest.class)))
                .thenReturn(response);

        mockMvc.perform(put("/api/incidents/{id}", INCIDENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated title"))
                .andExpect(jsonPath("$.severity").value("CRITICAL"));
    }

    @Test
    @DisplayName("PATCH /api/incidents/{id}/resolve — 200 OK")
    void shouldResolveIncident() throws Exception {
        ResolveIncidentRequest request = new ResolveIncidentRequest("Increased pool size");

        CreatorResponse creator = new CreatorResponse(UUID.randomUUID(), "engineer@test.com");
        IncidentResponse response = new IncidentResponse(
                INCIDENT_ID, "DB timeout", "Desc", "payment-service",
                IncidentSeverity.HIGH, IncidentStatus.RESOLVED, "production",
                creator, NOW, NOW, NOW, "Increased pool size");

        when(incidentService.resolve(eq(INCIDENT_ID), eq("Increased pool size")))
                .thenReturn(response);

        mockMvc.perform(patch("/api/incidents/{id}/resolve", INCIDENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolution").value("Increased pool size"));
    }

    @Test
    @DisplayName("PATCH /api/incidents/{id}/resolve — 422 with empty resolution")
    void shouldRejectEmptyResolution() throws Exception {
        ResolveIncidentRequest request = new ResolveIncidentRequest("");

        mockMvc.perform(patch("/api/incidents/{id}/resolve", INCIDENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("PATCH /api/incidents/{id}/close — 200 OK")
    void shouldCloseIncident() throws Exception {
        CreatorResponse creator = new CreatorResponse(UUID.randomUUID(), "engineer@test.com");
        IncidentResponse response = new IncidentResponse(
                INCIDENT_ID, "DB timeout", "Desc", "payment-service",
                IncidentSeverity.HIGH, IncidentStatus.CLOSED, "production",
                creator, NOW, NOW, NOW, "Fixed");

        when(incidentService.close(INCIDENT_ID)).thenReturn(response);

        mockMvc.perform(patch("/api/incidents/{id}/close", INCIDENT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    @Test
    @DisplayName("DELETE /api/incidents/{id} — 204 No Content")
    void shouldDeleteIncident() throws Exception {
        doNothing().when(incidentService).delete(INCIDENT_ID);

        mockMvc.perform(delete("/api/incidents/{id}", INCIDENT_ID))
                .andExpect(status().isNoContent());
    }
}
