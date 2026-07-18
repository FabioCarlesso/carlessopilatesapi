package com.carlesso.pilatesapi.dto;

import com.carlesso.pilatesapi.entity.AvaliacaoPostural;
import com.carlesso.pilatesapi.entity.enums.StatusAvaliacaoPostural;
import com.carlesso.pilatesapi.entity.enums.VistaPostural;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "Análise postural com os pontos marcados e as métricas calculadas pelo sistema")
public record AvaliacaoPosturalResponseDTO(
        Long id,
        Long avaliacaoFisioterapeuticaId,
        VistaPostural vista,
        StatusAvaliacaoPostural status,
        BigDecimal linhaPrumoX,
        BigDecimal calibracaoCmPorUnidade,
        BigDecimal proporcaoImagem,
        String observacoes,
        @Schema(description = "Indica se a foto já foi enviada") boolean temFoto,
        List<LandmarkDTO> landmarks,
        MetricasPosturaisDTO metricas,
        LocalDateTime dataCriacao,
        LocalDateTime dataAtualizacao) {

    public static AvaliacaoPosturalResponseDTO from(
            AvaliacaoPostural a, List<LandmarkDTO> landmarks, MetricasPosturaisDTO metricas) {
        return new AvaliacaoPosturalResponseDTO(
                a.getId(),
                a.getAvaliacaoFisioterapeutica().getId(),
                a.getVista(),
                a.getStatus(),
                a.getLinhaPrumoX(),
                a.getCalibracaoCmPorUnidade(),
                a.getProporcaoImagem(),
                a.getObservacoes(),
                a.getFotoContentType() != null,
                landmarks,
                metricas,
                a.getDataCriacao(),
                a.getDataAtualizacao());
    }
}
