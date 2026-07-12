package com.carlesso.pilatesapi.service;

import com.carlesso.pilatesapi.config.AppProperties;
import com.carlesso.pilatesapi.dto.PagamentoRequestDTO;
import com.carlesso.pilatesapi.dto.PagamentoResponseDTO;
import com.carlesso.pilatesapi.entity.Paciente;
import com.carlesso.pilatesapi.entity.Pagamento;
import com.carlesso.pilatesapi.entity.Plano;
import com.carlesso.pilatesapi.entity.enums.StatusPagamento;
import com.carlesso.pilatesapi.exception.BusinessException;
import com.carlesso.pilatesapi.exception.ConflictException;
import com.carlesso.pilatesapi.exception.ResourceNotFoundException;
import com.carlesso.pilatesapi.repository.PacienteRepository;
import com.carlesso.pilatesapi.repository.PagamentoRepository;
import com.carlesso.pilatesapi.repository.PlanoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class PagamentoService {

    private static final Logger log = LoggerFactory.getLogger(PagamentoService.class);

    private final PagamentoRepository pagamentoRepository;
    private final PacienteRepository pacienteRepository;
    private final PlanoRepository planoRepository;
    private final AulaService aulaService;
    private final AppProperties appProperties;

    public PagamentoService(PagamentoRepository pagamentoRepository,
                            PacienteRepository pacienteRepository,
                            PlanoRepository planoRepository,
                            AulaService aulaService,
                            AppProperties appProperties) {
        this.pagamentoRepository = pagamentoRepository;
        this.pacienteRepository = pacienteRepository;
        this.planoRepository = planoRepository;
        this.aulaService = aulaService;
        this.appProperties = appProperties;
    }

    @Transactional
    public PagamentoResponseDTO criar(PagamentoRequestDTO dto) {
        Paciente paciente = pacienteRepository.findById(dto.pacienteId())
                .orElseThrow(() -> new ResourceNotFoundException("Paciente não encontrado: " + dto.pacienteId()));

        if (!paciente.isAtivo()) {
            throw new BusinessException("Paciente inativo não pode receber novas cobranças");
        }

        Plano plano = planoRepository.findById(dto.planoId())
                .orElseThrow(() -> new ResourceNotFoundException("Plano não encontrado: " + dto.planoId()));

        if (dto.valor().compareTo(plano.getValor()) < 0) {
            throw new IllegalArgumentException(
                    "Valor do pagamento (R$ %s) não pode ser menor que o valor do plano (R$ %s)"
                            .formatted(dto.valor(), plano.getValor())
            );
        }

        LocalDate periodoFim = dto.periodoInicio().plusMonths(plano.getTipo().getMeses()).minusDays(1);

        if (pagamentoRepository.existsByPlanoAndPeriodoInicio(plano, dto.periodoInicio())) {
            throw new ConflictException(
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

        Pagamento salvo = pagamentoRepository.save(pagamento);
        log.info("Cobrança criada: pagamentoId={}, pacienteId={}, planoId={}, valor={}, vencimento={}",
                salvo.getId(), paciente.getId(), plano.getId(), salvo.getValor(), salvo.getDataVencimento());
        return PagamentoResponseDTO.from(salvo);
    }

    @Transactional
    public PagamentoResponseDTO pagar(Long id, LocalDate dataPagamento) {
        Pagamento pagamento = encontrar(id);

        if (pagamento.getStatus() == StatusPagamento.PAGO) {
            log.warn("Confirmação de pagamento rejeitada (já confirmado): pagamentoId={}", id);
            throw new ConflictException("Pagamento já foi confirmado");
        }

        pagamento.setStatus(StatusPagamento.PAGO);
        pagamento.setDataPagamento(dataPagamento != null ? dataPagamento : LocalDate.now());
        pagamentoRepository.save(pagamento);

        aulaService.gerarAulas(pagamento);

        log.info("Pagamento confirmado: pagamentoId={}, pacienteId={}, valor={}, dataPagamento={}",
                pagamento.getId(), pagamento.getPaciente().getId(), pagamento.getValor(), pagamento.getDataPagamento());
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

        int vencimentoDias = appProperties.cobranca().vencimentoDias();
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
            novoPagamento.setDataVencimento(periodoInicio.plusDays(vencimentoDias));
            novoPagamento.setPeriodoInicio(periodoInicio);
            novoPagamento.setPeriodoFim(periodoFim);

            pagamentoRepository.save(novoPagamento);
            count++;
        }

        return count;
    }

    private Pagamento encontrar(Long id) {
        return pagamentoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pagamento não encontrado: " + id));
    }
}
