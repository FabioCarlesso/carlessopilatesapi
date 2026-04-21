package com.carlesso.pilatesapi.service;

import com.carlesso.pilatesapi.dto.PagamentoRequestDTO;
import com.carlesso.pilatesapi.dto.PagamentoResponseDTO;
import com.carlesso.pilatesapi.entity.Paciente;
import com.carlesso.pilatesapi.entity.Pagamento;
import com.carlesso.pilatesapi.entity.Plano;
import com.carlesso.pilatesapi.entity.enums.StatusPagamento;
import com.carlesso.pilatesapi.repository.PacienteRepository;
import com.carlesso.pilatesapi.repository.PagamentoRepository;
import com.carlesso.pilatesapi.repository.PlanoRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class PagamentoService {

    private final PagamentoRepository pagamentoRepository;
    private final PacienteRepository pacienteRepository;
    private final PlanoRepository planoRepository;
    private final AulaService aulaService;

    public PagamentoService(PagamentoRepository pagamentoRepository,
                            PacienteRepository pacienteRepository,
                            PlanoRepository planoRepository,
                            AulaService aulaService) {
        this.pagamentoRepository = pagamentoRepository;
        this.pacienteRepository = pacienteRepository;
        this.planoRepository = planoRepository;
        this.aulaService = aulaService;
    }

    @Transactional
    public PagamentoResponseDTO criar(PagamentoRequestDTO dto) {
        Paciente paciente = pacienteRepository.findById(dto.pacienteId())
                .orElseThrow(() -> new EntityNotFoundException("Paciente não encontrado: " + dto.pacienteId()));

        if (!paciente.isAtivo()) {
            throw new IllegalStateException("Paciente inativo não pode receber novas cobranças");
        }

        Plano plano = planoRepository.findById(dto.planoId())
                .orElseThrow(() -> new EntityNotFoundException("Plano não encontrado: " + dto.planoId()));

        if (dto.valor().compareTo(plano.getValor()) < 0) {
            throw new IllegalArgumentException(
                    "Valor do pagamento (R$ %s) não pode ser menor que o valor do plano (R$ %s)"
                            .formatted(dto.valor(), plano.getValor())
            );
        }

        LocalDate periodoFim = dto.periodoInicio().plusMonths(plano.getTipo().getMeses()).minusDays(1);

        if (pagamentoRepository.existsByPlanoAndPeriodoInicio(plano, dto.periodoInicio())) {
            throw new IllegalStateException(
                    "Já existe um pagamento para este plano no período iniciado em " + dto.periodoInicio()
            );
        }

        Pagamento pagamento = new Pagamento();
        pagamento.setPaciente(paciente);
        pagamento.setPlano(plano);
        pagamento.setValor(dto.valor());
        pagamento.setDataVencimento(dto.dataVencimento());
        pagamento.setPeriodoInicio(dto.periodoInicio());
        pagamento.setPeriodoFim(periodoFim);

        return PagamentoResponseDTO.from(pagamentoRepository.save(pagamento));
    }

    @Transactional
    public PagamentoResponseDTO pagar(Long id, LocalDate dataPagamento) {
        Pagamento pagamento = encontrar(id);

        if (pagamento.getStatus() == StatusPagamento.PAGO) {
            throw new IllegalStateException("Pagamento já foi confirmado");
        }

        pagamento.setStatus(StatusPagamento.PAGO);
        pagamento.setDataPagamento(dataPagamento != null ? dataPagamento : LocalDate.now());
        pagamentoRepository.save(pagamento);

        aulaService.gerarAulas(pagamento);

        return PagamentoResponseDTO.from(pagamento);
    }

    public PagamentoResponseDTO buscarPorId(Long id) {
        return PagamentoResponseDTO.from(encontrar(id));
    }

    public List<PagamentoResponseDTO> listarPorPaciente(Long pacienteId) {
        return pagamentoRepository.findByPacienteId(pacienteId)
                .stream()
                .map(PagamentoResponseDTO::from)
                .toList();
    }

    @Transactional
    public int atualizarVencidos() {
        List<Pagamento> vencidos = pagamentoRepository
                .findByStatusAndDataVencimentoBefore(StatusPagamento.PENDENTE, LocalDate.now());
        vencidos.forEach(p -> p.setStatus(StatusPagamento.VENCIDO));
        return vencidos.size();
    }

    @Transactional
    public int gerarCobrancasFuturas() {
        List<Plano> planosAtivos = planoRepository.findByAtivoTrue();
        int count = 0;

        for (Plano plano : planosAtivos) {
            if (!plano.getPaciente().isAtivo()) continue;

            var ultimoPagamento = pagamentoRepository.findTopByPlanoOrderByPeriodoFimDesc(plano);

            LocalDate periodoInicio;
            if (ultimoPagamento.isEmpty()) {
                periodoInicio = LocalDate.now();
            } else {
                var ultimo = ultimoPagamento.get();
                if (LocalDate.now().isBefore(ultimo.getPeriodoFim().minusDays(6))) continue;
                periodoInicio = ultimo.getPeriodoFim().plusDays(1);
                if (pagamentoRepository.existsByPlanoAndPeriodoInicio(plano, periodoInicio)) continue;
            }

            LocalDate periodoFim = periodoInicio.plusMonths(plano.getTipo().getMeses()).minusDays(1);

            Pagamento novoPagamento = new Pagamento();
            novoPagamento.setPaciente(plano.getPaciente());
            novoPagamento.setPlano(plano);
            novoPagamento.setValor(plano.getValor());
            novoPagamento.setDataVencimento(periodoInicio.plusDays(10));
            novoPagamento.setPeriodoInicio(periodoInicio);
            novoPagamento.setPeriodoFim(periodoFim);

            pagamentoRepository.save(novoPagamento);
            count++;
        }

        return count;
    }

    private Pagamento encontrar(Long id) {
        return pagamentoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Pagamento não encontrado: " + id));
    }
}
