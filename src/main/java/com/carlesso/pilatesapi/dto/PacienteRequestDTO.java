package com.carlesso.pilatesapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
        @Valid EnderecoDTO endereco,
        @Schema(description = "O paciente deseja CPF/CNPJ na NFSE. Ausente: false", example = "true") Boolean cpfNaNota,
        @Schema(
                        description =
                                "Tomador da nota (responsável ou empresa, ver /tomadores). Ausente: a nota sai no CPF do"
                                        + " próprio paciente. Depois do cadastro, o vínculo muda por PATCH /pacientes/{id}/tomador",
                        example = "3")
                Long tomadorId) {}
