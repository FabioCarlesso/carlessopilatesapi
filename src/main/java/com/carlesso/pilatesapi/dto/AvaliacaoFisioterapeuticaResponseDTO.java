package com.carlesso.pilatesapi.dto;

import com.carlesso.pilatesapi.entity.AvaliacaoFisioterapeutica;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record AvaliacaoFisioterapeuticaResponseDTO(
        Long id,
        Long pacienteId,
        String nomePaciente,
        LocalDate dataAvaliacao,
        String queixaFuncional,
        String avaliacaoPostural,
        String mobilidadeArticular,
        String forcaMuscular,
        String flexibilidade,
        String equilibrio,
        String coordenacaoMotora,
        String padraoRespiratorio,
        Integer escalaDor,
        String testesFuncionaisRealizados,
        String diagnosticoFisioterapeutico,
        String observacoesGerais,
        LocalDateTime dataCriacao,
        LocalDateTime dataAtualizacao) {
    public static AvaliacaoFisioterapeuticaResponseDTO from(AvaliacaoFisioterapeutica a) {
        return new AvaliacaoFisioterapeuticaResponseDTO(
                a.getId(),
                a.getPaciente().getId(),
                a.getPaciente().getNome(),
                a.getDataAvaliacao(),
                a.getQueixaFuncional(),
                a.getAvaliacaoPostural(),
                a.getMobilidadeArticular(),
                a.getForcaMuscular(),
                a.getFlexibilidade(),
                a.getEquilibrio(),
                a.getCoordenacaoMotora(),
                a.getPadraoRespiratorio(),
                a.getEscalaDor(),
                a.getTestesFuncionaisRealizados(),
                a.getDiagnosticoFisioterapeutico(),
                a.getObservacoesGerais(),
                a.getDataCriacao(),
                a.getDataAtualizacao());
    }
}
