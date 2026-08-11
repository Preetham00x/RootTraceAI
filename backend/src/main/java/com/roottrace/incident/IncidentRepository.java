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
}
