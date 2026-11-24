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

    static final String PENDENCIA_SEM_CPF = "Paciente deseja CPF/CNPJ na nota, mas o CPF não está cadastrado";

    /**
     * Pagamentos confirmados na competência, de pacientes ativos ou não (o dinheiro entrou e a nota é devida).
     * CPF/CNPJ só sai para quem pediu CPF na nota; quem pediu e não tem CPF vira {@code pendencia} no item, em vez
     * de derrubar o relatório inteiro (#229).
     */
    @Transactional(readOnly = true)
    public List<RelatorioNfseResponseDTO> gerar(String competencia, Boolean notaAnteriorEmitida, Boolean cpfNaNota) {
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
                .filter(item -> cpfNaNota == null || item.cpfNaNota() == cpfNaNota)
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

        String nome = paciente.getNome();
        String cpfCnpj = null;
        String pendencia = null;
        if (paciente.isCpfNaNota()) {
            if (paciente.getNotaCpfCnpj() != null) {
                cpfCnpj = paciente.getNotaCpfCnpj();
                if (paciente.getNotaNome() != null) {
                    nome = paciente.getNotaNome();
                }
            } else if (paciente.getCpf() != null && !paciente.getCpf().isBlank()) {
                cpfCnpj = paciente.getCpf();
            } else {
                pendencia = PENDENCIA_SEM_CPF;
            }
        }

        return new RelatorioNfseResponseDTO(
                nome,
                cpfCnpj,
                pagamento.getValor(),
                competencia,
                "Aulas de Pilates - Competência " + competencia,
                pacientesComNotaAnterior.contains(paciente.getId()),
                pagamento.getDataPagamento(),
                "",
                paciente.getId(),
                paciente.isCpfNaNota(),
                pendencia);
    }

    private void validarDadosParaEmissao(Pagamento pagamento, Paciente paciente) {
        if (paciente.getNome() == null || paciente.getNome().isBlank()) {
            throw new BusinessException("Nome do paciente deve estar preenchido para emissão da NFSE");
        }
        if (pagamento.getValor() == null || pagamento.getValor().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("ValorPago deve ser maior que zero para emissão da NFSE");
        }
        if (pagamento.getDataPagamento() == null) {
            throw new BusinessException("DataPagamento deve estar preenchida para emissão da NFSE");
        }
    }
}
