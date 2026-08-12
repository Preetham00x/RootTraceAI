package com.roottrace.incident;

import com.roottrace.common.audit.AuditEventType;
import com.roottrace.common.audit.AuditService;
import com.roottrace.common.exception.BadRequestException;
import com.roottrace.common.exception.ResourceNotFoundException;
import com.roottrace.incident.dto.CreateIncidentRequest;
import com.roottrace.incident.dto.IncidentResponse;
import com.roottrace.incident.dto.IncidentSummaryResponse;
import com.roottrace.incident.dto.UpdateIncidentRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class IncidentService {

    private static final Logger log = LoggerFactory.getLogger(IncidentService.class);
    private static final String ENTITY_TYPE = "Incident";

    private final IncidentRepository incidentRepository;
    private final AuditService auditService;
    private final com.roottrace.common.security.CurrentUserService currentUserService;

    public IncidentService(IncidentRepository incidentRepository, AuditService auditService, com.roottrace.common.security.CurrentUserService currentUserService) {
        this.incidentRepository = incidentRepository;
        this.auditService = auditService;
        this.currentUserService = currentUserService;
    }

    @Transactional
    public IncidentResponse create(CreateIncidentRequest request) {
        Incident incident = IncidentMapper.toEntity(request);
        incident.setCreatedBy(currentUserService.getCurrentUser());
        incident = incidentRepository.save(incident);

        auditService.record(
                AuditEventType.INCIDENT_CREATED,
                ENTITY_TYPE,
                incident.getId().toString(),
                currentUserService.getCurrentUser().getEmail(),
                String.format("Incident created: %s [%s]", request.title(), request.severity())
        );

        log.info("Incident created: id={}, title={}", incident.getId(), incident.getTitle());
        return IncidentMapper.toResponse(incident);
    }

    public IncidentResponse getById(UUID id) {
        Incident incident = findActiveOrThrow(id);
        return IncidentMapper.toResponse(incident);
    }

    public Page<IncidentSummaryResponse> list(Pageable pageable,
                                               IncidentStatus status,
                                               IncidentSeverity severity,
                                               String service,
                                               String environment,
                                               String createdBy,
                                               String search) {
        Specification<Incident> spec = buildSpecification(status, severity, service,
                environment, createdBy, search);
        return incidentRepository.findAll(spec, pageable)
                .map(IncidentMapper::toSummary);
    }

    @Transactional
    public IncidentResponse update(UUID id, UpdateIncidentRequest request) {
        Incident incident = findActiveOrThrow(id);

        if (request.title() != null && !request.title().isBlank()) {
            incident.setTitle(request.title());
        }
        if (request.description() != null) {
            incident.setDescription(request.description());
        }
        if (request.service() != null && !request.service().isBlank()) {
            incident.setService(request.service());
        }
        if (request.severity() != null) {
            incident.setSeverity(request.severity());
        }
        if (request.status() != null) {
            validateStatusTransition(incident.getStatus(), request.status());
            incident.setStatus(request.status());
        }
        if (request.environment() != null) {
            incident.setEnvironment(request.environment());
        }

        incident = incidentRepository.save(incident);

        auditService.record(
                AuditEventType.INCIDENT_UPDATED,
                ENTITY_TYPE,
                id.toString(),
                currentUserService.getCurrentUser().getEmail(),
                "Incident updated"
        );

        log.info("Incident updated: id={}", id);
        return IncidentMapper.toResponse(incident);
    }

    @Transactional
    public IncidentResponse resolve(UUID id, String resolution) {
        Incident incident = findActiveOrThrow(id);

        if (incident.getStatus() == IncidentStatus.CLOSED) {
            throw new BadRequestException("Cannot resolve a closed incident");
        }

        incident.setStatus(IncidentStatus.RESOLVED);
        incident.setResolution(resolution);
        incident.setResolvedAt(Instant.now());
        incident = incidentRepository.save(incident);

        auditService.record(
                AuditEventType.INCIDENT_RESOLVED,
                ENTITY_TYPE,
                id.toString(),
                currentUserService.getCurrentUser().getEmail(),
                "Incident resolved: " + truncate(resolution, 200)
        );

        log.info("Incident resolved: id={}", id);
        return IncidentMapper.toResponse(incident);
    }

    @Transactional
    public IncidentResponse close(UUID id) {
        Incident incident = findActiveOrThrow(id);

        if (incident.getStatus() != IncidentStatus.RESOLVED) {
            throw new BadRequestException("Only resolved incidents can be closed");
        }

        incident.setStatus(IncidentStatus.CLOSED);
        incident = incidentRepository.save(incident);

        auditService.record(
                AuditEventType.INCIDENT_CLOSED,
                ENTITY_TYPE,
                id.toString(),
                currentUserService.getCurrentUser().getEmail(),
                "Incident closed"
        );

        log.info("Incident closed: id={}", id);
        return IncidentMapper.toResponse(incident);
    }

    @Transactional
    public void delete(UUID id) {
        Incident incident = findActiveOrThrow(id);
        incident.setDeletedAt(Instant.now());
        incidentRepository.save(incident);

        auditService.record(
                AuditEventType.INCIDENT_DELETED,
                ENTITY_TYPE,
                id.toString(),
                currentUserService.getCurrentUser().getEmail(),
                "Incident soft-deleted"
        );

        log.info("Incident soft-deleted: id={}", id);
    }

    // --- Private helpers ---

    private Incident findActiveOrThrow(UUID id) {
        return incidentRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException(ENTITY_TYPE, id));
    }

    private void validateStatusTransition(IncidentStatus current, IncidentStatus target) {
        switch (current) {
            case CLOSED -> throw new BadRequestException("Cannot change status of a closed incident");
            case RESOLVED -> {
                if (target == IncidentStatus.OPEN) {
                    throw new BadRequestException(
                            "Cannot reopen a resolved incident directly; close or re-investigate");
                }
            }
            case OPEN, INVESTIGATING -> { /* any transition allowed */ }
        }
    }

    private Specification<Incident> buildSpecification(IncidentStatus status,
                                                        IncidentSeverity severity,
                                                        String service,
                                                        String environment,
                                                        String createdBy,
                                                        String search) {
        Specification<Incident> spec = IncidentSpecifications.notDeleted();

        if (status != null) {
            spec = spec.and(IncidentSpecifications.hasStatus(status));
        }
        if (severity != null) {
            spec = spec.and(IncidentSpecifications.hasSeverity(severity));
        }
        if (service != null && !service.isBlank()) {
            spec = spec.and(IncidentSpecifications.hasService(service));
        }
        if (environment != null && !environment.isBlank()) {
            spec = spec.and(IncidentSpecifications.hasEnvironment(environment));
        }
        if (createdBy != null && !createdBy.isBlank()) {
            spec = spec.and(IncidentSpecifications.hasCreatedBy(createdBy));
        }
        if (search != null && !search.isBlank()) {
            spec = spec.and(IncidentSpecifications.titleContains(search));
        }

        return spec;
    }

    private static String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}
