package com.roottrace.postmortem;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roottrace.common.exception.BadRequestException;
import com.roottrace.common.exception.GlobalExceptionHandler;
import com.roottrace.common.exception.ResourceNotFoundException;
import com.roottrace.common.security.JwtAuthenticationFilter;
import com.roottrace.common.security.JwtService;
import com.roottrace.postmortem.dto.CreateActionItemRequest;
import com.roottrace.postmortem.dto.PostmortemActionItemResponse;
import com.roottrace.postmortem.dto.PostmortemResponse;
import com.roottrace.postmortem.dto.PostmortemTimelineEntry;
import com.roottrace.postmortem.dto.UpdateActionItemRequest;
import com.roottrace.postmortem.dto.UpdatePostmortemRequest;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PostmortemController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class PostmortemControllerTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class TestSecurityConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PostmortemService postmortemService;

    @MockitoBean
    private JwtService jwtService;

    private final UUID incidentId = UUID.randomUUID();
    private final UUID postmortemId = UUID.randomUUID();
    private final UUID actionItemId = UUID.randomUUID();

    private final UserDto testUserDto = new UserDto(
            UUID.randomUUID(),
            "sre@roottrace.com",
            "Alice",
            "Smith",
            "ENGINEER"
    );

    @Test
    @DisplayName("Viewer can GET postmortem (200 OK)")
    @WithMockUser(roles = "VIEWER")
    void testGetPostmortem_Viewer_Success() throws Exception {
        PostmortemResponse response = new PostmortemResponse(
                postmortemId,
                incidentId,
                "Outage Postmortem",
                "Summary",
                "Impact",
                "Root cause",
                "Resolution",
                List.of(new PostmortemTimelineEntry(Instant.now(), "Incident detected", "INCIDENT")),
                List.of("Lesson 1"),
                PostmortemStatus.PUBLISHED,
                45L,
                testUserDto,
                Instant.now(),
                List.of(),
                Instant.now(),
                Instant.now()
        );

        when(postmortemService.getPostmortem(incidentId)).thenReturn(response);

        mockMvc.perform(get("/api/incidents/{incidentId}/postmortem", incidentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(postmortemId.toString()))
                .andExpect(jsonPath("$.title").value("Outage Postmortem"))
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    @Test
    @DisplayName("Viewer can export postmortem as Markdown (200 OK)")
    @WithMockUser(roles = "VIEWER")
    void testExportMarkdown_Viewer_Success() throws Exception {
        String markdown = "# Postmortem: Outage\n\n## Summary\nEverything failed.";
        when(postmortemService.exportMarkdown(incidentId)).thenReturn(markdown);

        mockMvc.perform(get("/api/incidents/{incidentId}/postmortem/export", incidentId))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/markdown;charset=UTF-8"))
                .andExpect(content().string(markdown));
    }

    @Test
    @DisplayName("Viewer gets 403 Forbidden on POST /generate")
    @WithMockUser(roles = "VIEWER")
    void testGeneratePostmortem_Viewer_Forbidden() throws Exception {
        mockMvc.perform(post("/api/incidents/{incidentId}/postmortem/generate", incidentId))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Viewer gets 403 Forbidden on PATCH /postmortem")
    @WithMockUser(roles = "VIEWER")
    void testUpdatePostmortem_Viewer_Forbidden() throws Exception {
        UpdatePostmortemRequest request = new UpdatePostmortemRequest(
                "New Title", null, null, null, null, null, null, PostmortemStatus.PUBLISHED
        );

        mockMvc.perform(patch("/api/incidents/{incidentId}/postmortem", incidentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Viewer gets 403 Forbidden on POST /action-items")
    @WithMockUser(roles = "VIEWER")
    void testCreateActionItem_Viewer_Forbidden() throws Exception {
        CreateActionItemRequest request = new CreateActionItemRequest(
                "Action 1", "Desc", ActionItemCategory.PREVENT, ActionItemPriority.HIGH, null, null
        );

        mockMvc.perform(post("/api/incidents/{incidentId}/postmortem/action-items", incidentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Engineer can generate postmortem (201 Created)")
    @WithMockUser(roles = "ENGINEER")
    void testGeneratePostmortem_Engineer_Success() throws Exception {
        PostmortemResponse response = new PostmortemResponse(
                postmortemId,
                incidentId,
                "Generated Postmortem",
                "Summary",
                "Impact",
                "Root cause",
                "Resolution",
                List.of(),
                List.of(),
                PostmortemStatus.DRAFT,
                30L,
                testUserDto,
                null,
                List.of(),
                Instant.now(),
                Instant.now()
        );

        when(postmortemService.generatePostmortem(incidentId)).thenReturn(response);

        mockMvc.perform(post("/api/incidents/{incidentId}/postmortem/generate", incidentId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(postmortemId.toString()))
                .andExpect(jsonPath("$.title").value("Generated Postmortem"))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    @DisplayName("Engineer can update postmortem content and status (200 OK)")
    @WithMockUser(roles = "ENGINEER")
    void testUpdatePostmortem_Engineer_Success() throws Exception {
        UpdatePostmortemRequest request = new UpdatePostmortemRequest(
                "Updated Title", "Updated Summary", null, null, null, null, null, PostmortemStatus.PUBLISHED
        );

        PostmortemResponse response = new PostmortemResponse(
                postmortemId,
                incidentId,
                "Updated Title",
                "Updated Summary",
                "Impact",
                "Root cause",
                "Resolution",
                List.of(),
                List.of(),
                PostmortemStatus.PUBLISHED,
                30L,
                testUserDto,
                Instant.now(),
                List.of(),
                Instant.now(),
                Instant.now()
        );

        when(postmortemService.updatePostmortem(eq(incidentId), any(UpdatePostmortemRequest.class)))
                .thenReturn(response);

        mockMvc.perform(patch("/api/incidents/{incidentId}/postmortem", incidentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated Title"))
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    @Test
    @DisplayName("Engineer can add action item (201 Created)")
    @WithMockUser(roles = "ENGINEER")
    void testCreateActionItem_Engineer_Success() throws Exception {
        CreateActionItemRequest request = new CreateActionItemRequest(
                "Add Metric", "Add pool usage gauge", ActionItemCategory.DETECT, ActionItemPriority.MEDIUM, null, null
        );

        PostmortemActionItemResponse response = new PostmortemActionItemResponse(
                actionItemId,
                postmortemId,
                "Add Metric",
                "Add pool usage gauge",
                ActionItemCategory.DETECT,
                ActionItemPriority.MEDIUM,
                ActionItemStatus.OPEN,
                testUserDto,
                null,
                null,
                Instant.now(),
                Instant.now()
        );

        when(postmortemService.createActionItem(eq(incidentId), any(CreateActionItemRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/incidents/{incidentId}/postmortem/action-items", incidentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(actionItemId.toString()))
                .andExpect(jsonPath("$.title").value("Add Metric"));
    }

    @Test
    @DisplayName("Should return 404 when postmortem is not found")
    @WithMockUser(roles = "ENGINEER")
    void testGetPostmortem_NotFound() throws Exception {
        when(postmortemService.getPostmortem(incidentId))
                .thenThrow(new ResourceNotFoundException("Postmortem for incident", incidentId));

        mockMvc.perform(get("/api/incidents/{incidentId}/postmortem", incidentId))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should return 400 when generating postmortem for unresolved incident")
    @WithMockUser(roles = "ENGINEER")
    void testGeneratePostmortem_UnresolvedIncident() throws Exception {
        when(postmortemService.generatePostmortem(incidentId))
                .thenThrow(new BadRequestException("Incident must be RESOLVED or CLOSED"));

        mockMvc.perform(post("/api/incidents/{incidentId}/postmortem/generate", incidentId))
                .andExpect(status().isBadRequest());
    }
}
