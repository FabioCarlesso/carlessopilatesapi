package com.carlesso.pilatesapi.dto;

import com.carlesso.pilatesapi.entity.enums.AchadoPostural;
import com.carlesso.pilatesapi.entity.enums.GrauAlteracao;
import com.carlesso.pilatesapi.entity.enums.PadraoRespiratorio;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

public record AvaliacaoFisioterapeuticaRequestDTO(
        @NotNull Long pacienteId,
        @NotNull LocalDate dataAvaliacao,
        @NotBlank String queixaFuncional,
        String avaliacaoPostural,
        String mobilidadeArticular,
        String forcaMuscular,
        String flexibilidade,
        String equilibrio,
        String coordenacaoMotora,
        String padraoRespiratorio,
        @NotNull @Min(0) @Max(10) Integer escalaDor,
        String testesFuncionaisRealizados,
        @NotBlank String diagnosticoFisioterapeutico,
        String observacoesGerais,
        @ArraySchema(
                        arraySchema = @Schema(description = "Repetidos são descartados"),
                        schema = @Schema(implementation = AchadoPostural.class))
                List<String> achadosPosturais,
        String achadosPosturaisComplemento,
        @Schema(implementation = PadraoRespiratorio.class) String padraoRespiratorioOpcao,
        String padraoRespiratorioComplemento,
        @Schema(implementation = GrauAlteracao.class) String equilibrioOpcao,
        String equilibrioComplemento,
        @Schema(implementation = GrauAlteracao.class) String coordenacaoMotoraOpcao,
        String coordenacaoMotoraComplemento,
        @ArraySchema(arraySchema = @Schema(description = "Cada teste uma vez, com resultado"))
                List<TesteFuncionalRealizadoDTO> testesFuncionais,
        String testesFuncionaisComplemento)
        implements CamposExameFisicoOpcoesDTO {}
