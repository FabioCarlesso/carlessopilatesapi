package com.carlesso.pilatesapi.dto;

import com.carlesso.pilatesapi.entity.ContatoCobranca;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ContatoCobrancaResponseDTO(
        Long id,
        Long pagamentoId,
        LocalDate dataContato,
        String observacao,
        String registradoPor,
        LocalDateTime registradoEm) {

    public static ContatoCobrancaResponseDTO from(ContatoCobranca contato) {
        return new ContatoCobrancaResponseDTO(
                contato.getId(),
                contato.getPagamento().getId(),
                contato.getDataContato(),
                contato.getObservacao(),
                contato.getRegistradoPor(),
                contato.getRegistradoEm());
    }
}
