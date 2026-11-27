package com.carlesso.pilatesapi.dto;

import com.carlesso.pilatesapi.entity.PontoDor;
import com.carlesso.pilatesapi.entity.enums.VistaCorporal;

public record PontoDorResponseDTO(
        Long id,
        VistaCorporal vista,
        Double x,
        Double y,
        Integer desenhoVersao,
        Integer intensidade,
        String observacao) {

    public static PontoDorResponseDTO from(PontoDor ponto) {
        return new PontoDorResponseDTO(
                ponto.getId(),
                ponto.getVista(),
                ponto.getX().doubleValue(),
                ponto.getY().doubleValue(),
                ponto.getDesenhoVersao(),
                ponto.getIntensidade(),
                ponto.getObservacao());
    }
}
