package com.carlesso.pilatesapi.dto;

import com.carlesso.pilatesapi.entity.enums.TipoContrato;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ProfissionalRequestDTO(
        @NotBlank String nome,
        @NotBlank @Email String email,
        @NotBlank String cpf,
        String telefone,
        @NotNull TipoContrato tipoContrato,
        @NotNull @DecimalMin("0.01") @DecimalMax("100.00") BigDecimal percentualPagamentoAula,
        @NotNull LocalDate dataInicio,
        @Schema(
                        description =
                                "Número de registro no CREFITO. Obrigatório (#250): ausente ou em branco retorna 400",
                        example = "123456-F")
                @Size(max = 30) String numeroRegistro) {}
