package com.carlesso.pilatesapi.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Regras de agenda das aulas (issue #106).
 *
 * @param duracaoAulaMinutos duração fixa de toda aula; duas aulas do mesmo profissional disputam o mesmo horário
 *     quando seus inícios distam menos que isso
 * @param capacidadePorHorario quantas aulas um profissional atende ao mesmo tempo
 */
@Validated
@ConfigurationProperties(prefix = "app.agenda")
public record AgendaProperties(
        // Acima de 12h a janela de conflito daria a volta no relógio pelos dois lados.
        @Min(1) @Max(720) int duracaoAulaMinutos, @Min(1) int capacidadePorHorario) {}
