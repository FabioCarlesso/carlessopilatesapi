package com.carlesso.pilatesapi.dto;

import com.carlesso.pilatesapi.entity.enums.AchadoPostural;
import com.carlesso.pilatesapi.entity.enums.GrauAlteracao;
import com.carlesso.pilatesapi.entity.enums.PadraoRespiratorio;
import com.carlesso.pilatesapi.entity.enums.RegiaoDor;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

public record ReavaliacaoRequestDTO(
        @NotNull Long pacienteId,
        Long avaliacaoFisioterapeuticaId,
        Long planoTratamentoId,
        @NotNull LocalDate dataReavaliacao,
        String comparativoAvaliacaoAnterior,
        String evolucaoDor,
        String evolucaoForca,
        String evolucaoMobilidade,
        String evolucaoFuncional,
        String objetivosAlcancados,
        String pontosAtencao,
        String ajustesRecomendados,
        String observacoesGerais,
        @ArraySchema(
                        arraySchema = @Schema(description = "Repetidos são descartados"),
                        schema = @Schema(implementation = RegiaoDor.class))
                List<String> regioesDor,
        String regioesDorComplemento,
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
