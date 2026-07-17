package com.carlesso.pilatesapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserAlterarSenhaRequestDTO(
        @NotBlank String senhaAtual,
        @NotBlank @Size(min = 8) String novaSenha,
        @NotBlank String confirmacaoNovaSenha) {}
