package com.carlesso.pilatesapi.dto;

import com.carlesso.pilatesapi.entity.enums.CampoOpcoesAvaliacao;
import com.carlesso.pilatesapi.entity.enums.OpcaoAvaliacao;
import com.carlesso.pilatesapi.entity.enums.SelecaoOpcao;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Arrays;
import java.util.List;

/** Catálogo das opções pré-definidas da anamnese, da avaliação e da reavaliação (#248). */
public record OpcoesAvaliacaoResponseDTO(List<Campo> campos) {

    public static OpcoesAvaliacaoResponseDTO deTodosOsCampos() {
        return new OpcoesAvaliacaoResponseDTO(
                Arrays.stream(CampoOpcoesAvaliacao.values()).map(Campo::from).toList());
    }

    public record Campo(
            @Schema(
                            description = "Nome da propriedade nos DTOs de anamnese/avaliação/reavaliação",
                            example = "regioesDor")
                    String campo,
            @Schema(example = "Regiões de dor/queixa") String rotulo,
            SelecaoOpcao selecao,
            @Schema(description = "true só em testesFuncionais: cada item leva resultado POSITIVO ou NEGATIVO")
                    boolean comResultado,
            List<Opcao> opcoes,
            @Schema(
                            description = "Nome da propriedade do texto livre que complementa o campo",
                            example = "regioesDorComplemento")
                    String campoComplemento) {

        static Campo from(CampoOpcoesAvaliacao c) {
            return new Campo(
                    c.getCampo(),
                    c.getRotulo(),
                    c.getSelecao(),
                    c.isComResultado(),
                    c.getOpcoes().stream().map(Opcao::from).toList(),
                    c.getCampoComplemento());
        }
    }

    public record Opcao(
            @Schema(description = "Valor gravado e enviado na API", example = "CERVICAL") String valor,
            @Schema(example = "Cervical") String rotulo,
            @Schema(
                            description =
                                    "false = opção aposentada: exibir nos registros que a usam, mas não oferecer para seleção nova")
                    boolean ativa) {

        static Opcao from(OpcaoAvaliacao o) {
            return new Opcao(o.name(), o.getRotulo(), o.isAtiva());
        }
    }
}
