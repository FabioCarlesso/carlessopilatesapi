package com.carlesso.pilatesapi.dto;

public record AuthResponseDTO(String accessToken, String refreshToken, String tokenType, UserResponseDTO user) {
    public static AuthResponseDTO bearer(String accessToken, String refreshToken, UserResponseDTO user) {
        return new AuthResponseDTO(accessToken, refreshToken, "Bearer", user);
    }
}
