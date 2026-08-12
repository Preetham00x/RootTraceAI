package com.roottrace.incident;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface IncidentRepository extends JpaRepository<Incident, UUID>,
        JpaSpecificationExecutor<Incident> {

    @Query("SELECT i FROM Incident i WHERE i.id = :id AND i.deletedAt IS NULL")
    Optional<Incident> findByIdAndNotDeleted(@Param("id") UUID id);

    @Query("SELECT i FROM Incident i WHERE i.deletedAt IS NULL")
    Page<Incident> findAllNotDeleted(Pageable pageable);

    @Query("SELECT COUNT(i) FROM Incident i WHERE i.deletedAt IS NULL AND i.status = :status")
    long countByStatus(@Param("status") IncidentStatus status);

    /**
     * Semantic vector search using cosine similarity (pgvector) on incident embeddings.
     * Excludes deleted incidents and the source incident itself.
     * Returns: id, title, service, severity, status, environment, created_at, resolved_at, resolution, similarity_score
     */
    @Query(value = """
            SELECT i.id          AS id,
                   i.title       AS title,
                   i.service     AS service,
                   i.severity    AS severity,
                   i.status      AS status,
                   i.environment AS environment,
                   i.created_at  AS created_at,
                   i.resolved_at AS resolved_at,
                   i.resolution  AS resolution,
                   (1.0 - (i.embedding <=> CAST(:queryEmbedding AS vector))) AS similarity
            FROM incidents i
            WHERE i.deleted_at IS NULL
              AND i.id != :excludeId
              AND i.embedding IS NOT NULL
            ORDER BY i.embedding <=> CAST(:queryEmbedding AS vector)
            LIMIT :limit
            """, nativeQuery = true)
    java.util.List<Object[]> findSimilarIncidents(
            @Param("excludeId") UUID excludeId,
            @Param("queryEmbedding") String queryEmbedding,
            @Param("limit") int limit
    );
}
