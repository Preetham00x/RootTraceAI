package com.roottrace.knowledge.controller;

import com.roottrace.ai.config.AiProperties;
import com.roottrace.knowledge.dto.KnowledgeDocumentResponse;
import com.roottrace.knowledge.dto.KnowledgeDocumentSummaryResponse;
import com.roottrace.knowledge.dto.KnowledgeSearchRequest;
import com.roottrace.knowledge.dto.KnowledgeSearchResponse;
import com.roottrace.knowledge.dto.KnowledgeSearchResult;
import com.roottrace.knowledge.retrieval.HybridRetrievalService;
import com.roottrace.knowledge.retrieval.RankedChunk;
import com.roottrace.knowledge.retrieval.RetrievalQuery;
import com.roottrace.knowledge.retrieval.RetrievalResult;
import com.roottrace.knowledge.service.KnowledgeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Knowledge", description = "Knowledge base management and retrieval")
public class KnowledgeController {

    private final KnowledgeService knowledgeService;
    private final HybridRetrievalService hybridRetrievalService;
    private final AiProperties aiProperties;

    public KnowledgeController(KnowledgeService knowledgeService,
                               HybridRetrievalService hybridRetrievalService,
                               AiProperties aiProperties) {
        this.knowledgeService = knowledgeService;
        this.hybridRetrievalService = hybridRetrievalService;
        this.aiProperties = aiProperties;
    }

    // ─── Document Management ──────────────────────────────────────────────────

    @PostMapping("/api/knowledge/documents")
    @Operation(summary = "Upload and ingest a knowledge document")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENGINEER')")
    public ResponseEntity<KnowledgeDocumentResponse> uploadDocument(
            @RequestParam("file") MultipartFile file) {
        KnowledgeDocumentResponse response = knowledgeService.uploadDocument(file);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/api/knowledge/documents")
    @Operation(summary = "List all knowledge documents")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENGINEER', 'VIEWER')")
    public ResponseEntity<List<KnowledgeDocumentSummaryResponse>> listDocuments() {
        return ResponseEntity.ok(knowledgeService.listDocuments());
    }

    @GetMapping("/api/knowledge/documents/{id}")
    @Operation(summary = "Get a knowledge document by ID")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENGINEER', 'VIEWER')")
    public ResponseEntity<KnowledgeDocumentResponse> getDocument(@PathVariable UUID id) {
        return ResponseEntity.ok(knowledgeService.getDocument(id));
    }

    @DeleteMapping("/api/knowledge/documents/{id}")
    @Operation(summary = "Delete a knowledge document")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENGINEER')")
    public ResponseEntity<Void> deleteDocument(@PathVariable UUID id) {
        knowledgeService.deleteDocument(id);
        return ResponseEntity.noContent().build();
    }

    // ─── Hybrid Search ────────────────────────────────────────────────────────

    @PostMapping("/api/knowledge/search")
    @Operation(summary = "Hybrid knowledge search (FTS + semantic + RRF)")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENGINEER', 'VIEWER')")
    public ResponseEntity<KnowledgeSearchResponse> search(
            @Valid @RequestBody KnowledgeSearchRequest request) {

        int topK = request.effectiveTopK(aiProperties.getRetrieval().getTopK());
        RetrievalQuery query = new RetrievalQuery(
                request.query(), topK, request.service(), request.environment());

        RetrievalResult result = hybridRetrievalService.retrieve(query);

        List<KnowledgeSearchResult> results = result.results().stream()
                .map(this::toSearchResult)
                .toList();

        KnowledgeSearchResponse response = new KnowledgeSearchResponse(
                result.query(),
                results,
                results.size(),
                result.semanticAvailable(),
                result.keywordAvailable()
        );
        return ResponseEntity.ok(response);
    }

    private KnowledgeSearchResult toSearchResult(RankedChunk chunk) {
        return new KnowledgeSearchResult(
                chunk.chunkId(),
                chunk.documentId(),
                chunk.documentTitle(),
                chunk.sectionPath(),
                chunk.content(),
                chunk.semanticScore(),
                chunk.keywordRank(),
                chunk.semanticRank(),
                chunk.rrfScore()
        );
    }
}
