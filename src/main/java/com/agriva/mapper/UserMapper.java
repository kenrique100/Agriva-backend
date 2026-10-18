package com.agriva.mapper;

import com.agriva.model.dto.auth.UserListResponse;
import com.agriva.model.dto.auth.UserResponse;
import com.agriva.model.entity.User;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class UserMapper {

    public UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(), user.getEmail(), user.getUsername(), user.getRole(),
                user.getFarmId(), user.getCreatedAt(), user.getUpdatedAt());
    }

    public UserListResponse toListResponse(User user) {
        return new UserListResponse(
                user.getId(), user.getEmail(), user.getUsername(), user.getRole(), user.getFarmId());
    }

    public List<UserListResponse> toListResponse(List<User> users) {
        return users.stream().map(this::toListResponse).toList();
    }
}