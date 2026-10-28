package com.carlesso.pilatesapi.service;

import com.carlesso.pilatesapi.dto.AulasPorProfissionalDTO;
import com.carlesso.pilatesapi.dto.PeriodoDTO;
import com.carlesso.pilatesapi.dto.ProfissionalPagamentoRelatorioDTO;
import com.carlesso.pilatesapi.dto.RelatorioFinanceiroDTO;
import com.carlesso.pilatesapi.repository.AulaRepository;
import com.carlesso.pilatesapi.repository.PagamentoRepository;
import com.carlesso.pilatesapi.repository.PagamentoRepository.TotalPagamentosProjection;
import com.carlesso.pilatesapi.util.PeriodoGuard;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Relatório financeiro consolidado do estúdio (#108): receita, repasses aos profissionais, inadimplência e resultado
 * líquido de um período.
 *
 * <p>O repasse não tem cálculo próprio: é a soma dos relatórios de pagamento por profissional
 * ({@link ProfissionalService#gerarRelatorioPagamento}) no mesmo período. Assim o consolidado bate, por construção, com
 * a soma dos relatórios individuais — uma regra nova de repasse muda os dois de uma vez.
 */
@Service
public class RelatorioFinanceiroService {

    /** Mesmo teto do relatório de pagamento do profissional, que este relatório chama por baixo. */
    static final long LIMITE_DIAS = 366;

    private final PagamentoRepository pagamentoRepository;
    private final AulaRepository aulaRepository;
    private final ProfissionalService profissionalService;
    private final Clock relogio;

    @Autowired
    public RelatorioFinanceiroService(
            PagamentoRepository pagamentoRepository,
            AulaRepository aulaRepository,
            ProfissionalService profissionalService,
            @Value("${app.fuso-horario:America/Sao_Paulo}") String fusoHorario) {
        this(pagamentoRepository, aulaRepository, profissionalService, Clock.system(ZoneId.of(fusoHorario)));
    }

    RelatorioFinanceiroService(
            PagamentoRepository pagamentoRepository,
            AulaRepository aulaRepository,
            ProfissionalService profissionalService,
            Clock relogio) {
        this.pagamentoRepository = pagamentoRepository;
        this.aulaRepository = aulaRepository;
        this.profissionalService = profissionalService;
        this.relogio = relogio;
    }

    /**
     * @throws IllegalArgumentException período ausente, invertido ou acima de {@value #LIMITE_DIAS} dias ({@code 400})
     */
    @Transactional(readOnly = true)
    public RelatorioFinanceiroDTO gerar(LocalDate inicio, LocalDate fim) {
        PeriodoGuard.exigirPeriodoValido(inicio, fim, LIMITE_DIAS);

        TotalPagamentosProjection recebidos = pagamentoRepository.totalizarRecebidos(inicio, fim);
        var receita = new RelatorioFinanceiroDTO.Receita(recebidos.getQuantidade(), moeda(recebidos.getTotal()));

        TotalPagamentosProjection vencidos = pagamentoRepository.totalizarVencidos(inicio, fim, LocalDate.now(relogio));
        var inadimplencia =
                new RelatorioFinanceiroDTO.Inadimplencia(vencidos.getQuantidade(), moeda(vencidos.getTotal()));

        List<RelatorioFinanceiroDTO.RepasseProfissional> profissionais = repassesPorProfissional(inicio, fim);
        var repasses = new RelatorioFinanceiroDTO.Repasses(
                profissionais.stream()
                        .mapToLong(RelatorioFinanceiroDTO.RepasseProfissional::totalAulas)
                        .sum(),
                moeda(profissionais.stream()
                        .map(RelatorioFinanceiroDTO.RepasseProfissional::totalProfissional)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)),
                profissionais);

        return new RelatorioFinanceiroDTO(
                new PeriodoDTO(inicio, fim),
                receita,
                repasses,
                inadimplencia,
                receita.total().subtract(repasses.total()),
                LocalDateTime.now(relogio));
    }

    /**
     * Um relatório individual por profissional com aula realizada no período. O ranking do dashboard serve de lista de
     * candidatos: inclui profissional inativo e não filtra paciente; quem não tiver aula que gere repasse (só pacientes
     * inativos, por exemplo) sai do relatório individual com zero aulas e fica de fora daqui.
     */
    private List<RelatorioFinanceiroDTO.RepasseProfissional> repassesPorProfissional(LocalDate inicio, LocalDate fim) {
        return aulaRepository.countRealizadasPorProfissional(inicio, fim).stream()
                .map(AulasPorProfissionalDTO::profissionalId)
                .map(id -> profissionalService.gerarRelatorioPagamento(id, inicio, fim))
                .filter(relatorio -> relatorio.resumo().totalAulas() > 0)
                .map(RelatorioFinanceiroService::repasse)
                .sorted(Comparator.comparing(RelatorioFinanceiroDTO.RepasseProfissional::nome)
                        .thenComparing(RelatorioFinanceiroDTO.RepasseProfissional::profissionalId))
                .toList();
    }

    private static RelatorioFinanceiroDTO.RepasseProfissional repasse(ProfissionalPagamentoRelatorioDTO relatorio) {
        return new RelatorioFinanceiroDTO.RepasseProfissional(
                relatorio.profissional().id(),
                relatorio.profissional().nome(),
                relatorio.profissional().percentualPagamentoAula(),
                relatorio.resumo().totalAulas(),
                relatorio.resumo().quantidadePagamentos(),
                relatorio.resumo().totalPagamentosBruto(),
                relatorio.resumo().totalProfissional());
    }

    private static BigDecimal moeda(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP);
    }
}
