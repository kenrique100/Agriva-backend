package com.agriva.service;

import com.agriva.model.dto.auth.*;
import com.agriva.model.dto.farm.FarmDetailsResponse;
import com.agriva.model.dto.farm.FarmListResponse;

import java.util.List;
import java.util.UUID;

public interface AuthService {

    /** returns the full AuthResponse so the controller does not call getUserDetails again. */
    AuthResponse login(LoginRequest request);

    /**issue a fresh access + refresh token pair. */
    AuthResponse refresh(String refreshToken);

    /** no longer hits the DB — the principal already contains everything. */
    AuthenticatedUserInfo whoAmI();

    AuthResponse registerMaster(MasterRegisterRequest request);

    /** takes farmId from the authenticated MASTER's token, not the body. */
    SubUserRegisteredResponse registerSubUser(SubUserRegisterRequest request, UUID masterFarmId, String masterRole);

    UserResponse getUserById(UUID userId);
    List<UserListResponse> getUsersByFarmId(UUID farmId);
    List<UserListResponse> getAllUsers();

    FarmDetailsResponse getFarmDetails(UUID farmId);
    FarmListResponse getAllFarms();

    UserResponse updateUser(UUID userId, UpdateUserRequest request, String requesterRole);
    void changePassword(UUID userId, ChangePasswordRequest request);
    void resetOwnPassword(UUID userId, MeResetPasswordRequest request);

    UserResponse updateSubUserByMaster(UUID masterFarmId, UUID subUserId, UpdateUserRequest request);
    UserResponse updateUserByAdmin(UUID userId, UpdateUserRequest request);

    void deleteUserByAdmin(UUID userId);
    void deleteSubUserByMaster(UUID masterFarmId, UUID subUserId);
    void deleteFarmAndAllUsers(UUID masterFarmId, UUID farmIdToDelete);
    void deleteFarmByAdmin(UUID farmId);
    int deleteAllSubUsers();

    void deleteOwnAccount(UUID userId, String confirmationPhrase);

    ForgotPasswordResponse forgotPassword(ForgotPasswordRequest request);
    void resetPassword(ResetPasswordRequest request);

    void logout(String authHeader);
}