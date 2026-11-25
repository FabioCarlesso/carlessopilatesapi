package com.carlesso.pilatesapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TomadorRequestDTO(
        @Schema(description = "Nome ou razão social", example = "Empresa Exemplo Ltda") @NotBlank @Size(max = 255) String nome,
        @Schema(
                        description = "CPF ou CNPJ, com ou sem máscara; gravado só com dígitos (11 ou 14)",
                        example = "11.222.333/0001-81")
                @NotBlank String cpfCnpj,
        @Schema(example = "financeiro@empresa.com") @Email String email,
        @Schema(example = "45999990000") @Size(max = 30) String telefone,
        @Schema(description = "Observações livres para a emissão", example = "Nota até o dia 10") @Size(max = 500) String observacoes) {}
