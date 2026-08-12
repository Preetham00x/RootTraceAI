package com.roottrace.knowledge.service;

import com.roottrace.ai.config.AiProperties;
import com.roottrace.ai.embedding.AiEmbeddingService;
import com.roottrace.common.audit.AuditService;
import com.roottrace.common.security.CurrentUserService;
import com.roottrace.knowledge.KnowledgeChunkRepository;
import com.roottrace.knowledge.KnowledgeDocument;
import com.roottrace.knowledge.KnowledgeDocumentRepository;
import com.roottrace.knowledge.KnowledgeDocumentStatus;
import com.roottrace.knowledge.chunking.DocumentChunk;
import com.roottrace.knowledge.chunking.DocumentChunker;
import com.roottrace.knowledge.dto.KnowledgeDocumentResponse;
import com.roottrace.knowledge.exception.DocumentProcessingException;
import com.roottrace.knowledge.exception.DocumentTooLargeException;
import com.roottrace.knowledge.ingestion.DocumentParser;
import com.roottrace.knowledge.ingestion.DocumentParserFactory;
import com.roottrace.knowledge.ingestion.ParsedDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeServiceTest {

    @Mock
    private KnowledgeDocumentRepository documentRepository;
    @Mock
    private KnowledgeChunkRepository chunkRepository;
    @Mock
    private DocumentParserFactory parserFactory;
    @Mock
    private DocumentChunker documentChunker;
    @Mock
    private AiEmbeddingService embeddingService;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private AuditService auditService;
    @Mock
    private DocumentParser parser;

    private KnowledgeService knowledgeService;
    private com.roottrace.user.User mockUser;

    @BeforeEach
    void setUp() {
        AiProperties aiProperties = new AiProperties();
        aiProperties.getIngestion().setMaxDocumentSize(1024); // 1KB for testing
        
        knowledgeService = new KnowledgeService(
                documentRepository, chunkRepository, parserFactory, documentChunker,
                embeddingService, currentUserService, auditService, aiProperties
        );
        mockUser = org.mockito.Mockito.mock(com.roottrace.user.User.class);
        org.mockito.Mockito.lenient().when(mockUser.getId()).thenReturn(UUID.randomUUID());
        org.mockito.Mockito.lenient().when(currentUserService.getCurrentUser()).thenReturn(mockUser);
    }

    @Test
    void testUploadDocument_TooLarge() {
        byte[] largeContent = new byte[2048];
        MockMultipartFile file = new MockMultipartFile("file", "test.md", "text/markdown", largeContent);
        
        assertThrows(DocumentTooLargeException.class, () -> knowledgeService.uploadDocument(file));
    }

    @Test
    void testProcessDocument_Success() {
        UUID docId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setId(docId);
        doc.setCreatedBy(userId);

        MockMultipartFile file = new MockMultipartFile("file", "test.md", "text/markdown", "content".getBytes());

        when(documentRepository.findById(docId)).thenReturn(Optional.of(doc));
        when(parserFactory.getParser(anyString(), anyString())).thenReturn(parser);
        when(parser.parse(any(InputStream.class), anyString())).thenReturn(new ParsedDocument("Title", "content", "MARKDOWN"));
        
        List<DocumentChunk> chunks = List.of(new DocumentChunk("content", "Title"));
        when(documentChunker.chunk(anyString(), anyString())).thenReturn(chunks);
        
        when(embeddingService.embedAll(any())).thenReturn(List.of(new float[]{0.1f}));
        
        KnowledgeDocumentResponse response = knowledgeService.processDocument(docId, file);
        
        assertEquals(KnowledgeDocumentStatus.READY.name(), response.status());
        assertEquals(1, response.chunkCount());
        
        verify(chunkRepository).deleteByDocumentId(docId);
        verify(chunkRepository).saveAll(any());
    }

    @Test
    void testProcessDocument_EmbeddingFailure() {
        UUID docId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setId(docId);
        doc.setCreatedBy(userId);

        MockMultipartFile file = new MockMultipartFile("file", "test.md", "text/markdown", "content".getBytes());

        when(documentRepository.findById(docId)).thenReturn(Optional.of(doc));
        when(parserFactory.getParser(anyString(), anyString())).thenReturn(parser);
        when(parser.parse(any(InputStream.class), anyString())).thenReturn(new ParsedDocument("Title", "content", "MARKDOWN"));
        
        List<DocumentChunk> chunks = List.of(new DocumentChunk("content", "Title"));
        when(documentChunker.chunk(anyString(), anyString())).thenReturn(chunks);
        
        // Simulating embedding failure
        when(embeddingService.embedAll(any())).thenThrow(new RuntimeException("API error"));
        
        assertThrows(DocumentProcessingException.class, () -> knowledgeService.processDocument(docId, file));
        
        assertEquals(KnowledgeDocumentStatus.FAILED, doc.getStatus());
        verify(documentRepository, org.mockito.Mockito.times(2)).save(doc); // state updated to FAILED
    }
}
