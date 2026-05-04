package com.carlesso.pilatesapi.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;

public record PlanoTratamentoUpdateDTO(
        LocalDate dataInicio,
        LocalDate dataFimPrevista,
        @Pattern(regexp = ".*\\S.*", message = "objetivosTratamento não pode ser vazio")
        String objetivosTratamento,
        String intervencoesPlanejadas,
        @Positive Integer numeroSessoesPrevistas,
        String frequenciaSessoes,
        String responsavelTratamento,
        String observacoes
) {}
