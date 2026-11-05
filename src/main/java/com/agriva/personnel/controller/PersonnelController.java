package com.agriva.personnel.controller;

import com.agriva.personnel.models.dto.response.PersonnelProfileResponse;
import com.agriva.personnel.models.dto.request.PersonnelStatusUpdateRequest;
import com.agriva.personnel.models.dto.request.PersonnelUpdateRequest;
import com.agriva.personnel.models.dto.response.PersonnelResponse;
import com.agriva.personnel.models.dto.response.PersonnelStatusHistoryResponse;
import com.agriva.shared.exception.PersonnelAccessDeniedException;
import com.agriva.shared.models.dto.PageResponse;
import com.agriva.personnel.models.enums.PersonnelStatus;
import com.agriva.shared.security.AuthenticatedUser;
import com.agriva.personnel.service.PersonnelService;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/personnel")
@RequiredArgsConstructor
public class PersonnelController {

    private final PersonnelService personnelService;

    @GetMapping
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<PageResponse<PersonnelResponse>> list(
            @AuthenticationPrincipal AuthenticatedUser u,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PersonnelStatus st = (status == null || status.isBlank())
                ? null : PersonnelStatus.valueOf(status.toUpperCase());

        return ResponseEntity.ok(PageResponse.of(personnelService.list(
                u.farmId(), st,
                PageRequest.of(page, Math.min(size, 100),
                        Sort.by(Sort.Direction.ASC, "name")))));
    }

    @GetMapping("/me")
    public ResponseEntity<PersonnelProfileResponse> myProfile(@AuthenticationPrincipal AuthenticatedUser u) {
        return ResponseEntity.ok(personnelService.getOwnProfile(u.userId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PersonnelProfileResponse> getOne(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID id) {

        if (!u.isMasterOrAdmin()) {
            PersonnelProfileResponse own = personnelService.getOwnProfile(u.userId());
            if (!own.getPersonnel().getId().equals(id)) {
                throw new PersonnelAccessDeniedException("Access denied");
            }
            return ResponseEntity.ok(own);
        }
        return ResponseEntity.ok(personnelService.getProfile(id, u.farmId()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<PersonnelResponse> update(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID id,
            @Valid @RequestBody PersonnelUpdateRequest request) {
        return ResponseEntity.ok(personnelService.update(id, request, u.farmId()));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<PersonnelResponse> changeStatus(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID id,
            @Valid @RequestBody PersonnelStatusUpdateRequest request) {
        return ResponseEntity.ok(personnelService.changeStatus(id, request, u.farmId()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID id) {
        personnelService.delete(id, u.farmId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/status-history")
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<List<PersonnelStatusHistoryResponse>> statusHistory(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID id) {
        return ResponseEntity.ok(personnelService.getStatusHistory(id, u.farmId()));
    }

    @DeleteMapping("/{id}/picture")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> deletePicture(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID id) {
        if (!u.isMasterOrAdmin()) {
            PersonnelProfileResponse own = personnelService.getOwnProfile(u.userId());
            if (!own.getPersonnel().getId().equals(id)) {
                throw new PersonnelAccessDeniedException(
                        "You can only delete your own profile picture");
            }
        }
        personnelService.deletePicture(id, u.farmId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/{id}/picture", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PersonnelResponse> uploadPicture(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID id,
            @RequestPart("file") MultipartFile file) {
        if (!u.isMasterOrAdmin()) {
            PersonnelProfileResponse own = personnelService.getOwnProfile(u.userId());
            if (!own.getPersonnel().getId().equals(id)) {
                throw new PersonnelAccessDeniedException(
                        "You can only upload your own profile picture");
            }
        }
        return ResponseEntity.ok(personnelService.uploadPicture(id, u.farmId(), file));
    }
}