package com.roottrace.knowledge.service;

import com.roottrace.ai.config.AiProperties;
import com.roottrace.ai.embedding.AiEmbeddingService;
import com.roottrace.common.audit.AuditEventType;
import com.roottrace.common.audit.AuditService;
import com.roottrace.common.exception.ResourceNotFoundException;
import com.roottrace.common.security.CurrentUserService;
import com.roottrace.knowledge.KnowledgeChunk;
import com.roottrace.knowledge.KnowledgeChunkRepository;
import com.roottrace.knowledge.KnowledgeDocument;
import com.roottrace.knowledge.KnowledgeDocumentRepository;
import com.roottrace.knowledge.KnowledgeDocumentStatus;
import com.roottrace.knowledge.chunking.DocumentChunk;
import com.roottrace.knowledge.chunking.DocumentChunker;
import com.roottrace.knowledge.dto.KnowledgeDocumentResponse;
import com.roottrace.knowledge.dto.KnowledgeDocumentSummaryResponse;
import com.roottrace.knowledge.exception.DocumentProcessingException;
import com.roottrace.knowledge.exception.DocumentTooLargeException;
import com.roottrace.knowledge.ingestion.DocumentParser;
import com.roottrace.knowledge.ingestion.DocumentParserFactory;
import com.roottrace.knowledge.ingestion.ParsedDocument;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class KnowledgeService {

    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeChunkRepository chunkRepository;
    private final DocumentParserFactory parserFactory;
    private final DocumentChunker documentChunker;
    private final AiEmbeddingService embeddingService;
    private final CurrentUserService currentUserService;
    private final AuditService auditService;
    private final long maxDocumentSize;

    public KnowledgeService(
            KnowledgeDocumentRepository documentRepository,
            KnowledgeChunkRepository chunkRepository,
            DocumentParserFactory parserFactory,
            DocumentChunker documentChunker,
            AiEmbeddingService embeddingService,
            CurrentUserService currentUserService,
            AuditService auditService,
            AiProperties aiProperties) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.parserFactory = parserFactory;
        this.documentChunker = documentChunker;
        this.embeddingService = embeddingService;
        this.currentUserService = currentUserService;
        this.auditService = auditService;
        this.maxDocumentSize = aiProperties.getIngestion().getMaxDocumentSize();
    }

    @Transactional
    public KnowledgeDocumentResponse uploadDocument(MultipartFile file) {
        validateFile(file);

        UUID currentUserId = currentUserService.getCurrentUser().getId();

        // 1. Create KnowledgeDocument in PROCESSING state
        KnowledgeDocument document = new KnowledgeDocument();
        document.setTitle(file.getOriginalFilename() != null ? file.getOriginalFilename() : "Untitled");
        document.setOriginalFilename(file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown");
        document.setSourceType("UNKNOWN"); // will be updated
        document.setStatus(KnowledgeDocumentStatus.PROCESSING);
        document.setCreatedBy(currentUserId);
        
        document = documentRepository.save(document);
        
        auditService.record(AuditEventType.KNOWLEDGE_DOCUMENT_CREATED, "KnowledgeDocument", document.getId().toString(), currentUserId.toString(), "Document upload initiated");

        // We commit this and do processing. In Phase 3.2, processing is synchronous, but we split the TX
        // to avoid holding the DB transaction open while calling Gemini. 
        // We will do the processing logic outside a transaction, then save in another transaction.
        return processDocument(document.getId(), file);
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new com.roottrace.knowledge.exception.UnsupportedDocumentException("Empty file is not supported.");
        }
        if (file.getSize() > maxDocumentSize) {
            throw new DocumentTooLargeException("File size exceeds the maximum allowed limit.");
        }
    }

    // Separate transaction boundary for processing
    public KnowledgeDocumentResponse processDocument(UUID documentId, MultipartFile file) {
        KnowledgeDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentProcessingException("Document not found for processing: " + documentId));

        try {
            // 2. Parse
            DocumentParser parser = parserFactory.getParser(file.getOriginalFilename(), file.getContentType());
            ParsedDocument parsedDoc = parser.parse(file.getInputStream(), file.getOriginalFilename());

            // Update document details
            document.setTitle(parsedDoc.title());
            document.setSourceType(parsedDoc.sourceType());
            documentRepository.save(document);

            // 3. Chunk
            List<DocumentChunk> chunks = documentChunker.chunk(parsedDoc.content(), parsedDoc.title());

            // 4. Generate embeddings (batch where possible)
            // Extract texts
            List<String> texts = chunks.stream().map(DocumentChunk::content).toList();
            
            // 5. External API call (no TX held)
            List<float[]> embeddings = embeddingService.embedAll(texts);
            
            if (embeddings == null || embeddings.size() != chunks.size()) {
                throw new DocumentProcessingException("Failed to generate embeddings for all chunks.");
            }

            // 6. Persist chunks in a transaction
            int chunkCount = saveChunks(documentId, chunks, embeddings);

            // 7. Mark READY
            document.setStatus(KnowledgeDocumentStatus.READY);
            document.setUpdatedAt(Instant.now());
            documentRepository.save(document);
            
            auditService.record(AuditEventType.KNOWLEDGE_DOCUMENT_READY, "KnowledgeDocument", document.getId().toString(), document.getCreatedBy().toString(), "Document successfully ingested with " + chunkCount + " chunks");

            return mapToResponse(document, chunkCount);

        } catch (Exception e) {
            document.setStatus(KnowledgeDocumentStatus.FAILED);
            document.setUpdatedAt(Instant.now());
            documentRepository.save(document);
            
            auditService.record(AuditEventType.KNOWLEDGE_DOCUMENT_FAILED, "KnowledgeDocument", document.getId().toString(), document.getCreatedBy().toString(), "Ingestion failed: " + e.getMessage());
            
            throw new DocumentProcessingException("Failed to process knowledge document", e);
        }
    }

    @Transactional
    public int saveChunks(UUID documentId, List<DocumentChunk> chunks, List<float[]> embeddings) {
        // Idempotency: clear existing chunks if we are retrying a failed document
        chunkRepository.deleteByDocumentId(documentId);

        // Fetch document title for FTS denormalization
        String docTitle = documentRepository.findById(documentId)
                .map(d -> d.getTitle())
                .orElse("");

        List<KnowledgeChunk> entities = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            DocumentChunk dc = chunks.get(i);
            KnowledgeChunk entity = new KnowledgeChunk();
            entity.setDocumentId(documentId);
            entity.setChunkIndex(i);
            entity.setContent(dc.content());
            entity.setSectionPath(dc.sectionPath());
            entity.setDocumentTitle(docTitle);
            entity.setEmbedding(embeddings.get(i));
            entities.add(entity);
        }

        chunkRepository.saveAll(entities);
        return entities.size();
    }

    @Transactional(readOnly = true)
    public List<KnowledgeDocumentSummaryResponse> listDocuments() {
        return documentRepository.findAll().stream()
                .filter(d -> d.getDeletedAt() == null)
                .map(this::mapToSummaryResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public KnowledgeDocumentResponse getDocument(UUID id) {
        KnowledgeDocument document = documentRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Knowledge document", id));
        
        int chunkCount = chunkRepository.countByDocumentId(id);
        return mapToResponse(document, chunkCount);
    }

    @Transactional
    public void deleteDocument(UUID id) {
        KnowledgeDocument document = documentRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Knowledge document", id));

        document.setDeletedAt(Instant.now());
        documentRepository.save(document);
        
        // Ensure chunks cannot be retrieved
        chunkRepository.deleteByDocumentId(id);
        
        UUID currentUserId = currentUserService.getCurrentUser().getId();
        auditService.record(AuditEventType.KNOWLEDGE_DOCUMENT_DELETED, "KnowledgeDocument", id.toString(), currentUserId.toString(), "Document soft-deleted");
    }

    private KnowledgeDocumentResponse mapToResponse(KnowledgeDocument doc, int chunkCount) {
        return new KnowledgeDocumentResponse(
                doc.getId(),
                doc.getTitle(),
                doc.getOriginalFilename(),
                doc.getSourceType(),
                doc.getStatus().name(),
                chunkCount,
                doc.getCreatedAt()
        );
    }

    private KnowledgeDocumentSummaryResponse mapToSummaryResponse(KnowledgeDocument doc) {
        return new KnowledgeDocumentSummaryResponse(
                doc.getId(),
                doc.getTitle(),
                doc.getSourceType(),
                doc.getStatus().name(),
                doc.getCreatedAt()
        );
    }
}
