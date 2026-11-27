package com.carlesso.pilatesapi.dto;

import com.carlesso.pilatesapi.entity.Reavaliacao;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record ReavaliacaoResponseDTO(
        Long id,
        Long pacienteId,
        String nomePaciente,
        Long avaliacaoFisioterapeuticaId,
        Long planoTratamentoId,
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
        List<String> regioesDor,
        String regioesDorComplemento,
        List<String> achadosPosturais,
        String achadosPosturaisComplemento,
        String padraoRespiratorioOpcao,
        String padraoRespiratorioComplemento,
        String equilibrioOpcao,
        String equilibrioComplemento,
        String coordenacaoMotoraOpcao,
        String coordenacaoMotoraComplemento,
        List<TesteFuncionalRealizadoDTO> testesFuncionais,
        String testesFuncionaisComplemento,
        LocalDateTime dataCriacao,
        LocalDateTime dataAtualizacao,
        @Schema(description = "E-mail de quem criou o registro; \"system\" para processos automáticos")
                String criadoPor,
        @Schema(description = "E-mail de quem fez a última alteração; nulo enquanto não houver alteração")
                String alteradoPor,
        Long version) {
    public static ReavaliacaoResponseDTO from(Reavaliacao r) {
        return new ReavaliacaoResponseDTO(
                r.getId(),
                r.getPaciente().getId(),
                r.getPaciente().getNome(),
                r.getAvaliacaoFisioterapeutica() != null
                        ? r.getAvaliacaoFisioterapeutica().getId()
                        : null,
                r.getPlanoTratamento() != null ? r.getPlanoTratamento().getId() : null,
                r.getDataReavaliacao(),
                r.getComparativoAvaliacaoAnterior(),
                r.getEvolucaoDor(),
                r.getEvolucaoForca(),
                r.getEvolucaoMobilidade(),
                r.getEvolucaoFuncional(),
                r.getObjetivosAlcancados(),
                r.getPontosAtencao(),
                r.getAjustesRecomendados(),
                r.getObservacoesGerais(),
                r.getRegioesDor(),
                r.getRegioesDorComplemento(),
                r.getExameFisicoOpcoes().getAchadosPosturais(),
                r.getExameFisicoOpcoes().getAchadosPosturaisComplemento(),
                r.getExameFisicoOpcoes().getPadraoRespiratorioOpcao(),
                r.getExameFisicoOpcoes().getPadraoRespiratorioComplemento(),
                r.getExameFisicoOpcoes().getEquilibrioOpcao(),
                r.getExameFisicoOpcoes().getEquilibrioComplemento(),
                r.getExameFisicoOpcoes().getCoordenacaoMotoraOpcao(),
                r.getExameFisicoOpcoes().getCoordenacaoMotoraComplemento(),
                r.getExameFisicoOpcoes().getTestesFuncionais().stream()
                        .map(TesteFuncionalRealizadoDTO::from)
                        .toList(),
                r.getExameFisicoOpcoes().getTestesFuncionaisComplemento(),
                r.getDataCriacao(),
                r.getDataAtualizacao(),
                r.getCriadoPor(),
                r.getAlteradoPor(),
                r.getVersion());
    }
}
