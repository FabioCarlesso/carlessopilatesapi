package com.carlesso.pilatesapi.service;

import com.carlesso.pilatesapi.dto.AulasPorProfissionalDTO;
import com.carlesso.pilatesapi.dto.DashboardIndicadoresDTO;
import com.carlesso.pilatesapi.dto.DashboardResumoDTO;
import com.carlesso.pilatesapi.entity.enums.StatusPagamento;
import com.carlesso.pilatesapi.repository.AulaRepository;
import com.carlesso.pilatesapi.repository.ContagemMensalProjection;
import com.carlesso.pilatesapi.repository.PacienteRepository;
import com.carlesso.pilatesapi.repository.PagamentoRepository;
import com.carlesso.pilatesapi.repository.ProfissionalRepository;
import com.carlesso.pilatesapi.util.PeriodoGuard;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    public static final int MESES_PADRAO = 12;
    public static final int MESES_MAXIMO = 24;

    private final PacienteRepository pacienteRepository;
    private final ProfissionalRepository profissionalRepository;
    private final PagamentoRepository pagamentoRepository;
    private final AulaRepository aulaRepository;

    public DashboardService(
            PacienteRepository pacienteRepository,
            ProfissionalRepository profissionalRepository,
            PagamentoRepository pagamentoRepository,
            AulaRepository aulaRepository) {
        this.pacienteRepository = pacienteRepository;
        this.profissionalRepository = profissionalRepository;
        this.pagamentoRepository = pagamentoRepository;
        this.aulaRepository = aulaRepository;
    }

    @Transactional(readOnly = true)
    public DashboardResumoDTO obterResumo() {
        YearMonth mesAtual = YearMonth.now();
        LocalDate inicioMes = mesAtual.atDay(1);
        LocalDate fimMes = mesAtual.atEndOfMonth();

        var pacientes = new DashboardResumoDTO.PacientesResumo(
                pacienteRepository.countByAtivoTrue(), pacienteRepository.countByAtivoFalse());

        var profissionais = new DashboardResumoDTO.ProfissionaisResumo(
                profissionalRepository.countByAtivoTrue(), profissionalRepository.countByAtivoFalse());

        var pagamentos = new DashboardResumoDTO.PagamentosResumo(
                pagamentoRepository.countByStatus(StatusPagamento.PENDENTE),
                pagamentoRepository.countByStatus(StatusPagamento.PAGO),
                pagamentoRepository.countByStatus(StatusPagamento.VENCIDO),
                pagamentoRepository.sumValorByStatusAndDataPagamentoBetween(StatusPagamento.PAGO, inicioMes, fimMes));

        var aulas = new DashboardResumoDTO.AulasResumo(
                aulaRepository.countByRealizadaAndDataBetweenAndPacienteAtivoTrue(true, inicioMes, fimMes),
                aulaRepository.countByRealizadaAndDataBetweenAndPacienteAtivoTrue(false, inicioMes, fimMes));

        return new DashboardResumoDTO(pacientes, profissionais, pagamentos, aulas, LocalDateTime.now());
    }

    /**
     * Séries mensais dos últimos {@code meses} meses, terminando no mês atual (que entra na série).
     *
     * @throws IllegalArgumentException se {@code meses} estiver fora de 1..{@value #MESES_MAXIMO}
     */
    @Transactional(readOnly = true)
    public DashboardIndicadoresDTO obterIndicadores(int meses) {
        if (meses < 1 || meses > MESES_MAXIMO) {
            throw new IllegalArgumentException("Parâmetro meses deve estar entre 1 e " + MESES_MAXIMO);
        }
        YearMonth mesAtual = YearMonth.now();
        YearMonth primeiroMes = mesAtual.minusMonths(meses - 1L);
        LocalDate inicio = primeiroMes.atDay(1);
        LocalDate fim = mesAtual.atEndOfMonth();

        Map<YearMonth, BigDecimal> receitaPorMes = new HashMap<>();
        pagamentoRepository
                .sumValorPorMes(StatusPagamento.PAGO, inicio, fim)
                .forEach(linha -> receitaPorMes.put(YearMonth.of(linha.getAno(), linha.getMes()), linha.getTotal()));

        Map<YearMonth, AulaRepository.AulasMensalProjection> aulasPorMes = new HashMap<>();
        aulaRepository
                .countPorMes(inicio, fim)
                .forEach(linha -> aulasPorMes.put(YearMonth.of(linha.getAno(), linha.getMes()), linha));

        Map<YearMonth, Long> novosPorMes = porMes(pacienteRepository.countCadastradosPorMes(inicio, fim));
        Map<YearMonth, Long> inativadosPorMes = porMes(pacienteRepository.countInativadosPorMes(inicio, fim));

        List<DashboardIndicadoresDTO.ReceitaMensal> receita = new ArrayList<>(meses);
        List<DashboardIndicadoresDTO.AulasMensal> aulas = new ArrayList<>(meses);
        List<DashboardIndicadoresDTO.PacientesMensal> pacientes = new ArrayList<>(meses);
        for (YearMonth mes = primeiroMes; !mes.isAfter(mesAtual); mes = mes.plusMonths(1)) {
            receita.add(
                    new DashboardIndicadoresDTO.ReceitaMensal(mes, receitaPorMes.getOrDefault(mes, BigDecimal.ZERO)));
            AulaRepository.AulasMensalProjection aulasDoMes = aulasPorMes.get(mes);
            aulas.add(new DashboardIndicadoresDTO.AulasMensal(
                    mes,
                    aulasDoMes == null ? 0 : aulasDoMes.getRealizadas(),
                    aulasDoMes == null ? 0 : aulasDoMes.getAgendadas()));
            pacientes.add(new DashboardIndicadoresDTO.PacientesMensal(
                    mes, novosPorMes.getOrDefault(mes, 0L), inativadosPorMes.getOrDefault(mes, 0L)));
        }

        return new DashboardIndicadoresDTO(meses, receita, aulas, pacientes, LocalDateTime.now());
    }

    /**
     * Ranking de aulas realizadas por profissional. Sem {@code inicio} e {@code fim}, o período é o mês atual; com só
     * um dos dois, a requisição é recusada.
     *
     * @throws IllegalArgumentException período incompleto, invertido ou acima de
     *     {@value PeriodoGuard#LIMITE_DIAS_BLOQUEIOS} dias
     */
    @Transactional(readOnly = true)
    public List<AulasPorProfissionalDTO> obterAulasPorProfissional(LocalDate inicio, LocalDate fim) {
        if (inicio == null && fim == null) {
            YearMonth mesAtual = YearMonth.now();
            inicio = mesAtual.atDay(1);
            fim = mesAtual.atEndOfMonth();
        }
        PeriodoGuard.exigirPeriodoValido(inicio, fim, PeriodoGuard.LIMITE_DIAS_BLOQUEIOS);
        return aulaRepository.countRealizadasPorProfissional(inicio, fim);
    }

    private static Map<YearMonth, Long> porMes(List<ContagemMensalProjection> linhas) {
        Map<YearMonth, Long> totais = new HashMap<>();
        linhas.forEach(linha -> totais.put(YearMonth.of(linha.getAno(), linha.getMes()), linha.getTotal()));
        return totais;
    }
}
