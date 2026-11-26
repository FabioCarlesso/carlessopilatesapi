package com.carlesso.pilatesapi.dto;

import com.carlesso.pilatesapi.entity.Pagamento;
import com.carlesso.pilatesapi.entity.enums.FormaPagamento;
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
        FormaPagamento formaPagamento,
        LocalDate dataVencimento,
        LocalDate periodoInicio,
        LocalDate periodoFim,
        int aulasPacote,
        long aulasRealizadas,
        long aulasSemProfissional) {

    /** Sem contadores: para o pagamento que ainda não tem aulas (recém-criado ou pendente). */
    public static PagamentoResponseDTO from(Pagamento pagamento) {
        return from(pagamento, 0, 0);
    }

    /**
     * {@code aulasPacote} é o tamanho do pacote do plano (#243); {@code aulasRealizadas} conta as de feriado, que
     * consomem o pacote; {@code aulasSemProfissional} são as ainda não realizadas sem profissional (#245).
     */
    public static PagamentoResponseDTO from(Pagamento pagamento, long aulasRealizadas, long aulasSemProfissional) {
        return new PagamentoResponseDTO(
                pagamento.getId(),
                pagamento.getPaciente().getId(),
                pagamento.getPaciente().getNome(),
                pagamento.getPlano().getId(),
                pagamento.getValor(),
                pagamento.getStatus(),
                pagamento.getDataPagamento(),
                pagamento.getFormaPagamento(),
                pagamento.getDataVencimento(),
                pagamento.getPeriodoInicio(),
                pagamento.getPeriodoFim(),
                pagamento.getPlano().getTamanhoDoPacote(),
                aulasRealizadas,
                aulasSemProfissional);
    }
}
