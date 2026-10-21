package com.agriva.controller;

import com.agriva.exception.PersonnelAccessDeniedException;
import com.agriva.model.dto.auth.TokenValidationResponse;
import com.agriva.model.dto.personnel.*;
import com.agriva.model.entity.PersonnelStatus;
import com.agriva.security.RoleConstants;
import com.agriva.service.AuthService;
import com.agriva.service.PersonnelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/personnel")
@RequiredArgsConstructor
@Slf4j
public class PersonnelController {

    private final PersonnelService personnelService;
    private final AuthService authService;

    // NOTE: POST /personnel REMOVED — Personnel is auto-created when a
    // MASTER/SUB_USER registers via /api/v1/users/register/*.

    @GetMapping
    public ResponseEntity<Page<PersonnelResponse>> list(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        requireMaster(token);
        PersonnelStatus st = (status == null || status.isBlank())
                ? null : PersonnelStatus.valueOf(status.toUpperCase());
        return ResponseEntity.ok(personnelService.list(
                token.getFarmId(), st,
                PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "name"))));
    }

    @GetMapping("/me")
    public ResponseEntity<PersonnelProfileResponse> myProfile(
            @RequestHeader("Authorization") String authHeader) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        return ResponseEntity.ok(personnelService.getOwnProfile(token.getUserId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PersonnelProfileResponse> getOne(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        boolean isMaster = RoleConstants.MASTER.equals(token.getRole())
                || RoleConstants.ADMIN.equals(token.getRole());
        if (!isMaster) {
            PersonnelProfileResponse own = personnelService.getOwnProfile(token.getUserId());
            if (!own.getPersonnel().getId().equals(id)) {
                throw new PersonnelAccessDeniedException("Access denied");
            }
            return ResponseEntity.ok(own);
        }
        return ResponseEntity.ok(personnelService.getProfile(id, token.getFarmId()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PersonnelResponse> update(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id,
            @Valid @RequestBody PersonnelUpdateRequest request) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        requireMaster(token);
        return ResponseEntity.ok(personnelService.update(id, request, token.getFarmId()));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<PersonnelResponse> changeStatus(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id,
            @Valid @RequestBody PersonnelStatusUpdateRequest request) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        requireMaster(token);
        return ResponseEntity.ok(personnelService.changeStatus(id, request, token.getFarmId()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> delete(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        requireMaster(token);
        personnelService.delete(id, token.getFarmId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/status-history")
    public ResponseEntity<?> statusHistory(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        requireMaster(token);
        return ResponseEntity.ok(personnelService.getStatusHistory(id, token.getFarmId()));
    }

    @PostMapping(value = "/{id}/picture", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PersonnelResponse> uploadPicture(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id,
            @RequestPart("file") MultipartFile file) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        boolean isMaster = RoleConstants.MASTER.equals(token.getRole())
                || RoleConstants.ADMIN.equals(token.getRole());
        if (!isMaster) {
            PersonnelProfileResponse own = personnelService.getOwnProfile(token.getUserId());
            if (!own.getPersonnel().getId().equals(id)) {
                throw new PersonnelAccessDeniedException(
                        "You can only upload your own profile picture");
            }
        }
        return ResponseEntity.ok(personnelService.uploadPicture(id, token.getFarmId(), file));
    }

    private void requireMaster(TokenValidationResponse token) {
        if (!RoleConstants.MASTER.equals(token.getRole())
                && !RoleConstants.ADMIN.equals(token.getRole())) {
            throw new PersonnelAccessDeniedException("MASTER or ADMIN required");
        }
    }
}