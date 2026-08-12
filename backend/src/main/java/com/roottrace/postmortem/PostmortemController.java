package com.roottrace.postmortem;

import com.roottrace.postmortem.dto.CreateActionItemRequest;
import com.roottrace.postmortem.dto.PostmortemActionItemResponse;
import com.roottrace.postmortem.dto.PostmortemResponse;
import com.roottrace.postmortem.dto.UpdateActionItemRequest;
import com.roottrace.postmortem.dto.UpdatePostmortemRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/incidents/{incidentId}/postmortem")
@Tag(name = "Postmortems", description = "AI-assisted SRE postmortem generation, tracking, and action items")
public class PostmortemController {

    private final PostmortemService postmortemService;

    public PostmortemController(PostmortemService postmortemService) {
        this.postmortemService = postmortemService;
    }

    @PostMapping("/generate")
    @Operation(summary = "Generate an AI-assisted postmortem report for a resolved incident")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENGINEER')")
    public ResponseEntity<PostmortemResponse> generatePostmortem(@PathVariable UUID incidentId) {
        PostmortemResponse response = postmortemService.generatePostmortem(incidentId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "Get the postmortem report for an incident")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENGINEER', 'VIEWER')")
    public ResponseEntity<PostmortemResponse> getPostmortem(@PathVariable UUID incidentId) {
        return ResponseEntity.ok(postmortemService.getPostmortem(incidentId));
    }

    @PatchMapping
    @Operation(summary = "Update postmortem contents or lifecycle status (DRAFT -> IN_REVIEW -> PUBLISHED)")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENGINEER')")
    public ResponseEntity<PostmortemResponse> updatePostmortem(
            @PathVariable UUID incidentId,
            @RequestBody UpdatePostmortemRequest request) {
        return ResponseEntity.ok(postmortemService.updatePostmortem(incidentId, request));
    }

    @GetMapping(value = "/export", produces = "text/markdown;charset=UTF-8")
    @Operation(summary = "Export postmortem as standard Google SRE-style Markdown")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENGINEER', 'VIEWER')")
    public ResponseEntity<String> exportMarkdown(@PathVariable UUID incidentId) {
        String markdown = postmortemService.exportMarkdown(incidentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/markdown;charset=UTF-8"))
                .body(markdown);
    }

    @PostMapping("/action-items")
    @Operation(summary = "Add a preventive action item to the postmortem")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENGINEER')")
    public ResponseEntity<PostmortemActionItemResponse> createActionItem(
            @PathVariable UUID incidentId,
            @Valid @RequestBody CreateActionItemRequest request) {
        PostmortemActionItemResponse response = postmortemService.createActionItem(incidentId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/action-items/{actionItemId}")
    @Operation(summary = "Update a postmortem action item status, priority, category, or assignment")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENGINEER')")
    public ResponseEntity<PostmortemActionItemResponse> updateActionItem(
            @PathVariable UUID incidentId,
            @PathVariable UUID actionItemId,
            @RequestBody UpdateActionItemRequest request) {
        return ResponseEntity.ok(postmortemService.updateActionItem(incidentId, actionItemId, request));
    }
}
