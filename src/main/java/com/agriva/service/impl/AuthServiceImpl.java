package com.agriva.service.impl;

import com.agriva.exception.BusinessException;
import com.agriva.exception.InvalidCredentialsException;
import com.agriva.exception.InvalidTokenException;
import com.agriva.model.dto.*;
import com.agriva.model.entity.User;
import com.agriva.repository.AuthRepository;
import com.agriva.security.JwtUtil;
import com.agriva.service.AuthService;
import com.agriva.service.PasswordResetService;
import com.agriva.service.TokenBlacklistService;
import com.agriva.utils.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final AuthRepository authRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final UserValidationUtils userValidation;
    private final FarmUtils farmUtils;
    private final UserBuilderUtils userBuilder;
    private final FarmBuilderUtils farmBuilder;
    private final ResponseBuilderUtils responseBuilder;
    private final UserDeleteUtils userDeleteUtils;
    private final PasswordResetService passwordResetService;
    private final TokenBlacklistService tokenBlacklistService;

    @Value("${agriva.security.password-reset.expose-token:false}")
    private boolean exposeResetToken;

    @Value("${agriva.security.password-reset.ttl-seconds:900}")
    private long resetTokenTtlSeconds;

    @Override
    public String login(LoginRequest request) {
        User user = authRepository.findByEmailOrUsername(request.getEmailOrUsername(), request.getEmailOrUsername())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credentials"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            log.warn("Failed login attempt for {}", request.getEmailOrUsername());
            throw new InvalidCredentialsException("Invalid credentials");
        }

        log.info("Successful login for {} with role {}", user.getEmail(), user.getRole());
        return jwtUtil.generateToken(user);
    }

    @Override
    public UserDetails getUserDetails(String email) {
        User user = userValidation.validateAndGetUserByEmail(email);
        return responseBuilder.buildUserDetails(user);
    }

    @Override
    public TokenValidationResponse validateToken(String authHeader) {
        String token = userValidation.validateAndExtractToken(authHeader);
        jwtUtil.validateToken(token);
        return responseBuilder.buildTokenValidationResponse(token, jwtUtil);
    }

    @Override
    @Transactional
    public AuthResponse registerMaster(MasterRegisterRequest request) {
        log.info("Registering master: {}", request.getEmail());

        PasswordValidator.validatePasswordStrength(request.getPassword());
        userValidation.validateEmailNotExists(request.getEmail());
        userValidation.validateUsernameNotExists(request.getUsername());
        farmUtils.validateFarmNameUniqueness(request.getFarmName());

        FarmBuilderUtils.FarmData farmData = farmBuilder.createNewFarm(request.getFarmName());
        User user = userBuilder.createMasterUser(request, farmData.farmId(), passwordEncoder);
        farmBuilder.linkMasterToFarm(farmData.farm(), user.getId());

        log.info("Master registered: {} for farm {}", user.getEmail(), farmData.farmName());
        return responseBuilder.buildAuthResponse(user, jwtUtil);
    }

    @Override
    @Transactional
    public String registerSubUser(SubUserRegisterRequest request) {
        log.info("Registering sub-user: {}", request.getEmail());

        PasswordValidator.validatePasswordStrength(request.getPassword());
        userValidation.validateEmailNotExists(request.getEmail());
        userValidation.validateUsernameNotExists(request.getUsername());
        farmUtils.validateFarmExistsWithMaster(request.getFarmId());

        String role = userValidation.normalizeSubUserRole(request.getRole());
        User user = userBuilder.createSubUser(request, passwordEncoder, role);

        log.info("Sub-user registered: {} for farm {}", user.getEmail(), user.getFarmId());
        return responseBuilder.buildSubUserResponse(user.getEmail());
    }

    @Override
    public FarmDetailsResponse getFarmDetails(UUID farmId) {
        FarmUtils.FarmData farmData = farmUtils.getFarmWithValidation(farmId);
        List<User> farmUsers = authRepository.findByFarmId(farmId);

        User master = userValidation.extractMasterFromUsers(farmUsers);
        List<User> subUsers = userValidation.extractSubUsersFromUsers(farmUsers);

        return responseBuilder.buildFarmDetailsResponse(farmData, master, subUsers);
    }

    @Override
    public FarmsListResponse getAllFarms() {
        List<FarmUtils.FarmData> allFarms = farmUtils.getAllFarms();
        return responseBuilder.buildFarmsListResponse(allFarms, this);
    }

    @Override
    public UserResponse getUserById(UUID userId) {
        User user = userValidation.validateAndGetUser(userId);
        return responseBuilder.buildUserResponse(user);
    }

    @Override
    public List<UserListResponse> getUsersByFarmId(UUID farmId) {
        List<User> users = authRepository.findByFarmId(farmId);
        return responseBuilder.buildUserListResponse(users);
    }

    @Override
    public List<UserListResponse> getAllUsers() {
        List<User> users = authRepository.findAll();
        return responseBuilder.buildUserListResponse(users);
    }

    @Override
    @Transactional
    public UserResponse updateUser(UUID userId, UpdateUserRequest request, String requesterRole) {
        User user = userValidation.validateAndGetUser(userId);

        userValidation.updateUserEmail(user, request.getEmail(), userId);
        userValidation.updateUserUsername(user, request.getUsername(), userId);
        userValidation.updateUserRole(user, request.getRole(), requesterRole);

        User updated = authRepository.save(user);
        log.info("User updated: {}", updated.getEmail());
        return responseBuilder.buildUserResponse(updated);
    }

    @Override
    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = userValidation.validateAndGetUser(userId);

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new InvalidCredentialsException("Current password is incorrect");
        }

        if (request.getCurrentPassword().equals(request.getNewPassword())) {
            throw new BusinessException("New password must be different from the current one");
        }

        PasswordValidator.validatePasswordStrength(request.getNewPassword());

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        authRepository.save(user);
        log.info("Password changed for user: {}", user.getEmail());
    }

    @Override
    public ForgotPasswordResponse forgotPassword(ForgotPasswordRequest request) {
        String token = passwordResetService.initiateReset(request.getEmail());

        String genericMessage =
                "If an account exists for " + request.getEmail()
                        + ", a password reset link has been sent.";

        if (!exposeResetToken || token == null) {
            return new ForgotPasswordResponse(genericMessage, null, null);
        }

        return new ForgotPasswordResponse(genericMessage, token, resetTokenTtlSeconds);
    }

    @Override
    public void resetPassword(ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.getToken(), request.getNewPassword());
    }

    @Override
    @Transactional
    public void resetOwnPassword(UUID userId, MeResetPasswordRequest request) {
        User user = userValidation.validateAndGetUser(userId);

        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new BusinessException("New password must be different from the current one");
        }

        PasswordValidator.validatePasswordStrength(request.getNewPassword());

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        authRepository.save(user);

        log.info("Authenticated password reset for userId={} email={}",
                user.getId(), user.getEmail());
    }

    // NEW: sign-out
    @Override
    public void logout(String authHeader) {
        String token = userValidation.validateAndExtractToken(authHeader);

        if (!jwtUtil.validateToken(token)) {
            throw new InvalidTokenException("Token is invalid or expired");
        }

        String jti = jwtUtil.extractJti(token);
        if (jti == null || jti.isBlank()) {
            // Legacy token issued before jti was added — cannot blacklist precisely,
            // so we simply log out the caller (they just stop using the token).
            log.warn("Logout called with legacy token without jti; no blacklist entry created");
            return;
        }

        Date expiresAt = jwtUtil.extractExpiration(token);
        long ttlSeconds = (expiresAt.getTime() - System.currentTimeMillis()) / 1000L;

        if (ttlSeconds <= 0) {
            log.info("Logout called for already-expired token jti={}", jti);
            return;
        }

        tokenBlacklistService.blacklist(jti, ttlSeconds);
        log.info("User logged out, jti={} blacklisted for {}s", jti, ttlSeconds);
    }

    @Override
    @Transactional
    public UserResponse updateSubUserByMaster(UUID masterFarmId, UUID subUserId, UpdateUserRequest request) {
        User subUser = userValidation.validateAndGetUser(subUserId);

        userValidation.validateFarmOwnership(subUser.getFarmId(), masterFarmId, "update");
        userValidation.validateSubUserRole(subUser);
        userValidation.validateMasterCannotChangeRole(request.getRole(), subUser.getRole());

        userValidation.updateUserEmail(subUser, request.getEmail(), subUserId);
        userValidation.updateUserUsername(subUser, request.getUsername(), subUserId);

        User updated = authRepository.save(subUser);
        log.info("Master updated sub-user: {}", updated.getEmail());
        return responseBuilder.buildUserResponse(updated);
    }

    @Override
    @Transactional
    public UserResponse updateUserByAdmin(UUID userId, UpdateUserRequest request) {
        User user = userValidation.validateAndGetUser(userId);

        userValidation.updateUserEmail(user, request.getEmail(), userId);
        userValidation.updateUserUsername(user, request.getUsername(), userId);
        userValidation.updateUserRoleAdmin(user, request.getRole());

        User updated = authRepository.save(user);
        log.info("Admin updated user: {}", updated.getEmail());
        return responseBuilder.buildUserResponse(updated);
    }

    @Override
    @Transactional
    public void deleteUserByAdmin(UUID userId) {
        userDeleteUtils.deleteUserAndFarmIfMaster(userId);
    }

    @Override
    @Transactional
    public void deleteSubUserByMaster(UUID masterFarmId, UUID subUserId) {
        userDeleteUtils.deleteSubUserByMaster(masterFarmId, subUserId);
    }

    @Override
    @Transactional
    public void deleteFarmAndAllUsers(UUID masterFarmId, UUID farmIdToDelete) {
        farmUtils.validateMasterFarmOwnership(masterFarmId, farmIdToDelete);
        farmUtils.deleteFarmAndAllUsers(farmIdToDelete, "Master");
    }

    @Override
    @Transactional
    public void deleteFarmByAdmin(UUID farmId) {
        farmUtils.deleteFarmAndAllUsers(farmId, "Admin");
    }

    @Override
    @Transactional
    public int deleteAllSubUsers() {
        return userDeleteUtils.deleteAllSubUsers();
    }
}