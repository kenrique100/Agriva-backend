package com.agriva.controller;

import com.agriva.exception.PersonnelAccessDeniedException;
import com.agriva.model.dto.auth.TokenValidationResponse;
import com.agriva.model.dto.personnel.PersonnelProfileResponse;
import com.agriva.model.dto.task.*;
import com.agriva.model.entity.StagedTaskStatus;
import com.agriva.security.RoleConstants;
import com.agriva.service.AuthService;
import com.agriva.service.PersonnelService;
import com.agriva.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;
    private final AuthService authService;
    private final PersonnelService personnelService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<TaskResponse> create(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody TaskCreateRequest request) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        requireMaster(token);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(taskService.create(request, token.getFarmId(), token.getUserId()));
    }

    @PostMapping(value = "/bulk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ResponseEntity<TaskBulkUploadResponse> bulkUpload(
            @RequestHeader("Authorization") String authHeader,
            @RequestPart("file") MultipartFile file) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        requireMaster(token);
        return ResponseEntity.accepted().body(taskService.bulkUpload(
                file, token.getFarmId(), token.getUserId(),
                authService.getUserById(token.getUserId()).getEmail()));   // FIX
    }

    @GetMapping
    public ResponseEntity<Page<TaskResponse>> list(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(required = false) UUID personnelId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        UUID filter = personnelId;
        if (!isMasterOrAdmin(token)) {
            PersonnelProfileResponse own = personnelService.getOwnProfile(token.getUserId());
            filter = own.getPersonnel().getId();
        }
        return ResponseEntity.ok(taskService.list(
                token.getFarmId(), filter,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaskResponse> get(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        return ResponseEntity.ok(taskService.get(id, token.getFarmId()));
    }

    @PutMapping("/{id}/action")
    public ResponseEntity<TaskResponse> action(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id,
            @Valid @RequestBody TaskActionRequest request) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        return ResponseEntity.ok(taskService.action(
                id, token.getFarmId(), token.getUserId(),
                isMasterOrAdmin(token), request.getAction()));
    }

    @PostMapping("/{id}/validate")
    public ResponseEntity<TaskResponse> validate(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id,
            @Valid @RequestBody TaskValidationRequest request) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        requireMaster(token);
        return ResponseEntity.ok(taskService.validate(
                id, token.getFarmId(), token.getUserId(), request.getApprove()));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<TaskResponse> update(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id,
            @Valid @RequestBody TaskUpdateRequest request) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        requireMaster(token);
        return ResponseEntity.ok(taskService.update(id, request, token.getFarmId()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> delete(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        requireMaster(token);
        taskService.delete(id, token.getFarmId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/productivity/{personnelId}")
    public ResponseEntity<ProductivityStatsResponse> productivity(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID personnelId) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        if (!isMasterOrAdmin(token)) {
            PersonnelProfileResponse own = personnelService.getOwnProfile(token.getUserId());
            if (!own.getPersonnel().getId().equals(personnelId)) {
                throw new PersonnelAccessDeniedException("Access denied");
            }
        }
        return ResponseEntity.ok(taskService.productivity(personnelId, token.getFarmId()));
    }

    @GetMapping("/staging")
    public ResponseEntity<Page<StagedTaskResponse>> listStaging(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        requireMaster(token);
        return ResponseEntity.ok(taskService.listStaging(
                token.getFarmId(), status,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "submittedAt"))));
    }

    @GetMapping("/staging/stats")
    public ResponseEntity<Map<String, Long>> stagingStats(
            @RequestHeader("Authorization") String authHeader) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        requireMaster(token);
        return ResponseEntity.ok(taskService.stagingStats(token.getFarmId()));
    }

    private boolean isMasterOrAdmin(TokenValidationResponse t) {
        return RoleConstants.MASTER.equals(t.getRole()) || RoleConstants.ADMIN.equals(t.getRole());
    }

    private void requireMaster(TokenValidationResponse token) {
        if (!isMasterOrAdmin(token)) throw new PersonnelAccessDeniedException("MASTER or ADMIN required");
    }
}