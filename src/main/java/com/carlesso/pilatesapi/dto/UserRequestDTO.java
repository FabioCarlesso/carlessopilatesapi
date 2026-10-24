package com.carlesso.pilatesapi.dto;

import com.carlesso.pilatesapi.entity.enums.Role;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record UserRequestDTO(
        @NotBlank String name,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8) String password,
        // PACIENTE é recusado com 422 (#173): o schema anuncia só os papéis que GET /users/roles oferece.
        @ArraySchema(
                        schema =
                                @Schema(
                                        types = {"string"},
                                        allowableValues = {"ADMIN", "PROFISSIONAL", "RECEPCAO", "FINANCEIRO"}))
                @NotEmpty(message = "informe ao menos um papel") Set<@NotNull(message = "papel não pode ser nulo") Role> roles) {}
