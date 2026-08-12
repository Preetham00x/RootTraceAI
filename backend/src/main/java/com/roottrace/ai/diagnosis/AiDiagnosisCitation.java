package com.roottrace.ai.diagnosis;

import com.roottrace.knowledge.KnowledgeChunk;
import com.roottrace.knowledge.KnowledgeDocument;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_diagnosis_citations")
public class AiDiagnosisCitation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "diagnosis_id", nullable = false)
    private AiDiagnosis diagnosis;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private KnowledgeDocument document;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chunk_id")
    private KnowledgeChunk chunk;

    @Column(name = "document_title", length = 500)
    private String documentTitle;

    @Column(name = "section_path", columnDefinition = "TEXT")
    private String sectionPath;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AiDiagnosisCitation() {
    }

    public AiDiagnosisCitation(KnowledgeDocument document, KnowledgeChunk chunk,
                               String documentTitle, String sectionPath) {
        this.document = document;
        this.chunk = chunk;
        this.documentTitle = documentTitle;
        this.sectionPath = sectionPath;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public AiDiagnosis getDiagnosis() { return diagnosis; }
    public void setDiagnosis(AiDiagnosis diagnosis) { this.diagnosis = diagnosis; }
    public KnowledgeDocument getDocument() { return document; }
    public KnowledgeChunk getChunk() { return chunk; }
    public String getDocumentTitle() { return documentTitle; }
    public String getSectionPath() { return sectionPath; }
    public Instant getCreatedAt() { return createdAt; }
}
