package com.carlesso.pilatesapi.dto;

import com.carlesso.pilatesapi.entity.Pagamento;
import com.carlesso.pilatesapi.entity.enums.StatusPagamento;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PagamentoResponseDTO(
        Long id,
        Long pacienteId,
        String pacienteNome,
        Long planoId,
        BigDecimal valor,
        StatusPagamento status,
        LocalDate dataPagamento,
        LocalDate dataVencimento,
        LocalDate periodoInicio,
        LocalDate periodoFim
) {
    public static PagamentoResponseDTO from(Pagamento pagamento) {
        return new PagamentoResponseDTO(
                pagamento.getId(),
                pagamento.getPaciente().getId(),
                pagamento.getPaciente().getNome(),
                pagamento.getPlano().getId(),
                pagamento.getValor(),
                pagamento.getStatus(),
                pagamento.getDataPagamento(),
                pagamento.getDataVencimento(),
                pagamento.getPeriodoInicio(),
                pagamento.getPeriodoFim()
        );
    }
}
