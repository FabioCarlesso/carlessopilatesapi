package com.carlesso.pilatesapi.service;

import com.carlesso.pilatesapi.dto.RelatorioNfseResponseDTO;
import com.carlesso.pilatesapi.entity.Paciente;
import com.carlesso.pilatesapi.entity.Pagamento;
import com.carlesso.pilatesapi.entity.enums.StatusPagamento;
import com.carlesso.pilatesapi.exception.BusinessException;
import com.carlesso.pilatesapi.repository.NotaFiscalEmitidaRepository;
import com.carlesso.pilatesapi.repository.PagamentoRepository;
import com.carlesso.pilatesapi.util.CompetenciaUtils;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RelatorioNfseService {

    private final PagamentoRepository pagamentoRepository;
    private final NotaFiscalEmitidaRepository notaFiscalEmitidaRepository;

    public RelatorioNfseService(
            PagamentoRepository pagamentoRepository, NotaFiscalEmitidaRepository notaFiscalEmitidaRepository) {
        this.pagamentoRepository = pagamentoRepository;
        this.notaFiscalEmitidaRepository = notaFiscalEmitidaRepository;
    }

    @Transactional(readOnly = true)
    public List<RelatorioNfseResponseDTO> gerar(String competencia, Boolean notaAnteriorEmitida) {
        YearMonth periodo = CompetenciaUtils.parse(competencia);
        LocalDate inicio = periodo.atDay(1);
        LocalDate fim = periodo.atEndOfMonth();
        List<Pagamento> pagamentos =
                pagamentoRepository.findPagamentosConfirmadosParaRelatorioNfse(StatusPagamento.PAGO, inicio, fim);
        Set<Long> pacientesComNotaAnterior = buscarPacientesComNotaEmitidaAntes(pagamentos, inicio);

        return pagamentos.stream()
                .map(pagamento -> montarItem(pagamento, competencia, pacientesComNotaAnterior))
                .filter(item ->
                        notaAnteriorEmitida == null || Objects.equals(item.notaAnteriorEmitida(), notaAnteriorEmitida))
                .toList();
    }

    private Set<Long> buscarPacientesComNotaEmitidaAntes(List<Pagamento> pagamentos, LocalDate inicioCompetencia) {
        List<Long> pacienteIds = pagamentos.stream()
                .map(pagamento -> pagamento.getPaciente().getId())
                .distinct()
                .toList();

        if (pacienteIds.isEmpty()) {
            return Set.of();
        }

        return notaFiscalEmitidaRepository.findPacienteIdsComNotaEmitidaAntes(pacienteIds, inicioCompetencia).stream()
                .collect(Collectors.toSet());
    }

    private RelatorioNfseResponseDTO montarItem(
            Pagamento pagamento, String competencia, Set<Long> pacientesComNotaAnterior) {
        Paciente paciente = pagamento.getPaciente();
        validarDadosParaEmissao(pagamento, paciente);

        return new RelatorioNfseResponseDTO(
                paciente.getNome(),
                paciente.getCpf(),
                pagamento.getValor(),
                competencia,
                "Aulas de Pilates - Competência " + competencia,
                pacientesComNotaAnterior.contains(paciente.getId()),
                pagamento.getDataPagamento(),
                "");
    }

    private void validarDadosParaEmissao(Pagamento pagamento, Paciente paciente) {
        if (paciente.getNome() == null || paciente.getNome().isBlank()) {
            throw new BusinessException("Nome do paciente deve estar preenchido para emissão da NFSE");
        }
        if (paciente.getCpf() == null || paciente.getCpf().isBlank()) {
            throw new BusinessException("CPF/CNPJ deve estar preenchido para emissão da NFSE");
        }
        if (pagamento.getValor() == null || pagamento.getValor().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("ValorPago deve ser maior que zero para emissão da NFSE");
        }
        if (pagamento.getDataPagamento() == null) {
            throw new BusinessException("DataPagamento deve estar preenchida para emissão da NFSE");
        }
    }
}
