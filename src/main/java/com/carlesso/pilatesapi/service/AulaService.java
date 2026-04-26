package com.carlesso.pilatesapi.service;

import com.carlesso.pilatesapi.dto.AulaResponseDTO;
import com.carlesso.pilatesapi.entity.Aula;
import com.carlesso.pilatesapi.entity.Pagamento;
import com.carlesso.pilatesapi.entity.Profissional;
import com.carlesso.pilatesapi.entity.enums.StatusPagamento;
import com.carlesso.pilatesapi.repository.AulaRepository;
import com.carlesso.pilatesapi.repository.ProfissionalRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class AulaService {

    private final AulaRepository aulaRepository;
    private final ProfissionalRepository profissionalRepository;

    public AulaService(AulaRepository aulaRepository, ProfissionalRepository profissionalRepository) {
        this.aulaRepository = aulaRepository;
        this.profissionalRepository = profissionalRepository;
    }

    @Transactional
    public List<Aula> gerarAulas(Pagamento pagamento) {
        if (pagamento.getStatus() != StatusPagamento.PAGO) {
            throw new IllegalStateException("Aulas só podem ser geradas para pagamentos com status PAGO");
        }
        if (!pagamento.getPaciente().isAtivo()) {
            throw new IllegalStateException("Paciente inativo não pode ter aulas geradas");
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

    public List<AulaResponseDTO> buscarPorPaciente(Long pacienteId) {
        return aulaRepository.findByPacienteIdOrderByData(pacienteId)
                .stream()
                .map(AulaResponseDTO::from)
                .toList();
    }

    public List<AulaResponseDTO> buscarPorPagamento(Long pagamentoId) {
        return aulaRepository.findByPagamentoIdOrderByData(pagamentoId)
                .stream()
                .map(AulaResponseDTO::from)
                .toList();
    }

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
            throw new IllegalStateException("Aula já foi marcada como realizada");
        }
        if (profissionalId != null) {
            Profissional profissional = profissionalRepository.findById(profissionalId)
                    .orElseThrow(() -> new EntityNotFoundException("Profissional não encontrado: " + profissionalId));
            if (!profissional.isAtivo()) {
                throw new IllegalStateException("Profissional inativo não pode ser vinculado à aula");
            }
            aula.setProfissional(profissional);
        }
        aula.setRealizada(true);
        return AulaResponseDTO.from(aula);
    }

    private Aula encontrar(Long id) {
        return aulaRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Aula não encontrada: " + id));
    }
}
