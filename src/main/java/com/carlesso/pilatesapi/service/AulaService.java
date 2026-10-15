package com.carlesso.pilatesapi.service;

import com.carlesso.pilatesapi.dto.AulaResponseDTO;
import com.carlesso.pilatesapi.entity.Aula;
import com.carlesso.pilatesapi.entity.Pagamento;
import com.carlesso.pilatesapi.entity.Profissional;
import com.carlesso.pilatesapi.entity.enums.StatusPagamento;
import com.carlesso.pilatesapi.exception.BusinessException;
import com.carlesso.pilatesapi.exception.ConflictException;
import com.carlesso.pilatesapi.exception.ResourceNotFoundException;
import com.carlesso.pilatesapi.repository.AulaRepository;
import com.carlesso.pilatesapi.repository.ProfissionalRepository;
import com.carlesso.pilatesapi.util.PaginacaoGuard;
import com.carlesso.pilatesapi.util.PeriodoGuard;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AulaService {

    static final Set<String> CAMPOS_ORDENAVEIS_POR_PACIENTE = Set.of("data", "realizada");

    private final AulaRepository aulaRepository;
    private final ProfissionalRepository profissionalRepository;

    public AulaService(AulaRepository aulaRepository, ProfissionalRepository profissionalRepository) {
        this.aulaRepository = aulaRepository;
        this.profissionalRepository = profissionalRepository;
    }

    @Transactional
    public List<Aula> gerarAulas(Pagamento pagamento) {
        if (pagamento.getStatus() != StatusPagamento.PAGO) {
            throw new BusinessException("Aulas só podem ser geradas para pagamentos com status PAGO");
        }
        if (!pagamento.getPaciente().isAtivo()) {
            throw new BusinessException("Paciente inativo não pode ter aulas geradas");
        }

        var diasSemana = pagamento.getPlano().getDiasSemana();
        LocalDate inicio = pagamento.getPeriodoInicio();
        LocalDate fim = pagamento.getPeriodoFim();

        List<Aula> aulas = new ArrayList<>();
        LocalDate data = inicio;
        while (!data.isAfter(fim)) {
            if (diasSemana.contains(data.getDayOfWeek())) {
                if (!aulaRepository.existsByPacienteAndData(pagamento.getPaciente(), data)) {
                    Aula aula = new Aula();
                    aula.setPaciente(pagamento.getPaciente());
                    aula.setPagamento(pagamento);
                    aula.setData(data);
                    aulas.add(aula);
                }
            }
            data = data.plusDays(1);
        }

        return aulaRepository.saveAll(aulas);
    }

    @Transactional(readOnly = true)
    public Page<AulaResponseDTO> buscarPorPaciente(Long pacienteId, Pageable pageable) {
        return aulaRepository.findByPacienteId(
                pacienteId,
                PaginacaoGuard.comDesempatePorId(pageable, CAMPOS_ORDENAVEIS_POR_PACIENTE, Sort.Direction.DESC));
    }

    @Transactional(readOnly = true)
    public List<AulaResponseDTO> listarPorPeriodo(
            LocalDate inicio, LocalDate fim, Long profissionalId, Long pacienteId, Boolean realizada) {
        PeriodoGuard.exigirPeriodoValido(inicio, fim);
        List<AulaResponseDTO> aulas =
                aulaRepository.findAgendaPorPeriodo(inicio, fim, profissionalId, pacienteId, realizada);
        PeriodoGuard.exigirVolumeSuportado(aulas.size());
        return aulas;
    }

    @Transactional(readOnly = true)
    public List<AulaResponseDTO> buscarPorPagamento(Long pagamentoId) {
        return aulaRepository.findByPagamentoIdOrderByData(pagamentoId).stream()
                .map(AulaResponseDTO::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AulaResponseDTO buscarPorId(Long id) {
        return AulaResponseDTO.from(encontrar(id));
    }

    @Transactional
    public AulaResponseDTO realizarAula(Long id) {
        return realizarAula(id, null);
    }

    @Transactional
    public AulaResponseDTO realizarAula(Long id, Long profissionalId) {
        Aula aula = encontrar(id);
        if (aula.isRealizada()) {
            throw new ConflictException("Aula já foi marcada como realizada");
        }
        Profissional profissional = resolverProfissional(profissionalId);
        // Sem profissionalId no corpo, o vínculo previamente atribuído é preservado.
        if (profissional != null) {
            aula.setProfissional(profissional);
        }
        aula.setRealizada(true);
        return AulaResponseDTO.from(aula);
    }

    @Transactional
    public AulaResponseDTO atribuirProfissional(Long id, Long profissionalId) {
        Aula aula = encontrar(id);
        if (aula.isRealizada()) {
            throw new ConflictException("Aula já realizada não pode ter o profissional alterado");
        }
        aula.setProfissional(resolverProfissional(profissionalId));
        return AulaResponseDTO.from(aula);
    }

    /**
     * Troca a data de uma aula ainda não realizada.
     *
     * <p>A nova data não é confrontada com {@code periodoInicio}/{@code periodoFim} do pagamento de origem, e pode
     * estar no passado: a decisão de negócio da #170 foi deixar a recepção remarcar livremente, inclusive
     * registrando reposições já ocorridas. O {@code pagamento_id} nunca muda — a aula continua pertencendo ao
     * pagamento que a gerou, mesmo caindo em outro mês, e é por isso que o repasse do profissional pode contá-la
     * num período diferente do que a pagou.
     */
    @Transactional
    public AulaResponseDTO remarcar(Long id, LocalDate novaData) {
        Aula aula = encontrar(id);
        if (aula.isRealizada()) {
            throw new ConflictException("Aula já realizada não pode ser remarcada");
        }
        if (novaData.equals(aula.getData())) {
            return AulaResponseDTO.from(aula);
        }
        if (aulaRepository.existsByPacienteAndData(aula.getPaciente(), novaData)) {
            throw new ConflictException(conflitoDeData(novaData));
        }
        aula.setData(novaData);
        // A checagem acima não fecha a janela entre o exists e o update — a constraint (paciente_id, data) é a única
        // garantia real. Sem este flush a violação só apareceria no commit, longe deste catch, e escaparia como 500.
        try {
            aulaRepository.saveAndFlush(aula);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException(conflitoDeData(novaData));
        }
        return AulaResponseDTO.from(aula);
    }

    private String conflitoDeData(LocalDate data) {
        return "Paciente já possui aula na data " + data;
    }

    private Profissional resolverProfissional(Long profissionalId) {
        if (profissionalId == null) {
            return null;
        }
        Profissional profissional = profissionalRepository
                .findById(profissionalId)
                .orElseThrow(() -> new ResourceNotFoundException("Profissional não encontrado: " + profissionalId));
        if (!profissional.isAtivo()) {
            throw new BusinessException("Profissional inativo não pode ser vinculado à aula");
        }
        return profissional;
    }

    private Aula encontrar(Long id) {
        return aulaRepository
                .findByIdAndPacienteAtivoTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Aula não encontrada: " + id));
    }
}
