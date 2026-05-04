package com.carlesso.pilatesapi.dto;

import com.carlesso.pilatesapi.entity.enums.StatusSessao;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.time.LocalTime;

public record SessaoPilatesUpdateDTO(
        LocalDate data,
        LocalTime horario,
        String local,
        @Positive Integer duracaoMinutos,
        StatusSessao status,
        String observacoes,
        String evolucao
) {}
