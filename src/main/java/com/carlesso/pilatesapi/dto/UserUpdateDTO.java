package com.carlesso.pilatesapi.dto;

import com.carlesso.pilatesapi.entity.enums.Role;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record UserUpdateDTO(
        @Size(min = 1) String name,
        @Email String email,
        @Size(min = 8) String password,
        @Schema(description = "Substitui o conjunto inteiro de papéis. Ausente: papéis inalterados")
                @ArraySchema(
                        schema =
                                @Schema(
                                        types = {"string"},
                                        allowableValues = {"ADMIN", "PROFISSIONAL", "RECEPCAO", "FINANCEIRO"}))
                @Size(min = 1, message = "informe ao menos um papel") Set<@NotNull(message = "papel não pode ser nulo") Role> roles,
        @Schema(
                        description =
                                "Versão recebida no GET; divergente da atual retorna 409 concurrent-modification. Ausente: sem checagem (modo legado)",
                        example = "0")
                Long version) {}
