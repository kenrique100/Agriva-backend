package com.agriva.controller;

import com.agriva.model.dto.auth.*;
import com.agriva.model.dto.common.MessageResponse;
import com.agriva.model.dto.farm.FarmDetailsResponse;
import com.agriva.model.dto.farm.FarmListResponse;
import com.agriva.security.AuthenticatedUser;
import com.agriva.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;


    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/register/master")
    public ResponseEntity<AuthResponse> registerMaster(
            @Valid @RequestBody MasterRegisterRequest request) {
        return ResponseEntity.status(201).body(authService.registerMaster(request));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ForgotPasswordResponse> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        return ResponseEntity.ok(authService.forgotPassword(request));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<AuthenticatedUserInfo> me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(new AuthenticatedUserInfo(
                principal.userId(), principal.farmId(),
                principal.email(), principal.username(), principal.role()));
    }

    @PutMapping("/me")
    public ResponseEntity<UserResponse> updateMe(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(
                authService.updateUser(principal.userId(), request, principal.role()));
    }

    @PutMapping("/change-password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(principal.userId(), request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/me/reset-password")
    public ResponseEntity<Void> resetOwnPassword(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody MeResetPasswordRequest request) {
        authService.resetOwnPassword(principal.userId(), request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        authService.logout(authHeader);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN','MASTER','SUB_USER')")
    public ResponseEntity<UserResponse> getUserById(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID userId) {
        boolean isAdmin = principal.isAdmin();
        boolean isSelf = principal.userId().equals(userId);
        boolean isMasterSameFarm = principal.isMaster()
                && authService.getUserById(userId).getFarmId() != null
                && authService.getUserById(userId).getFarmId().equals(principal.farmId());
        if (!isAdmin && !isSelf && !isMasterSameFarm) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(authService.getUserById(userId));
    }

    @PostMapping("/register/sub-user")
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<SubUserRegisteredResponse> registerSubUser(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody SubUserRegisterRequest request) {
        // S1: farmId comes from the principal — never from the body.
        return ResponseEntity.status(201)
                .body(authService.registerSubUser(request, principal.farmId(), principal.role()));
    }

    @GetMapping("/farm/users")
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<List<UserListResponse>> getFarmUsers(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(authService.getUsersByFarmId(principal.farmId()));
    }

    @GetMapping("/farm/details")
    @PreAuthorize("hasRole('MASTER')")
    public ResponseEntity<FarmDetailsResponse> getFarmDetails(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(authService.getFarmDetails(principal.farmId()));
    }

    @PutMapping("/master/sub-user/{subUserId}")
    @PreAuthorize("hasRole('MASTER')")
    public ResponseEntity<UserResponse> updateSubUserByMaster(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID subUserId,
            @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(
                authService.updateSubUserByMaster(principal.farmId(), subUserId, request));
    }

    @DeleteMapping("/master/sub-user/{subUserId}")
    @PreAuthorize("hasRole('MASTER')")
    public ResponseEntity<MessageResponse> deleteSubUserByMaster(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID subUserId) {
        authService.deleteSubUserByMaster(principal.farmId(), subUserId);
        return ResponseEntity.ok(new MessageResponse("Sub-user deleted successfully"));
    }

    /**
     * MASTER self-service account deletion. Deletes the farm, every user in it,
     * all financial records (soft-deleted for audit), all personnel, tasks, crops.
     * Requires an explicit confirmation phrase.
     */
    @DeleteMapping("/me")
    public ResponseEntity<MessageResponse> deleteMyAccount(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(name = "confirmation") String confirmation) {
        authService.deleteOwnAccount(principal.userId(), confirmation);
        return ResponseEntity.ok(new MessageResponse("Your account and all related farm data have been deleted"));
    }

    @GetMapping("/admin/all-users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserListResponse>> getAllUsers() {
        return ResponseEntity.ok(authService.getAllUsers());
    }

    @GetMapping("/admin/all-farms")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<FarmListResponse> getAllFarms() {
        return ResponseEntity.ok(authService.getAllFarms());
    }

    @PutMapping("/admin/user/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> updateUserByAdmin(
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(authService.updateUserByAdmin(userId, request));
    }

    @DeleteMapping("/admin/user/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MessageResponse> deleteUserByAdmin(@PathVariable UUID userId) {
        authService.deleteUserByAdmin(userId);
        return ResponseEntity.ok(new MessageResponse("User deleted successfully by ADMIN"));
    }

    @DeleteMapping("/admin/farm/{farmId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MessageResponse> deleteFarmByAdmin(@PathVariable UUID farmId) {
        authService.deleteFarmByAdmin(farmId);
        return ResponseEntity.ok(new MessageResponse("Farm deleted successfully by ADMIN"));
    }

    @DeleteMapping("/admin/delete-all-sub-users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MessageResponse> deleteAllSubUsers() {
        int deletedCount = authService.deleteAllSubUsers();
        return ResponseEntity.ok(new MessageResponse("Deleted " + deletedCount + " sub-users"));
    }
}