package com.roottrace.knowledge.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roottrace.common.security.JwtAuthenticationFilter;
import com.roottrace.common.security.JwtService;
import com.roottrace.knowledge.dto.KnowledgeDocumentResponse;
import com.roottrace.knowledge.dto.KnowledgeDocumentSummaryResponse;
import com.roottrace.knowledge.service.KnowledgeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = KnowledgeController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
class KnowledgeControllerTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class TestSecurityConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private KnowledgeService knowledgeService;

    @MockitoBean
    private com.roottrace.knowledge.retrieval.HybridRetrievalService hybridRetrievalService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private com.roottrace.ai.config.AiProperties aiProperties;

    @Test
    @WithMockUser(roles = "ADMIN")
    void testUploadDocument_Admin_Success() throws Exception {
        UUID id = UUID.randomUUID();
        KnowledgeDocumentResponse response = new KnowledgeDocumentResponse(id, "Title", "test.md", "MARKDOWN", "READY", 2, Instant.now());
        
        when(knowledgeService.uploadDocument(any())).thenReturn(response);

        MockMultipartFile file = new MockMultipartFile("file", "test.md", "text/markdown", "content".getBytes());

        mockMvc.perform(multipart("/api/knowledge/documents")
                        .file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.status").value("READY"));
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void testUploadDocument_Viewer_Forbidden() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.md", "text/markdown", "content".getBytes());

        mockMvc.perform(multipart("/api/knowledge/documents")
                        .file(file))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ENGINEER")
    void testListDocuments_Success() throws Exception {
        UUID id = UUID.randomUUID();
        KnowledgeDocumentSummaryResponse response = new KnowledgeDocumentSummaryResponse(id, "Title", "MARKDOWN", "READY", Instant.now());
        
        when(knowledgeService.listDocuments()).thenReturn(List.of(response));

        mockMvc.perform(get("/api/knowledge/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id.toString()));
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void testGetDocument_Viewer_Success() throws Exception {
        UUID id = UUID.randomUUID();
        KnowledgeDocumentResponse response = new KnowledgeDocumentResponse(id, "Title", "test.md", "MARKDOWN", "READY", 2, Instant.now());
        
        when(knowledgeService.getDocument(id)).thenReturn(response);

        mockMvc.perform(get("/api/knowledge/documents/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    @WithMockUser(roles = "ENGINEER")
    void testDeleteDocument_Engineer_Success() throws Exception {
        UUID id = UUID.randomUUID();
        doNothing().when(knowledgeService).deleteDocument(id);

        mockMvc.perform(delete("/api/knowledge/documents/" + id))
                .andExpect(status().isNoContent());
    }
}
