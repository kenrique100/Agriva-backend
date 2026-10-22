package com.agriva.service;

import com.agriva.model.dto.auth.*;
import com.agriva.model.dto.farm.FarmDetailsResponse;
import com.agriva.model.dto.farm.FarmListResponse;

import java.util.List;
import java.util.UUID;

public interface AuthService {

    String login(LoginRequest request);

    UserDetails getUserDetails(String email);

    TokenValidationResponse validateToken(String authHeader);

    AuthResponse registerMaster(MasterRegisterRequest request);

    String registerSubUser(SubUserRegisterRequest request);

    UserResponse getUserById(UUID userId);

    List<UserListResponse> getUsersByFarmId(UUID farmId);

    List<UserListResponse> getAllUsers();

    FarmDetailsResponse getFarmDetails(UUID farmId);

    FarmListResponse getAllFarms();

    UserResponse updateUser(UUID userId, UpdateUserRequest request, String requesterRole);

    void changePassword(UUID userId, ChangePasswordRequest request);

    UserResponse updateSubUserByMaster(UUID masterFarmId, UUID subUserId, UpdateUserRequest request);

    UserResponse updateUserByAdmin(UUID userId, UpdateUserRequest request);

    void deleteUserByAdmin(UUID userId);

    void deleteSubUserByMaster(UUID masterFarmId, UUID subUserId);

    void deleteFarmAndAllUsers(UUID masterFarmId, UUID farmIdToDelete);

    void deleteFarmByAdmin(UUID farmId);

    int deleteAllSubUsers();

    ForgotPasswordResponse forgotPassword(ForgotPasswordRequest request);

    void resetPassword(ResetPasswordRequest request);

    void resetOwnPassword(UUID userId, MeResetPasswordRequest request);

    void logout(String authHeader);
}