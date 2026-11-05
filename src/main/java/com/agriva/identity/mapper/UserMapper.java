package com.agriva.identity.mapper;

import com.agriva.identity.models.dto.response.UserListResponse;
import com.agriva.identity.models.dto.response.UserResponse;
import com.agriva.identity.models.entity.User;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class UserMapper {

    public UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(), user.getEmail(), user.getUsername(), user.getRole(),
                user.getFarmId(), user.getGender(), user.getDateOfBirth(), user.getPhoneNumber(),
                user.getAddress(), user.getCity(), user.getState(), user.getCountry(),
                user.getPostalCode(), user.getPreferredCurrency(),
                user.getCreatedAt(), user.getUpdatedAt());
    }

    public UserListResponse toListResponse(User user) {
        return new UserListResponse(
                user.getId(), user.getEmail(), user.getUsername(), user.getRole(),
                user.getFarmId(), user.getGender(), user.getPhoneNumber());
    }

    public List<UserListResponse> toListResponse(List<User> users) {
        return users.stream().map(this::toListResponse).toList();
    }
}