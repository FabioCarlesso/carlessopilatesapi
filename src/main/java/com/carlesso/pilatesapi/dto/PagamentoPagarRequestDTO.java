package com.carlesso.pilatesapi.dto;

import com.carlesso.pilatesapi.entity.enums.FormaPagamento;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/** Corpo de {@code PATCH /pagamentos/{id}/pagar}. O corpo inteiro é opcional. */
public record PagamentoPagarRequestDTO(
        @Schema(
                        description = "Data em que o pagamento foi recebido. Ausente ou nula usa a data atual",
                        example = "2026-10-10")
                LocalDate dataPagamento,
        @Schema(
                        description =
                                "Como o pagamento foi recebido. Opcional por enquanto, para não quebrar clientes que ainda não"
                                        + " a enviam; ausente ou nula grava o pagamento sem forma",
                        example = "PIX")
                FormaPagamento formaPagamento) {}
