package com.agriva.utils;

import com.agriva.model.dto.*;
import com.agriva.model.entity.User;
import com.agriva.repository.AuthRepository;
import com.agriva.security.JwtUtil;
import com.agriva.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ResponseBuilderUtils {

    private final AuthRepository authRepository;

    public UserDetails buildUserDetails(User user) {
        return new UserDetails(user.getEmail(), user.getRole(), user.getFarmId());
    }

    public TokenValidationResponse buildTokenValidationResponse(String token, JwtUtil jwtUtil) {
        UUID userId = jwtUtil.extractUserId(token);
        String username = null;
        if (userId != null) {
            User user = authRepository.findById(userId).orElse(null);
            if (user != null) username = user.getUsername();
        }
        return new TokenValidationResponse(
                userId,
                jwtUtil.extractFarmId(token),
                jwtUtil.extractRole(token),
                username
        );
    }

    public AuthResponse buildAuthResponse(User user, JwtUtil jwtUtil) {
        String token = jwtUtil.generateToken(user);
        return new AuthResponse(token, user.getEmail(), user.getRole(), user.getFarmId());
    }

    public String buildSubUserResponse(String email) {
        return "Sub-user registered: " + email;
    }

    public FarmDetailsResponse buildFarmDetailsResponse(FarmUtils.FarmData farmData,
                                                        User master,
                                                        List<User> subUsers) {
        MasterDetails masterDetails = new MasterDetails(
                master.getId(),
                master.getEmail(),
                master.getUsername(),
                master.getRole(),
                master.getCreatedAt(),
                master.getUpdatedAt()
        );

        List<SubUserDetails> subUserDetails = subUsers.stream()
                .map(u -> new SubUserDetails(
                        u.getId(),
                        u.getEmail(),
                        u.getUsername(),
                        u.getRole(),
                        u.getCreatedAt(),
                        u.getUpdatedAt()
                ))
                .collect(Collectors.toList());

        int totalUsers = 1 + subUsers.size();

        return new FarmDetailsResponse(
                farmData.farmId(),
                farmData.farmName(),
                farmData.farm().getCreatedAt(),
                farmData.farm().getUpdatedAt(),
                masterDetails,
                subUserDetails,
                totalUsers,
                subUsers.size()
        );
    }

    public FarmsListResponse buildFarmsListResponse(List<FarmUtils.FarmData> allFarms,
                                                    AuthService authService) {
        List<FarmDetailsResponse> farmDetailsList = new ArrayList<>();
        int totalUsers = 0;
        int totalMasters = 0;
        int totalSubUsers = 0;

        for (FarmUtils.FarmData farmData : allFarms) {
            FarmDetailsResponse farmDetails = authService.getFarmDetails(farmData.farmId());
            farmDetailsList.add(farmDetails);
            totalUsers += farmDetails.getTotalUsers();
            totalMasters++;
            totalSubUsers += farmDetails.getTotalSubUsers();
        }

        return new FarmsListResponse(
                farmDetailsList,
                allFarms.size(),
                totalUsers,
                totalMasters,
                totalSubUsers
        );
    }

    public UserResponse buildUserResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getUsername(),
                user.getRole(),
                user.getFarmId(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }

    public List<UserListResponse> buildUserListResponse(List<User> users) {
        return users.stream()
                .map(u -> new UserListResponse(
                        u.getId(),
                        u.getEmail(),
                        u.getUsername(),       // NEW
                        u.getRole(),
                        u.getFarmId()
                ))
                .collect(Collectors.toList());
    }
}