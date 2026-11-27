package com.carlesso.pilatesapi.dto;

import com.carlesso.pilatesapi.entity.enums.CondicaoSaude;
import com.carlesso.pilatesapi.entity.enums.NivelAtividadeFisica;
import com.carlesso.pilatesapi.entity.enums.ObjetivoTratamento;
import com.carlesso.pilatesapi.entity.enums.RegiaoDor;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import java.util.List;

public record AnamneseUpdateDTO(
        @Pattern(regexp = ".*\\S.*", message = "queixaPrincipal não pode ser vazio") String queixaPrincipal,
        String historicoDoencas,
        String historicoCirurgias,
        String historicoLesoes,
        String medicamentosUso,
        String alergias,
        String nivelAtividadeFisica,
        String restricoesMedicas,
        @Pattern(regexp = ".*\\S.*", message = "objetivos não pode ser vazio") String objetivos,
        String observacoes,
        @ArraySchema(
                        arraySchema =
                                @Schema(
                                        description =
                                                "Ausente mantém a lista atual; lista vazia a limpa. Repetidos são descartados"),
                        schema = @Schema(implementation = RegiaoDor.class))
                List<String> regioesDor,
        String regioesDorComplemento,
        @ArraySchema(
                        arraySchema =
                                @Schema(
                                        description =
                                                "Ausente mantém a lista atual; lista vazia a limpa. Repetidos são descartados"),
                        schema = @Schema(implementation = CondicaoSaude.class))
                List<String> condicoesSaude,
        String condicoesSaudeComplemento,
        @Schema(
                        description = "Ausente mantém a seleção atual; texto vazio a limpa",
                        implementation = NivelAtividadeFisica.class)
                String nivelAtividadeFisicaOpcao,
        String nivelAtividadeFisicaComplemento,
        @ArraySchema(
                        arraySchema =
                                @Schema(
                                        description =
                                                "Ausente mantém a lista atual; lista vazia a limpa. Repetidos são descartados"),
                        schema = @Schema(implementation = ObjetivoTratamento.class))
                List<String> objetivosOpcoes,
        String objetivosComplemento,
        @Schema(
                        description =
                                "Versão recebida no GET; divergente da atual retorna 409 concurrent-modification. Ausente: sem checagem (modo legado)",
                        example = "0")
                Long version)
        implements CamposAnamneseOpcoesDTO {}
