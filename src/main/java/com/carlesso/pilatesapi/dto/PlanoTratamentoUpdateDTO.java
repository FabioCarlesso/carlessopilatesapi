package com.carlesso.pilatesapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;

public record PlanoTratamentoUpdateDTO(
        LocalDate dataInicio,
        LocalDate dataFimPrevista,
        @Pattern(regexp = ".*\\S.*", message = "objetivosTratamento não pode ser vazio") String objetivosTratamento,
        String intervencoesPlanejadas,
        @Positive Integer numeroSessoesPrevistas,
        String frequenciaSessoes,
        String responsavelTratamento,
        String observacoes,
        @Schema(
                        description =
                                "Versão recebida no GET; divergente da atual retorna 409 concurrent-modification. Ausente: sem checagem (modo legado)",
                        example = "0")
                Long version) {}
