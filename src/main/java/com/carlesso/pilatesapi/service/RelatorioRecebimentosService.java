package com.carlesso.pilatesapi.service;

import com.carlesso.pilatesapi.dto.PeriodoDTO;
import com.carlesso.pilatesapi.dto.RelatorioRecebimentosDTO;
import com.carlesso.pilatesapi.dto.RelatorioRecebimentosDTO.FormaRecebimento;
import com.carlesso.pilatesapi.entity.Pagamento;
import com.carlesso.pilatesapi.repository.PagamentoRepository;
import com.carlesso.pilatesapi.util.PeriodoGuard;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Relatório de recebimentos do período (#231): pagamentos {@code PAGO} por {@code dataPagamento}, com totais por forma
 * de pagamento.
 *
 * <p>O critério é o da receita do relatório financeiro e do dashboard (regime de caixa, sem filtrar paciente ativo):
 * sem filtros, o total daqui é a {@code receita} de {@link RelatorioFinanceiroService} no mesmo período.
 */
@Service
public class RelatorioRecebimentosService {

    /** Mesmo teto do relatório financeiro. */
    static final long LIMITE_DIAS = 366;

    private final PagamentoRepository pagamentoRepository;
    private final Clock relogio;

    @Autowired
    public RelatorioRecebimentosService(
            PagamentoRepository pagamentoRepository,
            @Value("${app.fuso-horario:America/Sao_Paulo}") String fusoHorario) {
        this(pagamentoRepository, Clock.system(ZoneId.of(fusoHorario)));
    }

    RelatorioRecebimentosService(PagamentoRepository pagamentoRepository, Clock relogio) {
        this.pagamentoRepository = pagamentoRepository;
        this.relogio = relogio;
    }

    /**
     * @param formaPagamento filtro opcional; {@code NAO_INFORMADA} traz os pagamentos sem forma registrada
     * @param pacienteId filtro opcional
     * @throws IllegalArgumentException período ausente, invertido, acima de {@value #LIMITE_DIAS} dias ou volume acima
     *     do teto de registros ({@code 400})
     */
    @Transactional(readOnly = true)
    public RelatorioRecebimentosDTO gerar(
            LocalDate inicio, LocalDate fim, FormaRecebimento formaPagamento, Long pacienteId) {
        PeriodoGuard.exigirPeriodoValido(inicio, fim, LIMITE_DIAS);

        // Os filtros vão em memória: o período já limita o volume, e parâmetro opcional nulo no JPQL exige casts no
        // PostgreSQL que não compensam para a escala do estúdio.
        List<RelatorioRecebimentosDTO.Recebimento> recebimentos =
                pagamentoRepository.findRecebidosNoPeriodo(inicio, fim).stream()
                        .filter(p -> pacienteId == null
                                || Objects.equals(p.getPaciente().getId(), pacienteId))
                        .map(RelatorioRecebimentosService::linha)
                        .filter(r -> formaPagamento == null || r.formaPagamento() == formaPagamento)
                        .toList();
        PeriodoGuard.exigirVolumeSuportado(recebimentos.size());

        return new RelatorioRecebimentosDTO(
                new PeriodoDTO(inicio, fim),
                new RelatorioRecebimentosDTO.Filtros(formaPagamento, pacienteId),
                resumo(recebimentos),
                recebimentos,
                LocalDateTime.now(relogio));
    }

    private static RelatorioRecebimentosDTO.Recebimento linha(Pagamento pagamento) {
        return new RelatorioRecebimentosDTO.Recebimento(
                pagamento.getId(),
                pagamento.getPaciente().getId(),
                pagamento.getPaciente().getNome(),
                moeda(pagamento.getValor()),
                pagamento.getDataPagamento(),
                FormaRecebimento.de(pagamento.getFormaPagamento()),
                pagamento.getDataVencimento(),
                pagamento.getPeriodoInicio(),
                pagamento.getPeriodoFim(),
                pagamento.getPlano().getId(),
                pagamento.getPlano().getFrequenciaSemanal());
    }

    private static RelatorioRecebimentosDTO.Resumo resumo(List<RelatorioRecebimentosDTO.Recebimento> recebimentos) {
        List<RelatorioRecebimentosDTO.TotalPorForma> porForma = Arrays.stream(FormaRecebimento.values())
                .map(forma -> {
                    List<RelatorioRecebimentosDTO.Recebimento> daForma = recebimentos.stream()
                            .filter(r -> r.formaPagamento() == forma)
                            .toList();
                    return new RelatorioRecebimentosDTO.TotalPorForma(
                            forma, forma.rotulo(), daForma.size(), somar(daForma));
                })
                .toList();
        return new RelatorioRecebimentosDTO.Resumo(recebimentos.size(), somar(recebimentos), porForma);
    }

    private static BigDecimal somar(List<RelatorioRecebimentosDTO.Recebimento> recebimentos) {
        return moeda(recebimentos.stream()
                .map(RelatorioRecebimentosDTO.Recebimento::valor)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private static BigDecimal moeda(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP);
    }
}
