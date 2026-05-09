package com.carlesso.pilatesapi.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

public record PacienteRequestDTO(
        @NotBlank String nome,
        @Email String email,
        String cpf,
        String telefone,
        LocalDate dataNascimento,
        @Valid EnderecoDTO endereco
) {}
