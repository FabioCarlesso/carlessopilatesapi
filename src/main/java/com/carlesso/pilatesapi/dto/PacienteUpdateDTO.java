package com.carlesso.pilatesapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record PacienteUpdateDTO(
        String nome,
        @Email String email,
        String telefone,
        LocalDate dataNascimento,
        @Valid EnderecoDTO endereco,
        @Schema(
                        description =
                                "Versão recebida no GET; divergente da atual retorna 409 concurrent-modification. Ausente: sem checagem (modo legado)",
                        example = "0")
                Long version,
        @Schema(
                        description =
                                "CPF do paciente. Ausente ou vazio mantém o atual; CPF de outro paciente retorna 409."
                                        + " Permite completar o cadastro de quem foi importado sem CPF",
                        example = "11122233344")
                String cpf,
        @Schema(description = "O paciente deseja CPF/CNPJ na NFSE. Ausente: mantém o atual", example = "true")
                Boolean cpfNaNota,
        @Schema(
                        description =
                                "CPF/CNPJ do tomador alternativo (11 ou 14 dígitos, com ou sem máscara). Ausente: mantém;"
                                        + " vazio: remove o tomador alternativo",
                        example = "12345678000199")
                String notaCpfCnpj,
        @Schema(description = "Nome do tomador alternativo. Ausente: mantém; vazio: remove", example = "Empresa Ltda")
                @Size(max = 255) String notaNome) {}
