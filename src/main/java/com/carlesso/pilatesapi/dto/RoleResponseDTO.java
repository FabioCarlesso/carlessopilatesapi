package com.carlesso.pilatesapi.dto;

import com.carlesso.pilatesapi.entity.enums.Role;

public record RoleResponseDTO(String value, String label) {
    public static RoleResponseDTO from(Role role) {
        return new RoleResponseDTO(role.name(), role.getLabel());
    }
}
