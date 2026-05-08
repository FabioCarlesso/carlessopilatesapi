package com.carlesso.pilatesapi.dto;

import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.time.LocalTime;

public record SessaoPilatesUpdateDTO(
        LocalDate data,
        LocalTime horario,
        String local,
        @Positive Integer duracaoMinutos,
        String observacoes
) {}
