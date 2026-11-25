package com.carlesso.pilatesapi.dto;

import com.carlesso.pilatesapi.entity.enums.FormaPagamento;
import com.carlesso.pilatesapi.entity.enums.FrequenciaSemanal;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Pagamentos confirmados no período, por data de pagamento (#231): a lista de "quem pagou" para conferir o caixa e
 * conciliar com o extrato, com totais por forma de pagamento.
 */
public record RelatorioRecebimentosDTO(
        PeriodoDTO periodo, Filtros filtros, Resumo resumo, List<Recebimento> recebimentos, LocalDateTime geradoEm) {

    /**
     * Forma de pagamento como o relatório a enxerga: as de {@link FormaPagamento} mais {@code NAO_INFORMADA}, para os
     * pagamentos confirmados sem forma registrada (os confirmados na migração do seufisio, por exemplo).
     */
    public enum FormaRecebimento {
        PIX("PIX"),
        DINHEIRO("Dinheiro"),
        CARTAO_CREDITO("Cartão de crédito"),
        CARTAO_DEBITO("Cartão de débito"),
        TRANSFERENCIA("Transferência"),
        NAO_INFORMADA("Não informada");

        private final String rotulo;

        FormaRecebimento(String rotulo) {
            this.rotulo = rotulo;
        }

        public String rotulo() {
            return rotulo;
        }

        public static FormaRecebimento de(FormaPagamento forma) {
            return forma == null ? NAO_INFORMADA : valueOf(forma.name());
        }
    }

    @Schema(description = "Filtros aplicados; null quando o filtro não foi informado")
    public record Filtros(FormaRecebimento formaPagamento, Long pacienteId) {}

    @Schema(
            description = "Totais das linhas do relatório. Sem filtros, o total é a receita do relatório financeiro"
                    + " no mesmo período")
    public record Resumo(long quantidadePagamentos, BigDecimal total, List<TotalPorForma> porFormaPagamento) {}

    @Schema(description = "Uma entrada por forma, sempre todas e na ordem do enum, mesmo zeradas")
    public record TotalPorForma(FormaRecebimento formaPagamento, String descricao, long quantidade, BigDecimal total) {}

    public record Recebimento(
            Long pagamentoId,
            Long pacienteId,
            String pacienteNome,
            BigDecimal valor,
            LocalDate dataPagamento,
            FormaRecebimento formaPagamento,
            LocalDate dataVencimento,
            LocalDate periodoInicio,
            LocalDate periodoFim,
            Long planoId,
            FrequenciaSemanal frequenciaSemanal) {}
}
