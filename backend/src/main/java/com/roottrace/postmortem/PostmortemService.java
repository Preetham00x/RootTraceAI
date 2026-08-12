package com.roottrace.postmortem;

import com.roottrace.ai.diagnosis.AiDiagnosis;
import com.roottrace.ai.diagnosis.AiDiagnosisRepository;
import com.roottrace.common.audit.AuditService;
import com.roottrace.common.audit.AuditEventType;
import com.roottrace.common.exception.BadRequestException;
import com.roottrace.common.exception.ResourceNotFoundException;
import com.roottrace.common.security.CurrentUserService;
import com.roottrace.incident.Incident;
import com.roottrace.incident.IncidentRepository;
import com.roottrace.incident.IncidentStatus;
import com.roottrace.investigation.InvestigationPlan;
import com.roottrace.investigation.InvestigationPlanRepository;
import com.roottrace.investigation.InvestigationStep;
import com.roottrace.postmortem.dto.CreateActionItemRequest;
import com.roottrace.postmortem.dto.PostmortemActionItemResponse;
import com.roottrace.postmortem.dto.PostmortemAiResponse;
import com.roottrace.postmortem.dto.PostmortemResponse;
import com.roottrace.postmortem.dto.PostmortemTimelineEntry;
import com.roottrace.postmortem.dto.UpdateActionItemRequest;
import com.roottrace.postmortem.dto.UpdatePostmortemRequest;
import com.roottrace.user.User;
import com.roottrace.user.UserRepository;
import com.roottrace.user.dto.UserDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PostmortemService {

    private static final Logger log = LoggerFactory.getLogger(PostmortemService.class);

    private final IncidentRepository incidentRepository;
    private final AiDiagnosisRepository diagnosisRepository;
    private final InvestigationPlanRepository investigationPlanRepository;
    private final PostmortemRepository postmortemRepository;
    private final PostmortemActionItemRepository actionItemRepository;
    private final UserRepository userRepository;
    private final GeminiPostmortemService geminiPostmortemService;
    private final CurrentUserService currentUserService;
    private final AuditService auditService;

    public PostmortemService(
            IncidentRepository incidentRepository,
            AiDiagnosisRepository diagnosisRepository,
            InvestigationPlanRepository investigationPlanRepository,
            PostmortemRepository postmortemRepository,
            PostmortemActionItemRepository actionItemRepository,
            UserRepository userRepository,
            GeminiPostmortemService geminiPostmortemService,
            CurrentUserService currentUserService,
            AuditService auditService) {
        this.incidentRepository = incidentRepository;
        this.diagnosisRepository = diagnosisRepository;
        this.investigationPlanRepository = investigationPlanRepository;
        this.postmortemRepository = postmortemRepository;
        this.actionItemRepository = actionItemRepository;
        this.userRepository = userRepository;
        this.geminiPostmortemService = geminiPostmortemService;
        this.currentUserService = currentUserService;
        this.auditService = auditService;
    }

    /**
     * Generates a structured postmortem report using Spring AI Gemini.
     * Follows the transaction separation pattern: Load data -> AI call outside TX -> Persist in new TX.
     */
    public PostmortemResponse generatePostmortem(UUID incidentId) {
        User currentUser = currentUserService.getCurrentUser();

        // 1. TX1: Load incident, diagnosis, plans and build timeline
        Incident incident = loadIncident(incidentId);

        if (incident.getStatus() != IncidentStatus.RESOLVED && incident.getStatus() != IncidentStatus.CLOSED) {
            throw new BadRequestException("Incident must be RESOLVED or CLOSED to generate a postmortem. Current status: " + incident.getStatus());
        }

        if (postmortemRepository.findByIncidentId(incidentId).isPresent()) {
            throw new BadRequestException("A postmortem already exists for incident: " + incidentId);
        }

        AiDiagnosis diagnosis = loadLatestDiagnosis(incidentId);
        List<InvestigationPlan> plans = investigationPlanRepository.findByIncidentIdWithSteps(incidentId);
        List<PostmortemTimelineEntry> timeline = constructTimeline(incident, diagnosis, plans);

        // 2. OUTSIDE TX: Call Gemini
        PostmortemAiResponse aiResponse = geminiPostmortemService.generatePostmortem(incident, diagnosis, plans, timeline);

        // 3. TX2: Persist Postmortem + Action Items
        Postmortem savedPostmortem = persistGeneratedPostmortem(incident, aiResponse, timeline, currentUser);

        // 4. Audit
        auditService.record(
                AuditEventType.POSTMORTEM_GENERATED,
                "Postmortem",
                String.valueOf(savedPostmortem.getId()),
                currentUser.getEmail(),
                "Generated AI postmortem for incident " + incidentId
        );

        return mapToResponse(savedPostmortem);
    }

    /**
     * Retrieves the postmortem for an incident.
     */
    @Transactional(readOnly = true)
    public PostmortemResponse getPostmortem(UUID incidentId) {
        loadIncident(incidentId);

        Postmortem postmortem = postmortemRepository.findByIncidentIdWithActionItems(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Postmortem for incident", incidentId));

        return mapToResponse(postmortem);
    }

    /**
     * Updates an existing postmortem's fields or status.
     */
    @Transactional
    public PostmortemResponse updatePostmortem(UUID incidentId, UpdatePostmortemRequest request) {
        loadIncident(incidentId);

        Postmortem postmortem = postmortemRepository.findByIncidentIdWithActionItems(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Postmortem for incident", incidentId));

        if (request.title() != null && !request.title().isBlank()) {
            postmortem.setTitle(request.title());
        }
        if (request.summary() != null && !request.summary().isBlank()) {
            postmortem.setSummary(request.summary());
        }
        if (request.impactSummary() != null && !request.impactSummary().isBlank()) {
            postmortem.setImpactSummary(request.impactSummary());
        }
        if (request.rootCauseAnalysis() != null && !request.rootCauseAnalysis().isBlank()) {
            postmortem.setRootCauseAnalysis(request.rootCauseAnalysis());
        }
        if (request.resolutionSummary() != null && !request.resolutionSummary().isBlank()) {
            postmortem.setResolutionSummary(request.resolutionSummary());
        }
        if (request.timeline() != null) {
            postmortem.setTimeline(request.timeline());
        }
        if (request.lessonsLearned() != null) {
            postmortem.setLessonsLearned(request.lessonsLearned());
        }

        boolean statusChangedToPublished = false;
        if (request.status() != null) {
            if (!postmortem.getStatus().canTransitionTo(request.status())) {
                throw new BadRequestException("Cannot transition postmortem status from " + postmortem.getStatus() + " to " + request.status());
            }
            if (request.status() == PostmortemStatus.PUBLISHED && postmortem.getStatus() != PostmortemStatus.PUBLISHED) {
                postmortem.setPublishedAt(Instant.now());
                statusChangedToPublished = true;
            } else if (request.status() != PostmortemStatus.PUBLISHED && postmortem.getStatus() == PostmortemStatus.PUBLISHED) {
                postmortem.setPublishedAt(null);
            }
            postmortem.setStatus(request.status());
        }

        Postmortem savedPostmortem = postmortemRepository.save(postmortem);
        User currentUser = currentUserService.getCurrentUser();

        if (statusChangedToPublished) {
            auditService.record(
                    AuditEventType.POSTMORTEM_PUBLISHED,
                    "Postmortem",
                    String.valueOf(savedPostmortem.getId()),
                    currentUser.getEmail(),
                    "Published postmortem for incident " + incidentId
            );
        } else {
            auditService.record(
                    AuditEventType.POSTMORTEM_UPDATED,
                    "Postmortem",
                    String.valueOf(savedPostmortem.getId()),
                    currentUser.getEmail(),
                    "Updated postmortem for incident " + incidentId
            );
        }

        return mapToResponse(savedPostmortem);
    }

    /**
     * Exports the postmortem formatted as standard Google SRE-style Markdown.
     */
    @Transactional(readOnly = true)
    public String exportMarkdown(UUID incidentId) {
        Incident incident = loadIncident(incidentId);

        Postmortem postmortem = postmortemRepository.findByIncidentIdWithActionItems(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Postmortem for incident", incidentId));

        StringBuilder md = new StringBuilder();
        md.append("# Postmortem: ").append(postmortem.getTitle()).append("\n\n");
        md.append("**Incident ID:** `").append(incident.getId()).append("`  \n");
        md.append("**Service:** `").append(incident.getService()).append("`  \n");
        md.append("**Severity:** `").append(incident.getSeverity()).append("`  \n");
        md.append("**Status:** `").append(postmortem.getStatus()).append("`  \n");
        if (postmortem.getDowntimeMinutes() != null) {
            md.append("**Calculated Downtime:** ").append(postmortem.getDowntimeMinutes()).append(" minutes  \n");
        }
        md.append("**Created:** ").append(incident.getCreatedAt()).append(" | **Resolved:** ")
                .append(incident.getResolvedAt() != null ? incident.getResolvedAt() : "N/A").append("  \n\n");
        md.append("---\n\n");

        md.append("## Executive Summary\n\n");
        md.append(postmortem.getSummary()).append("\n\n");

        md.append("## Impact Assessment\n\n");
        md.append(postmortem.getImpactSummary()).append("\n\n");

        md.append("## Root Cause Analysis\n\n");
        md.append(postmortem.getRootCauseAnalysis()).append("\n\n");

        md.append("## Resolution & Recovery\n\n");
        md.append(postmortem.getResolutionSummary()).append("\n\n");

        md.append("## Chronological Timeline\n\n");
        if (postmortem.getTimeline() != null && !postmortem.getTimeline().isEmpty()) {
            for (PostmortemTimelineEntry entry : postmortem.getTimeline()) {
                md.append("- **").append(entry.timestamp()).append("** (").append(entry.source()).append("): ")
                        .append(entry.description()).append("\n");
            }
        } else {
            md.append("_No timeline events recorded._\n");
        }
        md.append("\n");

        md.append("## Lessons Learned\n\n");
        if (postmortem.getLessonsLearned() != null && !postmortem.getLessonsLearned().isEmpty()) {
            for (String lesson : postmortem.getLessonsLearned()) {
                md.append("- ").append(lesson).append("\n");
            }
        } else {
            md.append("_No lessons learned recorded._\n");
        }
        md.append("\n");

        md.append("## Preventive Action Items\n\n");
        if (postmortem.getActionItems() != null && !postmortem.getActionItems().isEmpty()) {
            md.append("| Action | Category | Priority | Status | Assignee | Due Date |\n");
            md.append("|---|---|---|---|---|---|\n");
            for (PostmortemActionItem item : postmortem.getActionItems()) {
                String assignee = item.getAssignedTo() != null ? item.getAssignedTo().getEmail() : "Unassigned";
                String dueDate = item.getDueDate() != null ? item.getDueDate().toString() : "N/A";
                md.append("| ").append(item.getTitle()).append(": ").append(item.getDescription())
                        .append(" | ").append(item.getCategory())
                        .append(" | ").append(item.getPriority())
                        .append(" | ").append(item.getStatus())
                        .append(" | ").append(assignee)
                        .append(" | ").append(dueDate).append(" |\n");
            }
        } else {
            md.append("_No action items recorded._\n");
        }

        return md.toString();
    }

    /**
     * Creates an action item for a postmortem.
     */
    @Transactional
    public PostmortemActionItemResponse createActionItem(UUID incidentId, CreateActionItemRequest request) {
        loadIncident(incidentId);

        Postmortem postmortem = postmortemRepository.findByIncidentIdWithActionItems(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Postmortem for incident", incidentId));

        User assignedTo = null;
        if (request.assignedToId() != null) {
            assignedTo = userRepository.findById(request.assignedToId())
                    .orElseThrow(() -> new ResourceNotFoundException("User", request.assignedToId()));
        }

        PostmortemActionItem item = new PostmortemActionItem(
                postmortem,
                request.title(),
                request.description(),
                request.category(),
                request.priority(),
                assignedTo,
                request.dueDate()
        );

        postmortem.addActionItem(item);
        PostmortemActionItem savedItem = actionItemRepository.save(item);

        User currentUser = currentUserService.getCurrentUser();
        auditService.record(
                AuditEventType.POSTMORTEM_ACTION_ITEM_CREATED,
                "PostmortemActionItem",
                String.valueOf(savedItem.getId()),
                currentUser.getEmail(),
                "Created action item: " + savedItem.getTitle()
        );

        return mapActionItemToResponse(savedItem);
    }

    /**
     * Updates an action item's status, assignment, category, or priority.
     */
    @Transactional
    public PostmortemActionItemResponse updateActionItem(UUID incidentId, UUID actionItemId, UpdateActionItemRequest request) {
        loadIncident(incidentId);

        Postmortem postmortem = postmortemRepository.findByIncidentIdWithActionItems(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Postmortem for incident", incidentId));

        PostmortemActionItem item = actionItemRepository.findByIdAndPostmortemId(actionItemId, postmortem.getId())
                .orElseThrow(() -> new ResourceNotFoundException("PostmortemActionItem", actionItemId));

        if (request.title() != null && !request.title().isBlank()) {
            item.setTitle(request.title());
        }
        if (request.description() != null && !request.description().isBlank()) {
            item.setDescription(request.description());
        }
        if (request.category() != null) {
            item.setCategory(request.category());
        }
        if (request.priority() != null) {
            item.setPriority(request.priority());
        }
        if (request.dueDate() != null) {
            item.setDueDate(request.dueDate());
        }
        if (request.assignedToId() != null) {
            User assignedTo = userRepository.findById(request.assignedToId())
                    .orElseThrow(() -> new ResourceNotFoundException("User", request.assignedToId()));
            item.setAssignedTo(assignedTo);
        }

        if (request.status() != null) {
            if (!item.getStatus().canTransitionTo(request.status())) {
                throw new BadRequestException("Cannot transition action item status from " + item.getStatus() + " to " + request.status());
            }
            if (request.status() == ActionItemStatus.COMPLETED && item.getStatus() != ActionItemStatus.COMPLETED) {
                item.setCompletedAt(Instant.now());
            } else if (request.status() != ActionItemStatus.COMPLETED && item.getStatus() == ActionItemStatus.COMPLETED) {
                item.setCompletedAt(null);
            }
            item.setStatus(request.status());
        }

        PostmortemActionItem savedItem = actionItemRepository.save(item);
        User currentUser = currentUserService.getCurrentUser();

        auditService.record(
                AuditEventType.POSTMORTEM_ACTION_ITEM_UPDATED,
                "PostmortemActionItem",
                String.valueOf(savedItem.getId()),
                currentUser.getEmail(),
                "Updated action item: " + savedItem.getTitle()
        );

        return mapActionItemToResponse(savedItem);
    }

    // --- Helper Transactional Methods ---

    @Transactional(readOnly = true)
    protected Incident loadIncident(UUID incidentId) {
        return incidentRepository.findById(incidentId)
                .filter(inc -> !inc.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Incident", incidentId));
    }

    @Transactional(readOnly = true)
    protected AiDiagnosis loadLatestDiagnosis(UUID incidentId) {
        List<AiDiagnosis> diagnoses = diagnosisRepository.findByIncidentIdOrderByCreatedAtDesc(incidentId);
        return diagnoses.isEmpty() ? null : diagnoses.get(0);
    }

    @Transactional
    protected Postmortem persistGeneratedPostmortem(
            Incident incident,
            PostmortemAiResponse aiResponse,
            List<PostmortemTimelineEntry> timeline,
            User currentUser) {

        Instant resolvedAt = incident.getResolvedAt() != null ? incident.getResolvedAt() : incident.getUpdatedAt();
        Long downtimeMinutes = null;
        if (incident.getCreatedAt() != null && resolvedAt != null) {
            downtimeMinutes = Duration.between(incident.getCreatedAt(), resolvedAt).toMinutes();
        }

        String title = (aiResponse.title() != null && !aiResponse.title().isBlank())
                ? aiResponse.title()
                : "Postmortem: " + incident.getTitle();

        Postmortem postmortem = new Postmortem(
                incident,
                title,
                aiResponse.summary(),
                aiResponse.impactSummary() != null ? aiResponse.impactSummary() : "N/A",
                aiResponse.rootCauseAnalysis(),
                aiResponse.resolutionSummary() != null ? aiResponse.resolutionSummary() : (incident.getResolution() != null ? incident.getResolution() : "Resolution applied"),
                timeline,
                aiResponse.lessonsLearned(),
                downtimeMinutes,
                currentUser
        );

        if (aiResponse.actionItems() != null) {
            for (PostmortemAiResponse.ProposedActionItem proposed : aiResponse.actionItems()) {
                ActionItemCategory category = ActionItemCategory.PREVENT;
                if (proposed.category() != null) {
                    try {
                        category = ActionItemCategory.valueOf(proposed.category().toUpperCase());
                    } catch (IllegalArgumentException ignored) {
                    }
                }

                ActionItemPriority priority = ActionItemPriority.MEDIUM;
                if (proposed.priority() != null) {
                    try {
                        priority = ActionItemPriority.valueOf(proposed.priority().toUpperCase());
                    } catch (IllegalArgumentException ignored) {
                    }
                }

                PostmortemActionItem item = new PostmortemActionItem(
                        postmortem,
                        proposed.title(),
                        proposed.description(),
                        category,
                        priority,
                        null,
                        null
                );
                postmortem.addActionItem(item);
            }
        }

        return postmortemRepository.save(postmortem);
    }

    // --- Timeline Builder Helper ---

    private List<PostmortemTimelineEntry> constructTimeline(
            Incident incident,
            AiDiagnosis diagnosis,
            List<InvestigationPlan> plans) {

        List<PostmortemTimelineEntry> timeline = new ArrayList<>();

        if (incident.getCreatedAt() != null) {
            timeline.add(new PostmortemTimelineEntry(
                    incident.getCreatedAt(),
                    "Incident detected and created: " + incident.getTitle(),
                    "INCIDENT_CREATION"
            ));
        }

        if (diagnosis != null && diagnosis.getCreatedAt() != null) {
            timeline.add(new PostmortemTimelineEntry(
                    diagnosis.getCreatedAt(),
                    "AI Diagnosis generated. Probable cause: " + diagnosis.getProbableRootCause(),
                    "AI_DIAGNOSIS"
            ));
        }

        if (plans != null) {
            for (InvestigationPlan plan : plans) {
                if (plan.getSteps() != null) {
                    for (InvestigationStep step : plan.getSteps()) {
                        Instant timestamp = step.getCompletedAt() != null ? step.getCompletedAt() : step.getCreatedAt();
                        timeline.add(new PostmortemTimelineEntry(
                                timestamp,
                                "Investigation Step [" + step.getStatus() + "]: " + step.getTitle(),
                                "INVESTIGATION"
                        ));
                    }
                }
            }
        }

        Instant resolvedAt = incident.getResolvedAt() != null ? incident.getResolvedAt() : incident.getUpdatedAt();
        if (resolvedAt != null && (incident.getStatus() == IncidentStatus.RESOLVED || incident.getStatus() == IncidentStatus.CLOSED)) {
            String resolution = incident.getResolution() != null ? incident.getResolution() : "Incident marked as resolved.";
            timeline.add(new PostmortemTimelineEntry(
                    resolvedAt,
                    "Incident resolved: " + resolution,
                    "RESOLUTION"
            ));
        }

        timeline.sort(Comparator.comparing(PostmortemTimelineEntry::timestamp));
        return timeline;
    }

    // --- Mapping Helpers ---

    private PostmortemResponse mapToResponse(Postmortem postmortem) {
        List<PostmortemActionItemResponse> actionItemResponses = postmortem.getActionItems() != null
                ? postmortem.getActionItems().stream().map(this::mapActionItemToResponse).collect(Collectors.toList())
                : new ArrayList<>();

        return new PostmortemResponse(
                postmortem.getId(),
                postmortem.getIncident().getId(),
                postmortem.getTitle(),
                postmortem.getSummary(),
                postmortem.getImpactSummary(),
                postmortem.getRootCauseAnalysis(),
                postmortem.getResolutionSummary(),
                postmortem.getTimeline(),
                postmortem.getLessonsLearned(),
                postmortem.getStatus(),
                postmortem.getDowntimeMinutes(),
                mapUser(postmortem.getCreatedBy()),
                postmortem.getPublishedAt(),
                actionItemResponses,
                postmortem.getCreatedAt(),
                postmortem.getUpdatedAt()
        );
    }

    private PostmortemActionItemResponse mapActionItemToResponse(PostmortemActionItem item) {
        return new PostmortemActionItemResponse(
                item.getId(),
                item.getPostmortem().getId(),
                item.getTitle(),
                item.getDescription(),
                item.getCategory(),
                item.getPriority(),
                item.getStatus(),
                mapUser(item.getAssignedTo()),
                item.getDueDate(),
                item.getCompletedAt(),
                item.getCreatedAt(),
                item.getUpdatedAt()
        );
    }

    private UserDto mapUser(User user) {
        if (user == null) {
            return null;
        }
        return new UserDto(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getRole().name()
        );
    }
}
