package com.carlesso.pilatesapi.dto;

import com.carlesso.pilatesapi.entity.enums.CondicaoSaude;
import com.carlesso.pilatesapi.entity.enums.NivelAtividadeFisica;
import com.carlesso.pilatesapi.entity.enums.ObjetivoTratamento;
import com.carlesso.pilatesapi.entity.enums.RegiaoDor;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record AnamneseRequestDTO(
        @NotNull Long pacienteId,
        @NotBlank String queixaPrincipal,
        String historicoDoencas,
        String historicoCirurgias,
        String historicoLesoes,
        String medicamentosUso,
        String alergias,
        String nivelAtividadeFisica,
        String restricoesMedicas,
        @NotBlank String objetivos,
        String observacoes,
        @ArraySchema(
                        arraySchema = @Schema(description = "Repetidos são descartados"),
                        schema = @Schema(implementation = RegiaoDor.class))
                List<String> regioesDor,
        String regioesDorComplemento,
        @ArraySchema(
                        arraySchema = @Schema(description = "Repetidos são descartados"),
                        schema = @Schema(implementation = CondicaoSaude.class))
                List<String> condicoesSaude,
        String condicoesSaudeComplemento,
        @Schema(implementation = NivelAtividadeFisica.class) String nivelAtividadeFisicaOpcao,
        String nivelAtividadeFisicaComplemento,
        @ArraySchema(
                        arraySchema = @Schema(description = "Repetidos são descartados"),
                        schema = @Schema(implementation = ObjetivoTratamento.class))
                List<String> objetivosOpcoes,
        String objetivosComplemento)
        implements CamposAnamneseOpcoesDTO {}
