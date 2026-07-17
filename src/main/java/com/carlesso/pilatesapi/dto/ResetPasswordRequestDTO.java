package com.carlesso.pilatesapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequestDTO(
        @NotBlank String token, @NotBlank @Size(min = 8) String novaSenha, @NotBlank String confirmacaoNovaSenha) {}
