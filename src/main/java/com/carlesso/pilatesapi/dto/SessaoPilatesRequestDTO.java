package com.carlesso.pilatesapi.dto;

import com.carlesso.pilatesapi.entity.enums.TipoSessao;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.time.LocalTime;

public record SessaoPilatesRequestDTO(
        @NotNull Long pacienteId,
        Long profissionalId,
        Long planoTratamentoId,
        @NotNull TipoSessao tipo,
        @NotNull LocalDate data,
        LocalTime horario,
        String local,
        @Positive Integer duracaoMinutos,
        String observacoes
) {}
