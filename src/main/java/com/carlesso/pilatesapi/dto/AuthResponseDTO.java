package com.carlesso.pilatesapi.dto;

public record AuthResponseDTO(String accessToken, String tokenType, UserResponseDTO user) {
    public static AuthResponseDTO bearer(String accessToken, UserResponseDTO user) {
        return new AuthResponseDTO(accessToken, "Bearer", user);
    }
}
