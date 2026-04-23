package com.carlesso.pilatesapi.dto;

import com.carlesso.pilatesapi.entity.Profissional;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProfissionalResponseDTO(
        Long id,
        String nome,
        String email,
        String cpf,
        String telefone,
        String tipoContrato,
        BigDecimal percentualPagamentoAula,
        LocalDate dataInicio,
        boolean ativo
) {
    public static ProfissionalResponseDTO from(Profissional profissional) {
        return new ProfissionalResponseDTO(
                profissional.getId(),
                profissional.getNome(),
                profissional.getEmail(),
                profissional.getCpf(),
                profissional.getTelefone(),
                profissional.getTipoContrato(),
                profissional.getPercentualPagamentoAula(),
                profissional.getDataInicio(),
                profissional.isAtivo()
        );
    }
}
