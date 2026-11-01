package com.agriva.service.impl;

import com.agriva.exception.BusinessException;
import com.agriva.exception.InvalidCredentialsException;
import com.agriva.exception.InvalidTokenException;
import com.agriva.exception.RateLimitExceededException;
import com.agriva.mapper.FarmMapper;
import com.agriva.mapper.UserMapper;
import com.agriva.model.dto.auth.*;
import com.agriva.model.dto.farm.FarmDetailsResponse;
import com.agriva.model.dto.farm.FarmListResponse;
import com.agriva.model.entity.*;
import com.agriva.model.entity.enums.Department;
import com.agriva.model.entity.enums.PersonnelStatus;
import com.agriva.repository.*;
import com.agriva.security.AuthenticatedUser;
import com.agriva.security.JwtUtil;
import com.agriva.security.RoleConstants;
import com.agriva.service.AuthService;
import com.agriva.service.PasswordResetService;
import com.agriva.service.RefreshTokenService;
import com.agriva.service.TokenBlacklistService;
import com.agriva.utils.PasswordValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private static final String LOGIN_FAIL_KEY_PREFIX = "agriva:auth:loginfail:";
    private static final Duration LOCKOUT_WINDOW = Duration.ofMinutes(15);

    private final AuthRepository authRepository;
    private final FarmRepository farmRepository;
    private final IncomeRepository incomeRepository;
    private final ExpenseRepository expenseRepository;
    private final InvestmentRepository investmentRepository;
    private final TransactionRepository transactionRepository;
    private final PersonnelRepository personnelRepository;
    private final PersonnelStatusHistoryRepository personnelStatusHistoryRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RefreshTokenService refreshTokenService;
    private final PasswordResetService passwordResetService;
    private final TokenBlacklistService tokenBlacklistService;
    private final UserMapper userMapper;
    private final FarmMapper farmMapper;
    private final PasswordValidator passwordValidator;
    private final StringRedisTemplate redis;

    @Value("${agriva.security.password-reset.expose-token:false}")
    private boolean exposeResetToken;

    @Value("${agriva.security.password-reset.ttl-seconds:900}")
    private long resetTokenTtlSeconds;

    /** Reuses the existing property — see application.properties. */
    @Value("${agriva.rate-limit.login-per-hour-per-user:10}")
    private int maxFailedLoginAttempts;

    // ---------------- Login ----------------

    @Override
    public AuthResponse login(LoginRequest request) {
        String identity = request.getEmailOrUsername() == null
                ? "" : request.getEmailOrUsername().toLowerCase().trim();
        String lockKey = LOGIN_FAIL_KEY_PREFIX + identity;

        if (isLocked(lockKey)) {
            log.warn("Login attempt for locked account: {}", identity);
            throw new RateLimitExceededException(
                    "Too many failed login attempts. Try again later.",
                    LOCKOUT_WINDOW.getSeconds());
        }

        Optional<User> userOpt = authRepository.findByEmailOrUsernameLive(identity, identity);

        // Constant-time failure path
        String storedHash = userOpt.map(User::getPassword).orElse(
                "$2a$12$C6UzMDM.H6dfI/f/IKcEeO7ZBvnpCL6VgIi89oBW3eZ6y2r6WJZWi");
        boolean matches = passwordEncoder.matches(request.getPassword(), storedHash);

        if (userOpt.isEmpty() || !matches) {
            recordLoginFailure(lockKey);
            log.warn("Failed login attempt for {}", identity);
            throw new InvalidCredentialsException("Invalid credentials");
        }

        // Success: clear counter
        try { redis.delete(lockKey); } catch (Exception ignored) { }

        User user = userOpt.get();
        String farmName = user.getFarmId() != null
                ? farmRepository.findById(user.getFarmId()).map(Farm::getName).orElse(null)
                : null;

        String jti = UUID.randomUUID().toString();
        String accessToken = jwtUtil.generateToken(user, jti);
        String refreshToken = refreshTokenService.issue(user.getId());

        log.info("Successful login for {} role={}", user.getEmail(), user.getRole());

        return new AuthResponse(
                user.getId(),
                accessToken, refreshToken, jwtUtil.getAccessTtlSeconds(),
                user.getEmail(), user.getUsername(), user.getRole(),
                user.getFarmId(), farmName, user.getPreferredCurrency());
    }

    private boolean isLocked(String key) {
        try {
            String v = redis.opsForValue().get(key);
            return v != null && Integer.parseInt(v) >= maxFailedLoginAttempts;
        } catch (Exception e) {
            log.warn("Login lockout lookup failed (fail-open): {}", e.getMessage());
            return false;
        }
    }

    private void recordLoginFailure(String key) {
        try {
            Long n = redis.opsForValue().increment(key);
            if (n != null && n == 1L) redis.expire(key, LOCKOUT_WINDOW);
        } catch (Exception e) {
            log.warn("Login lockout write failed: {}", e.getMessage());
        }
    }

    @Override
    public AuthResponse refresh(String refreshToken) {
        UUID userId = refreshTokenService.consumeAndRotate(refreshToken);
        User user = authRepository.findByIdLive(userId)
                .orElseThrow(() -> new InvalidTokenException("Refresh token refers to a deleted user"));

        String farmName = user.getFarmId() != null
                ? farmRepository.findById(user.getFarmId()).map(Farm::getName).orElse(null)
                : null;

        String jti = UUID.randomUUID().toString();
        String newAccess = jwtUtil.generateToken(user, jti);
        String newRefresh = refreshTokenService.issue(user.getId());

        return new AuthResponse(
                user.getId(),
                newAccess, newRefresh, jwtUtil.getAccessTtlSeconds(),
                user.getEmail(), user.getUsername(), user.getRole(),
                user.getFarmId(), farmName, user.getPreferredCurrency());
    }

    @Override
    public AuthenticatedUserInfo whoAmI() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthenticatedUser u)) {
            throw new InvalidCredentialsException("Not authenticated");
        }
        return new AuthenticatedUserInfo(u.userId(), u.farmId(), u.email(), u.username(), u.role());
    }

    @Override
    public void logout(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new InvalidCredentialsException("Missing or invalid Authorization header");
        }
        String token = authHeader.substring(7);
        String jti = jwtUtil.extractJti(token);
        long ttl = jwtUtil.getSecondsUntilExpiry(token);
        if (jti != null && ttl > 0) {
            tokenBlacklistService.blacklist(jti, ttl);
        }
        UUID userId = jwtUtil.extractUserId(token);
        if (userId != null) {
            refreshTokenService.revokeAllForUser(userId);
        }
    }

    // ---------------- Registration ----------------

    @Override
    @Transactional
    public AuthResponse registerMaster(MasterRegisterRequest request) {
        passwordValidator.validatePasswordStrength(request.getPassword());
        validateEmailNotExists(request.getEmail());
        validateUsernameNotExists(request.getUsername());

        if (request.getDateOfBirth().plusYears(18).isAfter(LocalDate.now())) {
            throw new BusinessException("Master user must be at least 18 years old.");
        }
        if (farmRepository.existsByNameLive(request.getFarmName())) {
            throw new BusinessException("Farm name already taken");
        }

        Farm farm = farmRepository.save(Farm.builder()
                .name(request.getFarmName())
                .masterId(null)
                .build());

        User master = authRepository.save(User.builder()
                .email(request.getEmail())
                .username(trimToNull(request.getUsername()))
                .password(passwordEncoder.encode(request.getPassword()))
                .role(RoleConstants.MASTER)
                .farmId(farm.getId())
                .gender(request.getGender())
                .dateOfBirth(request.getDateOfBirth())
                .phoneNumber(request.getPhoneNumber())
                .address(request.getAddress())
                .city(request.getCity())
                .state(request.getState())
                .country(request.getCountry())
                .postalCode(request.getPostalCode())
                .preferredCurrency(request.getCurrency())
                .build());

        farm.setMasterId(master.getId());
        farmRepository.save(farm);

        createPersonnelForUser(master, request.getName(), Department.ADMINISTRATION);
        log.info("Master registered: {} for farm {}", master.getEmail(), farm.getName());

        String jti = UUID.randomUUID().toString();

        // ⬇⬇⬇ THE COMPILE ERROR FIX: `user.getId()` → `master.getId()`
        return new AuthResponse(
                master.getId(),
                jwtUtil.generateToken(master, jti),
                refreshTokenService.issue(master.getId()),
                jwtUtil.getAccessTtlSeconds(),
                master.getEmail(), master.getUsername(), master.getRole(),
                master.getFarmId(), farm.getName(), master.getPreferredCurrency());
    }

    @Override
    @Transactional
    public SubUserRegisteredResponse registerSubUser(SubUserRegisterRequest request,
                                                     UUID masterFarmId,
                                                     String masterRole) {
        if (masterFarmId == null) {
            throw new BusinessException("Caller has no farm context");
        }
        if (!RoleConstants.MASTER.equals(masterRole) && !RoleConstants.ADMIN.equals(masterRole)) {
            throw new BusinessException("Only MASTER or ADMIN can register sub-users");
        }

        passwordValidator.validatePasswordStrength(request.getPassword());
        validateEmailNotExists(request.getEmail());
        validateUsernameNotExists(request.getUsername());

        if (request.getDateOfBirth() == null ||
                request.getDateOfBirth().plusYears(18).isAfter(LocalDate.now())) {
            throw new BusinessException("Sub-user must be at least 18 years old.");
        }

        User subUser = authRepository.save(User.builder()
                .email(request.getEmail())
                .username(trimToNull(request.getUsername()))
                .password(passwordEncoder.encode(request.getPassword()))
                .role(RoleConstants.SUB_USER)          // role is ALWAYS SUB_USER
                .farmId(masterFarmId)                   // farm from token, not body
                .gender(request.getGender())
                .dateOfBirth(request.getDateOfBirth())
                .phoneNumber(request.getPhoneNumber())
                .address(request.getAddress())
                .build());

        createPersonnelForUser(
                subUser, request.getName(),
                request.getDepartment() != null ? request.getDepartment() : Department.ADMINISTRATION);

        log.info("Sub-user registered: {} for farm {}", subUser.getEmail(), subUser.getFarmId());
        return new SubUserRegisteredResponse(subUser.getId(), subUser.getEmail(), masterFarmId);
    }

    private Personnel createPersonnelForUser(User user, String preferredName, Department department) {
        String name = preferredName != null && !preferredName.isBlank()
                ? preferredName.trim()
                : (user.getUsername() != null && !user.getUsername().isBlank()
                   ? user.getUsername()
                   : user.getEmail().split("@")[0]);

        Personnel p = personnelRepository.save(Personnel.builder()
                .farmId(user.getFarmId())
                .userId(user.getId())
                .name(name)
                .gender(user.getGender())
                .dateOfBirth(user.getDateOfBirth())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .department(department)
                .dateOfEmployment(LocalDate.now())
                .status(PersonnelStatus.ACTIVE)
                .statusChangeTimestamp(LocalDateTime.now())
                .build());

        personnelStatusHistoryRepository.save(PersonnelStatusHistory.builder()
                .personnel(p)
                .status(PersonnelStatus.ACTIVE)
                .startTimestamp(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build());

        return p;
    }

    // ---------------- Reads ----------------

    @Override public UserResponse getUserById(UUID userId) { return userMapper.toResponse(loadUser(userId)); }
    @Override public List<UserListResponse> getUsersByFarmId(UUID farmId) {
        return userMapper.toListResponse(authRepository.findByFarmIdLive(farmId));
    }
    @Override public List<UserListResponse> getAllUsers() {
        return userMapper.toListResponse(authRepository.findAllLive());
    }

    @Override
    public FarmDetailsResponse getFarmDetails(UUID farmId) {
        Farm farm = farmRepository.findByIdLive(farmId)
                .orElseThrow(() -> new BusinessException("Farm not found with ID: " + farmId));
        return buildFarmDetails(farm);
    }

    @Override
    public FarmListResponse getAllFarms() {
        List<FarmDetailsResponse> details = new ArrayList<>();
        for (Farm f : farmRepository.findAllLive()) {
            details.add(buildFarmDetails(f));
        }
        return farmMapper.toList(details);
    }

    // ---------------- Updates ----------------

    @Override
    @Transactional
    public UserResponse updateUser(UUID userId, UpdateUserRequest request, String requesterRole) {
        User user = loadUser(userId);
        applyProfileUpdates(user, request, userId);
        applyRoleChange(user, request.getRole(), requesterRole);
        return userMapper.toResponse(authRepository.save(user));
    }

    @Override
    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = loadUser(userId);
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new InvalidCredentialsException("Current password is incorrect");
        }
        if (request.getCurrentPassword().equals(request.getNewPassword())) {
            throw new BusinessException("New password must be different from the current one");
        }
        passwordValidator.validatePasswordStrength(request.getNewPassword());
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        authRepository.save(user);

        tokenBlacklistService.blacklistAllForUser(userId, jwtUtil.getAccessTtlSeconds());
        refreshTokenService.revokeAllForUser(userId);
    }

    @Override
    @Transactional
    public UserResponse updateSubUserByMaster(UUID masterFarmId, UUID subUserId, UpdateUserRequest request) {
        User subUser = loadUser(subUserId);
        if (subUser.getFarmId() == null || !subUser.getFarmId().equals(masterFarmId)) {
            throw new BusinessException("You can only update users from your own farm");
        }
        if (RoleConstants.MASTER.equals(subUser.getRole())) {
            throw new BusinessException("Cannot perform this action on MASTER user");
        }
        if (request.getRole() != null && !request.getRole().equals(subUser.getRole())) {
            throw new BusinessException("Master cannot change user role");
        }
        applyProfileUpdates(subUser, request, subUserId);
        return userMapper.toResponse(authRepository.save(subUser));
    }

    @Override
    @Transactional
    public UserResponse updateUserByAdmin(UUID userId, UpdateUserRequest request) {
        User user = loadUser(userId);
        applyProfileUpdates(user, request, userId);
        applyRoleChange(user, request.getRole(), RoleConstants.ADMIN);
        return userMapper.toResponse(authRepository.save(user));
    }

    // ---------------- Deletes ----------------

    @Override
    @Transactional
    public void deleteUserByAdmin(UUID userId) {
        User user = loadUser(userId);
        if (RoleConstants.ADMIN.equals(user.getRole())) {
            long adminCount = authRepository.countByRoleLive(RoleConstants.ADMIN);
            if (adminCount <= 1) throw new BusinessException("Cannot delete the only ADMIN user");
        }
        if (RoleConstants.MASTER.equals(user.getRole()) && user.getFarmId() != null) {
            deleteFarmAndAllUsersInternal(user.getFarmId());
        } else {
            softDeleteUserAndRelated(user);
        }
    }

    @Override
    @Transactional
    public void deleteSubUserByMaster(UUID masterFarmId, UUID subUserId) {
        User subUser = loadUser(subUserId);
        if (subUser.getFarmId() == null || !subUser.getFarmId().equals(masterFarmId)) {
            throw new BusinessException("You can only delete users from your own farm");
        }
        if (RoleConstants.MASTER.equals(subUser.getRole())) {
            throw new BusinessException("Cannot delete MASTER user");
        }
        softDeleteUserAndRelated(subUser);
    }

    @Override
    @Transactional
    public void deleteOwnAccount(UUID userId, String confirmationPhrase) {
        if (!"DELETE_MY_ACCOUNT".equals(confirmationPhrase)) {
            throw new BusinessException("Confirmation phrase is required: ?confirmation=DELETE_MY_ACCOUNT");
        }
        User user = loadUser(userId);

        if (RoleConstants.MASTER.equals(user.getRole()) && user.getFarmId() != null) {
            deleteFarmAndAllUsersInternal(user.getFarmId());
        } else if (RoleConstants.ADMIN.equals(user.getRole())) {
            long adminCount = authRepository.countByRoleLive(RoleConstants.ADMIN);
            if (adminCount <= 1) throw new BusinessException("Cannot delete the only ADMIN user");
            softDeleteUserAndRelated(user);
        } else {
            softDeleteUserAndRelated(user);
        }

        tokenBlacklistService.blacklistAllForUser(userId, jwtUtil.getAccessTtlSeconds());
        refreshTokenService.revokeAllForUser(userId);
        log.info("Account {} deleted by self-service", userId);
    }

    @Override
    @Transactional
    public void deleteFarmAndAllUsers(UUID masterFarmId, UUID farmIdToDelete) {
        if (!masterFarmId.equals(farmIdToDelete)) {
            throw new BusinessException("You can only delete your own farm");
        }
        deleteFarmAndAllUsersInternal(farmIdToDelete);
    }

    @Override
    @Transactional
    public void deleteFarmByAdmin(UUID farmId) {
        deleteFarmAndAllUsersInternal(farmId);
    }

    @Override
    @Transactional
    public int deleteAllSubUsers() {
        List<User> subUsers = authRepository.findByRoleLive(RoleConstants.SUB_USER);
        subUsers.forEach(this::softDeleteUserAndRelated);
        log.info("Admin deleted {} sub-users", subUsers.size());
        return subUsers.size();
    }

    // ---------------- Password reset ----------------

    @Override
    public ForgotPasswordResponse forgotPassword(ForgotPasswordRequest request) {
        String token = passwordResetService.initiateReset(request.getEmail());
        String msg = "If an account exists for " + request.getEmail()
                + ", a password reset link has been sent.";
        if (!exposeResetToken || token == null) {
            return new ForgotPasswordResponse(msg, null, null);
        }
        return new ForgotPasswordResponse(msg, token, resetTokenTtlSeconds);
    }

    @Override
    public void resetPassword(ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.getToken(), request.getNewPassword());
    }

    // ---------------- Internal helpers ----------------

    private void softDeleteUserAndRelated(User user) {
        personnelRepository.findByUserId(user.getId()).ifPresent(p -> {
            p.setDeletedAt(LocalDateTime.now());
            personnelRepository.save(p);
        });
        user.setDeletedAt(LocalDateTime.now());
        authRepository.save(user);
        tokenBlacklistService.blacklistAllForUser(user.getId(), jwtUtil.getAccessTtlSeconds());
        refreshTokenService.revokeAllForUser(user.getId());
        log.info("Soft-deleted user {}", user.getId());
    }

    private void deleteFarmAndAllUsersInternal(UUID farmId) {
        Farm farm = farmRepository.findByIdLive(farmId)
                .orElseThrow(() -> new BusinessException("Farm not found with ID: " + farmId));

        LocalDateTime now = LocalDateTime.now();
        List<User> farmUsers = authRepository.findByFarmIdLive(farmId);
        for (User u : farmUsers) {
            personnelRepository.findByUserId(u.getId()).ifPresent(p -> {
                p.setDeletedAt(now); personnelRepository.save(p);
            });
            u.setDeletedAt(now);
            authRepository.save(u);
            tokenBlacklistService.blacklistAllForUser(u.getId(), jwtUtil.getAccessTtlSeconds());
            refreshTokenService.revokeAllForUser(u.getId());
        }

        farm.setDeletedAt(now);
        farmRepository.save(farm);
        log.info("Soft-deleted farm {} with {} user(s)", farm.getName(), farmUsers.size());
    }

    private void applyProfileUpdates(User user, UpdateUserRequest request, UUID userId) {
        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            validateEmailUniqueness(request.getEmail(), userId);
            user.setEmail(request.getEmail());
        }
        if (request.getUsername() != null && !request.getUsername().equals(user.getUsername())) {
            validateUsernameUniqueness(request.getUsername(), userId);
            user.setUsername(request.getUsername());
        }
        if (request.getGender() != null) user.setGender(request.getGender());
        if (request.getDateOfBirth() != null) {
            if (request.getDateOfBirth().plusYears(18).isAfter(LocalDate.now())) {
                throw new BusinessException("User must be at least 18 years old.");
            }
            user.setDateOfBirth(request.getDateOfBirth());
        }
        if (request.getPhoneNumber() != null) user.setPhoneNumber(request.getPhoneNumber());
        if (request.getAddress() != null) user.setAddress(request.getAddress());
        if (request.getCity() != null) user.setCity(request.getCity());
        if (request.getState() != null) user.setState(request.getState());
        if (request.getCountry() != null) user.setCountry(request.getCountry());
        if (request.getPostalCode() != null) user.setPostalCode(request.getPostalCode());
        if (request.getPreferredCurrency() != null) user.setPreferredCurrency(request.getPreferredCurrency());
    }

    private void applyRoleChange(User user, String newRole, String requesterRole) {
        if (newRole == null || newRole.equals(user.getRole())) return;
        if (!RoleConstants.ADMIN.equals(requesterRole)) {
            throw new BusinessException("Only ADMIN can change user role");
        }
        String normalized = RoleConstants.normalizeRole(newRole);
        if (!RoleConstants.ALLOWED_ROLES.contains(normalized)) {
            throw new BusinessException("Invalid role: " + newRole);
        }
        user.setRole(normalized);
    }

    private User loadUser(UUID userId) {
        if (userId == null) throw new BusinessException("User ID cannot be null");
        return authRepository.findByIdLive(userId)
                .orElseThrow(() -> new BusinessException("User not found with ID: " + userId));
    }

    private void validateEmailNotExists(String email) {
        if (email != null && authRepository.existsByEmailLive(email)) {
            throw new BusinessException("Email already in use");
        }
    }

    private void validateEmailUniqueness(String email, UUID currentUserId) {
        authRepository.findByEmailLive(email).ifPresent(existing -> {
            if (!existing.getId().equals(currentUserId)) {
                throw new BusinessException("Email already in use");
            }
        });
    }

    private void validateUsernameNotExists(String username) {
        if (username == null || username.isBlank()) return;
        if (authRepository.findByUsernameLive(username).isPresent()) {
            throw new BusinessException("Username already taken");
        }
    }

    private void validateUsernameUniqueness(String username, UUID currentUserId) {
        if (username == null || username.isBlank()) return;
        authRepository.findByUsernameLive(username).ifPresent(existing -> {
            if (!existing.getId().equals(currentUserId)) {
                throw new BusinessException("Username already taken");
            }
        });
    }

    private FarmDetailsResponse buildFarmDetails(Farm farm) {
        UUID farmId = farm.getId();
        List<User> users = authRepository.findByFarmIdLive(farmId);

        FarmMapper.FarmTotals totals = new FarmMapper.FarmTotals(
                incomeRepository.countByFarmId(farmId),
                expenseRepository.countByFarmId(farmId),
                investmentRepository.countByFarmId(farmId),
                transactionRepository.countByFarmId(farmId),
                incomeRepository.sumAmountByFarmIdOrZero(farmId),
                expenseRepository.sumAmountByFarmIdOrZero(farmId),
                investmentRepository.sumAmountByFarmIdOrZero(farmId)
        );

        Map<UUID, FarmMapper.UserActivity> activityByUser = new HashMap<>();
        Map<UUID, long[]> incomeCounts     = toUuidLongMap(incomeRepository.countByFarmIdGroupedByCreator(farmId));
        Map<UUID, long[]> expenseCounts    = toUuidLongMap(expenseRepository.countByFarmIdGroupedByCreator(farmId));
        Map<UUID, long[]> investmentCounts = toUuidLongMap(investmentRepository.countByFarmIdGroupedByCreator(farmId));
        Map<UUID, java.math.BigDecimal> incomeSums     = toUuidDecimalMap(incomeRepository.sumGroupedByCreator(farmId));
        Map<UUID, java.math.BigDecimal> expenseSums    = toUuidDecimalMap(expenseRepository.sumGroupedByCreator(farmId));
        Map<UUID, java.math.BigDecimal> investmentSums = toUuidDecimalMap(investmentRepository.sumGroupedByCreator(farmId));

        for (User u : users) {
            long[] ic = incomeCounts.getOrDefault(u.getId(), new long[]{0L});
            long[] ec = expenseCounts.getOrDefault(u.getId(), new long[]{0L});
            long[] vc = investmentCounts.getOrDefault(u.getId(), new long[]{0L});
            activityByUser.put(u.getId(), new FarmMapper.UserActivity(
                    ic[0], ec[0], vc[0],
                    incomeSums.getOrDefault(u.getId(), java.math.BigDecimal.ZERO),
                    expenseSums.getOrDefault(u.getId(), java.math.BigDecimal.ZERO),
                    investmentSums.getOrDefault(u.getId(), java.math.BigDecimal.ZERO)
            ));
        }

        return farmMapper.toDetails(farm, users, totals, activityByUser);
    }

    private static Map<UUID, long[]> toUuidLongMap(List<Object[]> rows) {
        Map<UUID, long[]> m = new HashMap<>();
        for (Object[] r : rows) {
            if (r[0] != null) m.put((UUID) r[0], new long[]{ ((Number) r[1]).longValue() });
        }
        return m;
    }

    private static Map<UUID, java.math.BigDecimal> toUuidDecimalMap(List<Object[]> rows) {
        Map<UUID, java.math.BigDecimal> m = new HashMap<>();
        for (Object[] r : rows) {
            if (r[0] != null && r[1] != null) m.put((UUID) r[0], (java.math.BigDecimal) r[1]);
        }
        return m;
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}