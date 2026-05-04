package com.carlesso.pilatesapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;

public record PlanoTratamentoRequestDTO(
        @NotNull Long pacienteId,
        @NotNull LocalDate dataInicio,
        LocalDate dataFimPrevista,
        @NotBlank String objetivosTratamento,
        String intervencoesPlanejadas,
        @Positive Integer numeroSessoesPrevistas,
        String frequenciaSessoes,
        String responsavelTratamento,
        String observacoes
) {}
