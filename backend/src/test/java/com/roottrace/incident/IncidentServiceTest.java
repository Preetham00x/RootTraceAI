package com.roottrace.incident;

import com.roottrace.common.audit.AuditEventType;
import com.roottrace.common.audit.AuditService;
import com.roottrace.common.exception.BadRequestException;
import com.roottrace.common.exception.ResourceNotFoundException;
import com.roottrace.incident.dto.CreateIncidentRequest;
import com.roottrace.incident.dto.IncidentResponse;
import com.roottrace.incident.dto.UpdateIncidentRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IncidentServiceTest {

    @Mock
    private IncidentRepository incidentRepository;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private IncidentService incidentService;

    private CreateIncidentRequest validCreateRequest;

    @BeforeEach
    void setUp() {
        validCreateRequest = new CreateIncidentRequest(
                "Database connection timeout",
                "Users experiencing 503 errors due to connection pool exhaustion",
                "payment-service",
                IncidentSeverity.HIGH,
                "production",
                "engineer@example.com"
        );
    }

    @Nested
    @DisplayName("Create Incident")
    class CreateTests {

        @Test
        @DisplayName("should create incident with OPEN status")
        void shouldCreateIncident() {
            Incident saved = createIncidentEntity(validCreateRequest);
            when(incidentRepository.save(any(Incident.class))).thenReturn(saved);

            IncidentResponse response = incidentService.create(validCreateRequest);

            assertThat(response.title()).isEqualTo("Database connection timeout");
            assertThat(response.status()).isEqualTo(IncidentStatus.OPEN);
            assertThat(response.severity()).isEqualTo(IncidentSeverity.HIGH);
            assertThat(response.service()).isEqualTo("payment-service");

            verify(auditService).record(
                    eq(AuditEventType.INCIDENT_CREATED),
                    eq("Incident"),
                    anyString(),
                    eq("engineer@example.com"),
                    anyString()
            );
        }
    }

    @Nested
    @DisplayName("Get Incident")
    class GetTests {

        @Test
        @DisplayName("should return incident when found")
        void shouldReturnIncident() {
            UUID id = UUID.randomUUID();
            Incident incident = createIncidentEntity(validCreateRequest);
            when(incidentRepository.findByIdAndNotDeleted(id)).thenReturn(Optional.of(incident));

            IncidentResponse response = incidentService.getById(id);

            assertThat(response.title()).isEqualTo("Database connection timeout");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when not found")
        void shouldThrowWhenNotFound() {
            UUID id = UUID.randomUUID();
            when(incidentRepository.findByIdAndNotDeleted(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> incidentService.getById(id))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(id.toString());
        }
    }

    @Nested
    @DisplayName("Update Incident")
    class UpdateTests {

        @Test
        @DisplayName("should update only provided fields")
        void shouldUpdatePartially() {
            UUID id = UUID.randomUUID();
            Incident incident = createIncidentEntity(validCreateRequest);
            when(incidentRepository.findByIdAndNotDeleted(id)).thenReturn(Optional.of(incident));
            when(incidentRepository.save(any(Incident.class))).thenReturn(incident);

            UpdateIncidentRequest updateRequest = new UpdateIncidentRequest(
                    "Updated title", null, null, IncidentSeverity.CRITICAL, null, null);

            IncidentResponse response = incidentService.update(id, updateRequest);

            assertThat(incident.getTitle()).isEqualTo("Updated title");
            assertThat(incident.getSeverity()).isEqualTo(IncidentSeverity.CRITICAL);
            // Description unchanged
            assertThat(incident.getDescription()).isEqualTo(validCreateRequest.description());

            verify(auditService).record(
                    eq(AuditEventType.INCIDENT_UPDATED), anyString(), anyString(), anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("Resolve Incident")
    class ResolveTests {

        @Test
        @DisplayName("should resolve an open incident")
        void shouldResolveOpenIncident() {
            UUID id = UUID.randomUUID();
            Incident incident = createIncidentEntity(validCreateRequest);
            when(incidentRepository.findByIdAndNotDeleted(id)).thenReturn(Optional.of(incident));
            when(incidentRepository.save(any(Incident.class))).thenReturn(incident);

            IncidentResponse response = incidentService.resolve(id, "Fixed connection pool settings");

            assertThat(incident.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
            assertThat(incident.getResolution()).isEqualTo("Fixed connection pool settings");
            assertThat(incident.getResolvedAt()).isNotNull();

            verify(auditService).record(
                    eq(AuditEventType.INCIDENT_RESOLVED), anyString(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("should reject resolving a closed incident")
        void shouldRejectResolvingClosedIncident() {
            UUID id = UUID.randomUUID();
            Incident incident = createIncidentEntity(validCreateRequest);
            incident.setStatus(IncidentStatus.CLOSED);
            when(incidentRepository.findByIdAndNotDeleted(id)).thenReturn(Optional.of(incident));

            assertThatThrownBy(() -> incidentService.resolve(id, "Fix"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("closed");
        }
    }

    @Nested
    @DisplayName("Close Incident")
    class CloseTests {

        @Test
        @DisplayName("should close a resolved incident")
        void shouldCloseResolvedIncident() {
            UUID id = UUID.randomUUID();
            Incident incident = createIncidentEntity(validCreateRequest);
            incident.setStatus(IncidentStatus.RESOLVED);
            when(incidentRepository.findByIdAndNotDeleted(id)).thenReturn(Optional.of(incident));
            when(incidentRepository.save(any(Incident.class))).thenReturn(incident);

            incidentService.close(id);

            assertThat(incident.getStatus()).isEqualTo(IncidentStatus.CLOSED);
            verify(auditService).record(
                    eq(AuditEventType.INCIDENT_CLOSED), anyString(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("should reject closing a non-resolved incident")
        void shouldRejectClosingOpenIncident() {
            UUID id = UUID.randomUUID();
            Incident incident = createIncidentEntity(validCreateRequest);
            // Status is OPEN
            when(incidentRepository.findByIdAndNotDeleted(id)).thenReturn(Optional.of(incident));

            assertThatThrownBy(() -> incidentService.close(id))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("resolved");
        }
    }

    @Nested
    @DisplayName("Delete Incident")
    class DeleteTests {

        @Test
        @DisplayName("should soft-delete an incident")
        void shouldSoftDelete() {
            UUID id = UUID.randomUUID();
            Incident incident = createIncidentEntity(validCreateRequest);
            when(incidentRepository.findByIdAndNotDeleted(id)).thenReturn(Optional.of(incident));
            when(incidentRepository.save(any(Incident.class))).thenReturn(incident);

            incidentService.delete(id);

            assertThat(incident.getDeletedAt()).isNotNull();
            assertThat(incident.isDeleted()).isTrue();

            verify(auditService).record(
                    eq(AuditEventType.INCIDENT_DELETED), anyString(), anyString(), anyString(), anyString());
        }
    }

    // --- Helpers ---

    private Incident createIncidentEntity(CreateIncidentRequest request) {
        Incident incident = new Incident();
        incident.setTitle(request.title());
        incident.setDescription(request.description());
        incident.setService(request.service());
        incident.setSeverity(request.severity());
        incident.setStatus(IncidentStatus.OPEN);
        incident.setEnvironment(request.environment());
        incident.setCreatedBy(request.createdBy());
        // Simulate JPA lifecycle: set ID (normally done by DB) and timestamps (@PrePersist)
        try {
            var idField = Incident.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(incident, UUID.randomUUID());

            var method = Incident.class.getDeclaredMethod("onCreate");
            method.setAccessible(true);
            method.invoke(incident);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return incident;
    }
}
