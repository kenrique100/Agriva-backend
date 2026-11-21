package com.agriva.task.controller;

import com.agriva.shared.models.dto.PageResponse;
import com.agriva.shared.exception.PersonnelAccessDeniedException;
import com.agriva.shared.security.AuthenticatedUser;
import com.agriva.personnel.service.PersonnelService;
import com.agriva.task.service.TaskService;
import com.agriva.task.model.dto.requests.TaskActionRequest;
import com.agriva.task.model.dto.requests.TaskCreateRequest;
import com.agriva.task.model.dto.requests.TaskUpdateRequest;
import com.agriva.task.model.dto.requests.TaskValidationRequest;
import com.agriva.task.model.dto.response.ProductivityStatsResponse;
import com.agriva.task.model.dto.response.StagedTaskResponse;
import com.agriva.task.model.dto.response.TaskBulkUploadResponse;
import com.agriva.task.model.dto.response.TaskResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;
    private final PersonnelService personnelService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<TaskResponse> create(
            @AuthenticationPrincipal AuthenticatedUser u,
            @Valid @RequestBody TaskCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(taskService.create(request, u.farmId(), u.userId()));
    }

    @PostMapping(value = "/bulk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<TaskBulkUploadResponse> bulkUpload(
            @AuthenticationPrincipal AuthenticatedUser u,
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.accepted().body(taskService.bulkUpload(
                file, u.farmId(), u.userId(), u.email()));
    }

    @GetMapping
    public ResponseEntity<PageResponse<TaskResponse>> list(
            @AuthenticationPrincipal AuthenticatedUser u,
            @RequestParam(required = false) UUID personnelId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        UUID filter = personnelId;
        if (!u.isMasterOrAdmin()) {
            var own = personnelService.getOwnProfile(u.userId());
            filter = own.getPersonnel().getId();
        }
        return ResponseEntity.ok(PageResponse.of(taskService.list(
                u.farmId(), filter,
                PageRequest.of(page, Math.min(size, 10),
                        Sort.by(Sort.Direction.DESC, "createdAt")))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaskResponse> get(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID id) {
        return ResponseEntity.ok(taskService.get(id, u.farmId()));
    }

    @PutMapping("/{id}/action")
    public ResponseEntity<TaskResponse> action(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID id,
            @Valid @RequestBody TaskActionRequest request) {
        return ResponseEntity.ok(taskService.action(
                id, u.farmId(), u.userId(), u.isMasterOrAdmin(), request.getAction()));
    }

    @PostMapping("/{id}/validate")
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<TaskResponse> validate(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID id,
            @Valid @RequestBody TaskValidationRequest request) {
        return ResponseEntity.ok(taskService.validate(
                id, u.farmId(), u.userId(), request.getApprove()));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<TaskResponse> update(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID id,
            @Valid @RequestBody TaskUpdateRequest request) {
        return ResponseEntity.ok(taskService.update(id, request, u.farmId()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID id) {
        taskService.delete(id, u.farmId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/productivity/{personnelId}")
    public ResponseEntity<ProductivityStatsResponse> productivity(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID personnelId) {
        if (!u.isMasterOrAdmin()) {
            var own = personnelService.getOwnProfile(u.userId());
            if (!own.getPersonnel().getId().equals(personnelId)) {
                throw new PersonnelAccessDeniedException("Access denied");
            }
        }
        return ResponseEntity.ok(taskService.productivity(personnelId, u.farmId()));
    }

    @GetMapping("/staging")
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<PageResponse<StagedTaskResponse>> listStaging(
            @AuthenticationPrincipal AuthenticatedUser u,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(PageResponse.of(taskService.listStaging(
                u.farmId(), status,
                PageRequest.of(page, Math.min(size, 10),
                        Sort.by(Sort.Direction.DESC, "submittedAt")))));
    }

    @GetMapping("/staging/stats")
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<Map<String, Long>> stagingStats(@AuthenticationPrincipal AuthenticatedUser u) {
        return ResponseEntity.ok(taskService.stagingStats(u.farmId()));
    }
}