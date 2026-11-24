package com.carlesso.pilatesapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record PacienteRequestDTO(
        @NotBlank String nome,
        @Email String email,
        String cpf,
        String telefone,
        LocalDate dataNascimento,
        @Valid EnderecoDTO endereco,
        @Schema(description = "O paciente deseja CPF/CNPJ na NFSE. Ausente: false", example = "true") Boolean cpfNaNota,
        @Schema(
                        description =
                                "CPF/CNPJ do tomador alternativo da nota (responsável ou empresa), com ou sem máscara;"
                                        + " precisa ter 11 ou 14 dígitos. Ausente: a nota sai no CPF do paciente",
                        example = "12345678000199")
                String notaCpfCnpj,
        @Schema(description = "Nome do tomador alternativo; exige notaCpfCnpj", example = "Empresa Exemplo Ltda")
                @Size(max = 255) String notaNome) {}
