package com.roottrace.incident;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class IncidentRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
            .withDatabaseName("roottrace_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private IncidentRepository incidentRepository;

    @BeforeEach
    void setUp() {
        incidentRepository.deleteAll();
    }

    @Test
    @DisplayName("should persist and retrieve an incident")
    void shouldPersistAndRetrieve() {
        Incident incident = createIncident("DB timeout", "payment-service",
                IncidentSeverity.HIGH, IncidentStatus.OPEN);
        incident = incidentRepository.save(incident);

        Optional<Incident> found = incidentRepository.findByIdAndNotDeleted(incident.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getTitle()).isEqualTo("DB timeout");
        assertThat(found.get().getCreatedAt()).isNotNull();
        assertThat(found.get().getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("should not return soft-deleted incidents")
    void shouldNotReturnSoftDeleted() {
        Incident incident = createIncident("Deleted incident", "auth-service",
                IncidentSeverity.LOW, IncidentStatus.OPEN);
        incident = incidentRepository.save(incident);

        incident.setDeletedAt(Instant.now());
        incidentRepository.save(incident);

        Optional<Incident> found = incidentRepository.findByIdAndNotDeleted(incident.getId());

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("should paginate non-deleted incidents")
    void shouldPaginateNonDeleted() {
        for (int i = 0; i < 5; i++) {
            incidentRepository.save(createIncident(
                    "Incident " + i, "service-" + i,
                    IncidentSeverity.MEDIUM, IncidentStatus.OPEN));
        }
        // Add a soft-deleted one
        Incident deleted = createIncident("Deleted", "deleted-service",
                IncidentSeverity.LOW, IncidentStatus.OPEN);
        deleted = incidentRepository.save(deleted);
        deleted.setDeletedAt(Instant.now());
        incidentRepository.save(deleted);

        Page<Incident> page = incidentRepository.findAllNotDeleted(
                PageRequest.of(0, 3, Sort.by(Sort.Direction.ASC, "title")));

        assertThat(page.getTotalElements()).isEqualTo(5);
        assertThat(page.getContent()).hasSize(3);
    }

    @Test
    @DisplayName("should filter by status using specifications")
    void shouldFilterByStatus() {
        incidentRepository.save(createIncident("Open 1", "svc",
                IncidentSeverity.MEDIUM, IncidentStatus.OPEN));
        incidentRepository.save(createIncident("Open 2", "svc",
                IncidentSeverity.HIGH, IncidentStatus.OPEN));

        Incident investigating = createIncident("Investigating", "svc",
                IncidentSeverity.CRITICAL, IncidentStatus.INVESTIGATING);
        incidentRepository.save(investigating);

        Specification<Incident> spec = IncidentSpecifications.notDeleted()
                .and(IncidentSpecifications.hasStatus(IncidentStatus.OPEN));

        Page<Incident> results = incidentRepository.findAll(spec,
                PageRequest.of(0, 10));

        assertThat(results.getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("should filter by severity using specifications")
    void shouldFilterBySeverity() {
        incidentRepository.save(createIncident("Low", "svc",
                IncidentSeverity.LOW, IncidentStatus.OPEN));
        incidentRepository.save(createIncident("Critical", "svc",
                IncidentSeverity.CRITICAL, IncidentStatus.OPEN));

        Specification<Incident> spec = IncidentSpecifications.notDeleted()
                .and(IncidentSpecifications.hasSeverity(IncidentSeverity.CRITICAL));

        Page<Incident> results = incidentRepository.findAll(spec,
                PageRequest.of(0, 10));

        assertThat(results.getTotalElements()).isEqualTo(1);
        assertThat(results.getContent().get(0).getTitle()).isEqualTo("Critical");
    }

    @Test
    @DisplayName("should search by title")
    void shouldSearchByTitle() {
        incidentRepository.save(createIncident("Database connection pool exhaustion",
                "svc", IncidentSeverity.HIGH, IncidentStatus.OPEN));
        incidentRepository.save(createIncident("API gateway timeout",
                "svc", IncidentSeverity.MEDIUM, IncidentStatus.OPEN));

        Specification<Incident> spec = IncidentSpecifications.notDeleted()
                .and(IncidentSpecifications.titleContains("database"));

        Page<Incident> results = incidentRepository.findAll(spec,
                PageRequest.of(0, 10));

        assertThat(results.getTotalElements()).isEqualTo(1);
        assertThat(results.getContent().get(0).getTitle()).contains("Database");
    }

    @Test
    @DisplayName("should count by status")
    void shouldCountByStatus() {
        incidentRepository.save(createIncident("Open 1", "svc",
                IncidentSeverity.MEDIUM, IncidentStatus.OPEN));
        incidentRepository.save(createIncident("Open 2", "svc",
                IncidentSeverity.HIGH, IncidentStatus.OPEN));
        incidentRepository.save(createIncident("Investigating", "svc",
                IncidentSeverity.CRITICAL, IncidentStatus.INVESTIGATING));

        long openCount = incidentRepository.countByStatus(IncidentStatus.OPEN);

        assertThat(openCount).isEqualTo(2);
    }

    // --- Helper ---

    private Incident createIncident(String title, String service,
                                     IncidentSeverity severity, IncidentStatus status) {
        Incident incident = new Incident();
        incident.setTitle(title);
        incident.setDescription("Test description for " + title);
        incident.setService(service);
        incident.setSeverity(severity);
        incident.setStatus(status);
        incident.setEnvironment("test");
        incident.setCreatedBy("test-user");
        return incident;
    }
}
