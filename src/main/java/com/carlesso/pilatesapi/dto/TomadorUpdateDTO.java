package com.carlesso.pilatesapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/** Atualização parcial: campo ausente mantém o valor; nos opcionais, vazio apaga. */
public record TomadorUpdateDTO(
        @Size(max = 255) String nome,
        @Schema(description = "CPF ou CNPJ, com ou sem máscara (11 ou 14 dígitos)") String cpfCnpj,
        @Email String email,
        @Size(max = 30) String telefone,
        @Size(max = 500) String observacoes,
        @Schema(
                        description =
                                "Versão recebida no GET; divergente da atual retorna 409 concurrent-modification. Ausente: sem checagem",
                        example = "0")
                Long version) {}
