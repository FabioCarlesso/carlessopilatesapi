package com.carlesso.pilatesapi.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import java.time.LocalDate;

public record PacienteUpdateDTO(
        String nome,
        @Email String email,
        String telefone,
        LocalDate dataNascimento,
        @Valid EnderecoDTO endereco
) {}
