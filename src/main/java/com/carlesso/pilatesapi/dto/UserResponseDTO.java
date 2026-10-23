package com.carlesso.pilatesapi.dto;

import com.carlesso.pilatesapi.entity.User;
import com.carlesso.pilatesapi.entity.enums.Role;
import java.util.List;

public record UserResponseDTO(Long id, String name, String email, List<Role> roles, boolean ativo, Long version) {
    public static UserResponseDTO from(User user) {
        return new UserResponseDTO(
                user.getId(),
                user.getName(),
                user.getEmail(),
                // Ordem da declaração do enum, para a resposta não variar com a ordem do Set.
                user.getRoles().stream().sorted().toList(),
                user.isAtivo(),
                user.getVersion());
    }
}
