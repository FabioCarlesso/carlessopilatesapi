package com.carlesso.pilatesapi.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RelatorioNfseResponseDTO(
        String nome,
        String cpfCnpj,
        BigDecimal valorPago,
        String competencia,
        String descricaoServico,
        Boolean notaAnteriorEmitida,
        LocalDate dataPagamento,
        String observacoes
) {
}
