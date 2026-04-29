package com.carlesso.pilatesapi.service;

import com.carlesso.pilatesapi.dto.DashboardResumoDTO;
import com.carlesso.pilatesapi.entity.enums.StatusPagamento;
import com.carlesso.pilatesapi.repository.AulaRepository;
import com.carlesso.pilatesapi.repository.PacienteRepository;
import com.carlesso.pilatesapi.repository.PagamentoRepository;
import com.carlesso.pilatesapi.repository.ProfissionalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

@Service
public class DashboardService {

    private final PacienteRepository pacienteRepository;
    private final ProfissionalRepository profissionalRepository;
    private final PagamentoRepository pagamentoRepository;
    private final AulaRepository aulaRepository;

    public DashboardService(PacienteRepository pacienteRepository,
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
                pacienteRepository.countByAtivoTrue(),
                pacienteRepository.countByAtivoFalse()
        );

        var profissionais = new DashboardResumoDTO.ProfissionaisResumo(
                profissionalRepository.countByAtivoTrue(),
                profissionalRepository.countByAtivoFalse()
        );

        var pagamentos = new DashboardResumoDTO.PagamentosResumo(
                pagamentoRepository.countByStatus(StatusPagamento.PENDENTE),
                pagamentoRepository.countByStatus(StatusPagamento.PAGO),
                pagamentoRepository.countByStatus(StatusPagamento.VENCIDO),
                pagamentoRepository.sumValorByStatusAndDataPagamentoBetween(
                        StatusPagamento.PAGO, inicioMes, fimMes)
        );

        var aulas = new DashboardResumoDTO.AulasResumo(
                aulaRepository.countByRealizadaAndDataBetweenAndPacienteAtivoTrue(true, inicioMes, fimMes),
                aulaRepository.countByRealizadaAndDataBetweenAndPacienteAtivoTrue(false, inicioMes, fimMes)
        );

        return new DashboardResumoDTO(pacientes, profissionais, pagamentos, aulas, LocalDateTime.now());
    }
}
