package com.carlesso.pilatesapi.dto;

import com.carlesso.pilatesapi.entity.enums.AchadoPostural;
import com.carlesso.pilatesapi.entity.enums.GrauAlteracao;
import com.carlesso.pilatesapi.entity.enums.PadraoRespiratorio;
import com.carlesso.pilatesapi.entity.enums.RegiaoDor;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

public record ReavaliacaoUpdateDTO(
        LocalDate dataReavaliacao,
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
                        schema = @Schema(implementation = AchadoPostural.class))
                List<String> achadosPosturais,
        String achadosPosturaisComplemento,
        @Schema(
                        description = "Ausente mantém a seleção atual; texto vazio a limpa",
                        implementation = PadraoRespiratorio.class)
                String padraoRespiratorioOpcao,
        String padraoRespiratorioComplemento,
        @Schema(
                        description = "Ausente mantém a seleção atual; texto vazio a limpa",
                        implementation = GrauAlteracao.class)
                String equilibrioOpcao,
        String equilibrioComplemento,
        @Schema(
                        description = "Ausente mantém a seleção atual; texto vazio a limpa",
                        implementation = GrauAlteracao.class)
                String coordenacaoMotoraOpcao,
        String coordenacaoMotoraComplemento,
        @ArraySchema(arraySchema = @Schema(description = "Ausente mantém a lista atual; lista vazia a limpa"))
                List<TesteFuncionalRealizadoDTO> testesFuncionais,
        String testesFuncionaisComplemento,
        @Schema(
                        description =
                                "Versão recebida no GET; divergente da atual retorna 409 concurrent-modification. Ausente: sem checagem (modo legado)",
                        example = "0")
                Long version)
        implements CamposExameFisicoOpcoesDTO {}
