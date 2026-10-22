package com.agriva.service.impl;

import com.agriva.exception.BusinessException;
import com.agriva.exception.InvalidCredentialsException;
import com.agriva.mapper.FarmMapper;
import com.agriva.mapper.UserMapper;
import com.agriva.model.dto.auth.*;
import com.agriva.model.dto.farm.FarmDetailsResponse;
import com.agriva.model.dto.farm.FarmListResponse;
import com.agriva.model.entity.*;
import com.agriva.repository.*;
import com.agriva.security.JwtUtil;
import com.agriva.security.RoleConstants;
import com.agriva.service.AuthService;
import com.agriva.service.PasswordResetService;
import com.agriva.service.TokenBlacklistService;
import com.agriva.utils.PasswordValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

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
    private final PasswordResetService passwordResetService;
    private final TokenBlacklistService tokenBlacklistService;
    private final UserMapper userMapper;
    private final FarmMapper farmMapper;

    @Value("${agriva.security.password-reset.expose-token:false}")
    private boolean exposeResetToken;

    @Value("${agriva.security.password-reset.ttl-seconds:900}")
    private long resetTokenTtlSeconds;

    @Override
    public String login(LoginRequest request) {
        User user = authRepository.findByEmailOrUsername(
                        request.getEmailOrUsername(), request.getEmailOrUsername())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credentials"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            log.warn("Failed login attempt for {}", request.getEmailOrUsername());
            throw new InvalidCredentialsException("Invalid credentials");
        }
        log.info("Successful login for {} with role {}", user.getEmail(), user.getRole());
        return jwtUtil.generateToken(user);
    }

    @Override
    public UserDetails getUserDetails(String emailOrUsername) {
        User user = authRepository.findByEmailOrUsername(emailOrUsername, emailOrUsername)
                .orElseThrow(() -> new BusinessException("User not found"));

        String farmName = null;
        if (user.getFarmId() != null) {
            farmName = farmRepository.findById(user.getFarmId())
                    .map(Farm::getName)
                    .orElse(null);
        }
        return new UserDetails(
                user.getEmail(), user.getUsername(), user.getRole(),
                user.getFarmId(), farmName, user.getPreferredCurrency());
    }

    @Override
    public TokenValidationResponse validateToken(String authHeader) {
        String token = stripBearer(authHeader);
        if (!jwtUtil.validateToken(token)) {
            throw new InvalidCredentialsException("Invalid token");
        }
        UUID userId = jwtUtil.extractUserId(token);
        String username = userId == null ? null
                : authRepository.findById(userId).map(User::getUsername).orElse(null);
        return new TokenValidationResponse(
                userId, jwtUtil.extractFarmId(token),
                jwtUtil.extractRole(token), username);
    }

    @Override
    public void logout(String authHeader) {
        tokenBlacklistService.blacklist(stripBearer(authHeader));
    }

    @Override
    @Transactional
    public AuthResponse registerMaster(MasterRegisterRequest request) {
        PasswordValidator.validatePasswordStrength(request.getPassword());
        validateEmailNotExists(request.getEmail());
        validateUsernameNotExists(request.getUsername());

        if (request.getDateOfBirth().plusYears(18).isAfter(LocalDate.now())) {
            throw new BusinessException("Master user must be at least 18 years old.");
        }
        if (farmRepository.existsByName(request.getFarmName())) {
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
        String token = jwtUtil.generateToken(master);
        return new AuthResponse(
                token,
                master.getEmail(),
                master.getUsername(),
                master.getRole(),
                master.getFarmId(),
                farm.getName(),
                master.getPreferredCurrency()
        );
    }

    @Override
    @Transactional
    public String registerSubUser(SubUserRegisterRequest request) {
        PasswordValidator.validatePasswordStrength(request.getPassword());
        validateEmailNotExists(request.getEmail());
        validateUsernameNotExists(request.getUsername());

        if (request.getDateOfBirth().plusYears(18).isAfter(LocalDate.now())) {
            throw new BusinessException("Sub-user must be at least 18 years old.");
        }
        boolean farmHasMaster = authRepository.existsByFarmIdAndRole(
                request.getFarmId(), RoleConstants.MASTER);
        if (!farmHasMaster) {
            throw new BusinessException("Farm not found or invalid");
        }

        User subUser = authRepository.save(User.builder()
                .email(request.getEmail())
                .username(trimToNull(request.getUsername()))
                .password(passwordEncoder.encode(request.getPassword()))
                .role(RoleConstants.SUB_USER)
                .farmId(request.getFarmId())
                .gender(request.getGender())
                .dateOfBirth(request.getDateOfBirth())
                .phoneNumber(request.getPhoneNumber())
                .address(request.getAddress())
                .build());

        createPersonnelForUser(
                subUser,
                request.getName(),
                request.getDepartment() != null ? request.getDepartment() : Department.ADMINISTRATION
        );

        log.info("Sub-user registered: {} for farm {}", subUser.getEmail(), subUser.getFarmId());
        return "Sub-user registered: " + subUser.getEmail();
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

    @Override public UserResponse getUserById(UUID userId) {
        return userMapper.toResponse(loadUser(userId));
    }
    @Override public List<UserListResponse> getUsersByFarmId(UUID farmId) {
        return userMapper.toListResponse(authRepository.findByFarmId(farmId));
    }
    @Override public List<UserListResponse> getAllUsers() {
        return userMapper.toListResponse(authRepository.findAll());
    }

    @Override
    public FarmDetailsResponse getFarmDetails(UUID farmId) {
        Farm farm = farmRepository.findById(farmId)
                .orElseThrow(() -> new BusinessException("Farm not found with ID: " + farmId));
        return buildFarmDetails(farm);
    }

    @Override
    public FarmListResponse getAllFarms() {
        List<FarmDetailsResponse> details = new ArrayList<>();
        for (Farm f : farmRepository.findAll()) {
            details.add(buildFarmDetails(f));
        }
        return farmMapper.toList(details);
    }

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
        PasswordValidator.validatePasswordStrength(request.getNewPassword());
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        authRepository.save(user);
    }

    @Override
    @Transactional
    public void resetOwnPassword(UUID userId, MeResetPasswordRequest request) {
        User user = loadUser(userId);
        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new BusinessException("New password must be different from the current one");
        }
        PasswordValidator.validatePasswordStrength(request.getNewPassword());
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        authRepository.save(user);
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

    @Override
    @Transactional
    public void deleteUserByAdmin(UUID userId) {
        User user = loadUser(userId);
        if (RoleConstants.ADMIN.equals(user.getRole())) {
            long adminCount = authRepository.findByRole(RoleConstants.ADMIN).size();
            if (adminCount <= 1) {
                throw new BusinessException("Cannot delete the only ADMIN user");
            }
        }
        if (RoleConstants.MASTER.equals(user.getRole()) && user.getFarmId() != null) {
            deleteFarmAndAllUsersInternal(user.getFarmId());
        } else {
            authRepository.delete(user);
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
        authRepository.delete(subUser);
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
        List<User> subUsers = authRepository.findByRole(RoleConstants.SUB_USER);
        int count = subUsers.size();
        authRepository.deleteAll(subUsers);
        log.info("Admin deleted {} sub-users", count);
        return count;
    }

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
        return authRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("User not found with ID: " + userId));
    }

    private void validateEmailNotExists(String email) {
        if (email != null && authRepository.existsByEmail(email)) {
            throw new BusinessException("Email already in use");
        }
    }

    private void validateEmailUniqueness(String email, UUID currentUserId) {
        authRepository.findByEmail(email).ifPresent(existing -> {
            if (!existing.getId().equals(currentUserId)) {
                throw new BusinessException("Email already in use");
            }
        });
    }

    private void validateUsernameNotExists(String username) {
        if (username == null || username.isBlank()) return;
        if (authRepository.findByUsername(username).isPresent()) {
            throw new BusinessException("Username already taken");
        }
    }

    private void validateUsernameUniqueness(String username, UUID currentUserId) {
        if (username == null || username.isBlank()) return;
        authRepository.findByUsername(username).ifPresent(existing -> {
            if (!existing.getId().equals(currentUserId)) {
                throw new BusinessException("Username already taken");
            }
        });
    }

    private void deleteFarmAndAllUsersInternal(UUID farmId) {
        Farm farm = farmRepository.findById(farmId)
                .orElseThrow(() -> new BusinessException("Farm not found with ID: " + farmId));
        List<User> farmUsers = authRepository.findByFarmId(farmId);
        authRepository.deleteAll(farmUsers);
        farmRepository.delete(farm);
        log.info("Deleted farm {} with {} users", farm.getName(), farmUsers.size());
    }

    private FarmDetailsResponse buildFarmDetails(Farm farm) {
        UUID farmId = farm.getId();
        List<User> users = authRepository.findByFarmId(farmId);

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
        for (User u : users) {
            activityByUser.put(u.getId(), new FarmMapper.UserActivity(
                    incomeRepository.countByFarmIdAndCreatedByUserId(farmId, u.getId()),
                    expenseRepository.countByFarmIdAndCreatedByUserId(farmId, u.getId()),
                    investmentRepository.countByFarmIdAndCreatedByUserId(farmId, u.getId()),
                    incomeRepository.sumAmountByFarmIdAndUserOrZero(farmId, u.getId()),
                    expenseRepository.sumAmountByFarmIdAndUserOrZero(farmId, u.getId()),
                    investmentRepository.sumAmountByFarmIdAndUserOrZero(farmId, u.getId())
            ));
        }
        return farmMapper.toDetails(farm, users, totals, activityByUser);
    }

    private String stripBearer(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new InvalidCredentialsException("Missing or invalid Authorization header");
        }
        return authHeader.substring(7);
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}