package com.carlesso.pilatesapi.dto;

import com.carlesso.pilatesapi.entity.User;
import com.carlesso.pilatesapi.entity.enums.Role;

public record UserResponseDTO(
        Long id,
        String name,
        String email,
        Role role
) {
    public static UserResponseDTO from(User user) {
        return new UserResponseDTO(user.getId(), user.getName(), user.getEmail(), user.getRole());
    }
}
