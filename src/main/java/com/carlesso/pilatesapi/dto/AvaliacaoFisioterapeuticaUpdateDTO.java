package com.carlesso.pilatesapi.dto;

import com.carlesso.pilatesapi.entity.enums.AchadoPostural;
import com.carlesso.pilatesapi.entity.enums.GrauAlteracao;
import com.carlesso.pilatesapi.entity.enums.PadraoRespiratorio;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.util.List;

public record AvaliacaoFisioterapeuticaUpdateDTO(
        LocalDate dataAvaliacao,
        @Pattern(regexp = ".*\\S.*", message = "queixaFuncional não pode ser vazio") String queixaFuncional,
        String avaliacaoPostural,
        String mobilidadeArticular,
        String forcaMuscular,
        String flexibilidade,
        String equilibrio,
        String coordenacaoMotora,
        String padraoRespiratorio,
        @Min(0) @Max(10) Integer escalaDor,
        String testesFuncionaisRealizados,
        @Pattern(regexp = ".*\\S.*", message = "diagnosticoFisioterapeutico não pode ser vazio") String diagnosticoFisioterapeutico,
        String observacoesGerais,
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
