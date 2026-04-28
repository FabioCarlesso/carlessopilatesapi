package com.carlesso.pilatesapi.service;

import com.carlesso.pilatesapi.dto.RelatorioNfseResponseDTO;
import com.carlesso.pilatesapi.entity.Paciente;
import com.carlesso.pilatesapi.entity.Pagamento;
import com.carlesso.pilatesapi.entity.enums.StatusPagamento;
import com.carlesso.pilatesapi.exception.BusinessException;
import com.carlesso.pilatesapi.repository.PagamentoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class RelatorioNfseService {

    private static final Pattern COMPETENCIA_PATTERN = Pattern.compile("^(0[1-9]|1[0-2])/\\d{4}$");

    private final PagamentoRepository pagamentoRepository;

    public RelatorioNfseService(PagamentoRepository pagamentoRepository) {
        this.pagamentoRepository = pagamentoRepository;
    }

    @Transactional(readOnly = true)
    public List<RelatorioNfseResponseDTO> gerar(String competencia, Boolean notaAnteriorEmitida) {
        YearMonth periodo = parseCompetencia(competencia);
        LocalDate inicio = periodo.atDay(1);
        LocalDate fim = periodo.atEndOfMonth();
        List<Pagamento> pagamentos = pagamentoRepository.findPagamentosConfirmadosParaRelatorioNfse(
                StatusPagamento.PAGO,
                inicio,
                fim);
        Set<Long> pacientesComPagamentoAnterior = buscarPacientesComPagamentoAnterior(pagamentos, inicio);

        return pagamentos.stream()
                .map(pagamento -> montarItem(pagamento, competencia, pacientesComPagamentoAnterior))
                .filter(item -> notaAnteriorEmitida == null
                        || Objects.equals(item.notaAnteriorEmitida(), notaAnteriorEmitida))
                .toList();
    }

    private YearMonth parseCompetencia(String competencia) {
        if (competencia == null || competencia.isBlank()) {
            throw new IllegalArgumentException("competencia é obrigatória");
        }
        if (!COMPETENCIA_PATTERN.matcher(competencia).matches()) {
            throw new IllegalArgumentException("competencia deve estar no formato MM/AAAA");
        }

        int mes = Integer.parseInt(competencia.substring(0, 2));
        int ano = Integer.parseInt(competencia.substring(3));
        return YearMonth.of(ano, mes);
    }

    private Set<Long> buscarPacientesComPagamentoAnterior(List<Pagamento> pagamentos, LocalDate inicioCompetencia) {
        List<Long> pacienteIds = pagamentos.stream()
                .map(pagamento -> pagamento.getPaciente().getId())
                .distinct()
                .toList();

        if (pacienteIds.isEmpty()) {
            return Set.of();
        }

        return pagamentoRepository.findPacienteIdsComPagamentoConfirmadoAntes(
                        pacienteIds,
                        StatusPagamento.PAGO,
                        inicioCompetencia)
                .stream()
                .collect(Collectors.toSet());
    }

    private RelatorioNfseResponseDTO montarItem(
            Pagamento pagamento,
            String competencia,
            Set<Long> pacientesComPagamentoAnterior) {
        Paciente paciente = pagamento.getPaciente();
        validarDadosParaEmissao(pagamento, paciente);

        return new RelatorioNfseResponseDTO(
                paciente.getNome(),
                paciente.getCpf(),
                pagamento.getValor(),
                competencia,
                "Aulas de Pilates - Competência " + competencia,
                pacientesComPagamentoAnterior.contains(paciente.getId()),
                pagamento.getDataPagamento(),
                ""
        );
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
