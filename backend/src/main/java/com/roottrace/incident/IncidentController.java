package com.roottrace.incident;

import com.roottrace.ai.diagnosis.SimilarIncidentService;
import com.roottrace.incident.dto.CreateIncidentRequest;
import com.roottrace.incident.dto.IncidentResponse;
import com.roottrace.incident.dto.IncidentSummaryResponse;
import com.roottrace.incident.dto.ResolveIncidentRequest;
import com.roottrace.incident.dto.SimilarIncidentResponse;
import com.roottrace.incident.dto.UpdateIncidentRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/incidents")
@Tag(name = "Incidents", description = "Incident management operations")
public class IncidentController {

    private final IncidentService incidentService;
    private final SimilarIncidentService similarIncidentService;

    public IncidentController(IncidentService incidentService,
                              SimilarIncidentService similarIncidentService) {
        this.incidentService = incidentService;
        this.similarIncidentService = similarIncidentService;
    }

    @PostMapping
    @Operation(summary = "Create a new incident")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENGINEER')")
    public ResponseEntity<IncidentResponse> create(@Valid @RequestBody CreateIncidentRequest request) {
        IncidentResponse response = incidentService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an incident by ID")
    public ResponseEntity<IncidentResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(incidentService.getById(id));
    }

    @GetMapping
    @Operation(summary = "List incidents with filtering, pagination, and sorting")
    public ResponseEntity<Page<IncidentSummaryResponse>> list(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable,
            @RequestParam(required = false) IncidentStatus status,
            @RequestParam(required = false) IncidentSeverity severity,
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String environment,
            @RequestParam(required = false) String createdBy,
            @RequestParam(required = false) String search) {
        Page<IncidentSummaryResponse> page = incidentService.list(
                pageable, status, severity, service, environment, createdBy, search);
        return ResponseEntity.ok(page);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an incident")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENGINEER')")
    public ResponseEntity<IncidentResponse> update(@PathVariable UUID id,
                                                    @Valid @RequestBody UpdateIncidentRequest request) {
        return ResponseEntity.ok(incidentService.update(id, request));
    }

    @PatchMapping("/{id}/resolve")
    @Operation(summary = "Resolve an incident with a resolution description")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENGINEER')")
    public ResponseEntity<IncidentResponse> resolve(@PathVariable UUID id,
                                                     @Valid @RequestBody ResolveIncidentRequest request) {
        return ResponseEntity.ok(incidentService.resolve(id, request.resolution()));
    }

    @PatchMapping("/{id}/close")
    @Operation(summary = "Close a resolved incident")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENGINEER')")
    public ResponseEntity<IncidentResponse> close(@PathVariable UUID id) {
        return ResponseEntity.ok(incidentService.close(id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft-delete an incident")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENGINEER')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        incidentService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/similar")
    @Operation(summary = "Find similar incidents using AI vector search")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENGINEER', 'VIEWER')")
    public ResponseEntity<java.util.List<SimilarIncidentResponse>> getSimilar(
            @PathVariable UUID id,
            @RequestParam(required = false, defaultValue = "5") Integer limit) {
        return ResponseEntity.ok(similarIncidentService.findSimilar(id, limit));
    }
}
