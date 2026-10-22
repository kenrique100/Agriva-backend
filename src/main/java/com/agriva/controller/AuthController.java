package com.agriva.controller;

import com.agriva.model.dto.auth.*;
import com.agriva.model.dto.farm.FarmDetailsResponse;
import com.agriva.model.dto.farm.FarmListResponse;
import com.agriva.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
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
        String token = authService.login(request);
        UserDetails userDetails = authService.getUserDetails(request.getEmailOrUsername());

        return ResponseEntity.ok(new AuthResponse(
                token,
                userDetails.getEmail(),
                userDetails.getUsername(),
                userDetails.getRole(),
                userDetails.getFarmId(),
                userDetails.getFarmName(),
                userDetails.getPreferredCurrency()
        ));
    }

    @PostMapping("/validate")
    public ResponseEntity<TokenValidationResponse> validate(
            @RequestHeader("Authorization") String authHeader) {
        return ResponseEntity.ok(authService.validateToken(authHeader));
    }

    @PostMapping("/register/master")
    public ResponseEntity<AuthResponse> registerMaster(
            @Valid @RequestBody MasterRegisterRequest request) {
        return ResponseEntity.ok(authService.registerMaster(request));
    }

    @PostMapping("/register/sub-user")
    public ResponseEntity<String> registerSubUser(
            @Valid @RequestBody SubUserRegisterRequest request) {
        return ResponseEntity.ok(authService.registerSubUser(request));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ForgotPasswordResponse> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        return ResponseEntity.ok(authService.forgotPassword(request));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok("Password reset successfully.");
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout(@RequestHeader("Authorization") String authHeader) {
        authService.logout(authHeader);
        return ResponseEntity.ok("Logged out successfully");
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(
            @RequestHeader("Authorization") String authHeader) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        if (token.getUserId() == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(authService.getUserById(token.getUserId()));
    }

    @PutMapping("/me")
    public ResponseEntity<UserResponse> updateCurrentUser(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody UpdateUserRequest request) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        return ResponseEntity.ok(
                authService.updateUser(token.getUserId(), request, token.getRole()));
    }

    @PutMapping("/change-password")
    public ResponseEntity<String> changePassword(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody ChangePasswordRequest request) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        authService.changePassword(token.getUserId(), request);
        return ResponseEntity.ok("Password changed successfully");
    }

    @PostMapping("/me/reset-password")
    public ResponseEntity<String> resetOwnPassword(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody MeResetPasswordRequest request) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        authService.resetOwnPassword(token.getUserId(), request);
        return ResponseEntity.ok("Password reset successfully.");
    }

    @GetMapping("/{userId}")
    public ResponseEntity<UserResponse> getUserById(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID userId) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        boolean isAdmin = "ADMIN".equals(token.getRole());
        boolean isSelf = token.getUserId() != null && token.getUserId().equals(userId);
        if (!isAdmin && !isSelf) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(authService.getUserById(userId));
    }

    @GetMapping("/farm/users")
    public ResponseEntity<List<UserListResponse>> getFarmUsers(
            @RequestHeader("Authorization") String authHeader) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        if (!"MASTER".equals(token.getRole())) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(authService.getUsersByFarmId(token.getFarmId()));
    }

    @GetMapping("/admin/all-users")
    public ResponseEntity<List<UserListResponse>> getAllUsers(
            @RequestHeader("Authorization") String authHeader) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        if (!"ADMIN".equals(token.getRole())) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(authService.getAllUsers());
    }

    @PutMapping("/master/sub-user/{subUserId}")
    public ResponseEntity<UserResponse> updateSubUserByMaster(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID subUserId,
            @Valid @RequestBody UpdateUserRequest request) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        if (!"MASTER".equals(token.getRole())) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(
                authService.updateSubUserByMaster(token.getFarmId(), subUserId, request));
    }

    @DeleteMapping("/master/sub-user/{subUserId}")
    public ResponseEntity<String> deleteSubUserByMaster(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID subUserId) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        if (!"MASTER".equals(token.getRole())) {
            return ResponseEntity.status(403).body("Only MASTER can delete sub-users");
        }
        authService.deleteSubUserByMaster(token.getFarmId(), subUserId);
        return ResponseEntity.ok("Sub-user deleted successfully");
    }

    @DeleteMapping("/master/farm")
    public ResponseEntity<String> deleteMyFarm(
            @RequestHeader("Authorization") String authHeader) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        if (!"MASTER".equals(token.getRole())) {
            return ResponseEntity.status(403).body("Only MASTER can delete farms");
        }
        authService.deleteFarmAndAllUsers(token.getFarmId(), token.getFarmId());
        return ResponseEntity.ok("Your farm and all associated users deleted successfully");
    }

    @GetMapping("/farm/details")
    public ResponseEntity<FarmDetailsResponse> getFarmDetails(
            @RequestHeader("Authorization") String authHeader) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        if (!"MASTER".equals(token.getRole())) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(authService.getFarmDetails(token.getFarmId()));
    }

    @PutMapping("/admin/user/{userId}")
    public ResponseEntity<UserResponse> updateUserByAdmin(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateUserRequest request) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        if (!"ADMIN".equals(token.getRole())) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(authService.updateUserByAdmin(userId, request));
    }

    @DeleteMapping("/admin/user/{userId}")
    public ResponseEntity<String> deleteUserByAdmin(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID userId) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        if (!"ADMIN".equals(token.getRole())) {
            return ResponseEntity.status(403).body("Only ADMIN can delete users");
        }
        authService.deleteUserByAdmin(userId);
        return ResponseEntity.ok("User deleted successfully by ADMIN");
    }

    @DeleteMapping("/admin/farm/{farmId}")
    public ResponseEntity<String> deleteFarmByAdmin(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID farmId) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        if (!"ADMIN".equals(token.getRole())) {
            return ResponseEntity.status(403).body("Only ADMIN can delete farms");
        }
        authService.deleteFarmByAdmin(farmId);
        return ResponseEntity.ok("Farm deleted successfully by ADMIN");
    }

    @DeleteMapping("/admin/delete-all-sub-users")
    public ResponseEntity<String> deleteAllSubUsers(
            @RequestHeader("Authorization") String authHeader) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        if (!"ADMIN".equals(token.getRole())) {
            return ResponseEntity.status(403).body("Only ADMIN can perform this action");
        }
        int deletedCount = authService.deleteAllSubUsers();
        return ResponseEntity.ok("Deleted " + deletedCount + " sub-users successfully");
    }

    @GetMapping("/admin/all-farms")
    public ResponseEntity<FarmListResponse> getAllFarms(
            @RequestHeader("Authorization") String authHeader) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        if (!"ADMIN".equals(token.getRole())) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(authService.getAllFarms());
    }

    @DeleteMapping("/me")
    public ResponseEntity<String> deleteCurrentUser(
            @RequestHeader("Authorization") String authHeader) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        authService.deleteUserByAdmin(token.getUserId());
        return ResponseEntity.ok("Your account has been deleted");
    }
}