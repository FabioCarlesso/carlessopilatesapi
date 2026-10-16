package com.carlesso.pilatesapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
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
                Long version) {}
