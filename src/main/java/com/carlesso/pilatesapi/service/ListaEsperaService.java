package com.carlesso.pilatesapi.service;

import com.carlesso.pilatesapi.dto.ListaEsperaRequestDTO;
import com.carlesso.pilatesapi.dto.ListaEsperaResponseDTO;
import com.carlesso.pilatesapi.entity.ListaEspera;
import com.carlesso.pilatesapi.entity.Paciente;
import com.carlesso.pilatesapi.exception.ConflictException;
import com.carlesso.pilatesapi.exception.ResourceNotFoundException;
import com.carlesso.pilatesapi.repository.ListaEsperaRepository;
import com.carlesso.pilatesapi.repository.PacienteRepository;
import com.carlesso.pilatesapi.util.PacienteGuard;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListaEsperaService {

    private final ListaEsperaRepository repository;
    private final PacienteRepository pacienteRepository;

    public ListaEsperaService(ListaEsperaRepository repository, PacienteRepository pacienteRepository) {
        this.repository = repository;
        this.pacienteRepository = pacienteRepository;
    }

    /**
     * Entra na fila. {@code dataEntrada} não é escrita aqui nem lida do corpo: o {@code @PrePersist} da entidade a
     * define, e é ela que determina a ordem de chegada.
     */
    @Transactional
    public ListaEsperaResponseDTO criar(ListaEsperaRequestDTO dto) {
        exigirFaixaValida(dto.horaInicio(), dto.horaFim());

        Paciente paciente = pacienteRepository
                .findById(dto.pacienteId())
                .orElseThrow(() -> new ResourceNotFoundException("Paciente não encontrado: " + dto.pacienteId()));
        PacienteGuard.exigirAtivo(paciente);

        if (repository.existeAtivaParaPacienteNaFaixa(
                paciente.getId(), dto.diaSemana(), dto.horaInicio(), dto.horaFim())) {
            throw new ConflictException("Paciente já está na lista de espera para esse dia e faixa de horário");
        }

        ListaEspera entrada = new ListaEspera();
        entrada.setPaciente(paciente);
        entrada.setDiaSemana(dto.diaSemana());
        entrada.setHoraInicio(dto.horaInicio());
        entrada.setHoraFim(dto.horaFim());
        entrada.setObservacao(normalizar(dto.observacao()));
        return ListaEsperaResponseDTO.from(repository.save(entrada));
    }

    /**
     * Fila ativa em ordem de chegada. Todos os filtros são opcionais, mas a faixa horária só vale inteira: meia faixa
     * não descreve nenhum interesse consultável.
     */
    @Transactional(readOnly = true)
    public List<ListaEsperaResponseDTO> listar(
            DayOfWeek diaSemana, LocalTime horaInicio, LocalTime horaFim, Long pacienteId) {
        if ((horaInicio == null) != (horaFim == null)) {
            throw new IllegalArgumentException(
                    "horaInicio e horaFim devem ser informadas juntas; omita as duas para não filtrar por horário");
        }
        if (horaInicio != null) {
            exigirFaixaValida(horaInicio, horaFim);
        }
        return repository.buscarAtivas(diaSemana, horaInicio, horaFim, pacienteId).stream()
                .map(ListaEsperaResponseDTO::from)
                .toList();
    }

    /**
     * Saída da fila por remoção lógica: o contrato HTTP é {@code DELETE}, mas apagar a linha perderia o registro de
     * quem foi chamado a partir da lista — a única memória de que a espera funcionou.
     */
    @Transactional
    public void remover(Long id) {
        ListaEspera entrada = repository
                .findAtivaById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Entrada da lista de espera não encontrada: " + id));
        entrada.setAtivo(false);
    }

    /**
     * @throws IllegalArgumentException mapeada para {@code 400} pelo GlobalExceptionHandler
     */
    private void exigirFaixaValida(LocalTime horaInicio, LocalTime horaFim) {
        if (!horaFim.isAfter(horaInicio)) {
            throw new IllegalArgumentException("horaFim deve ser posterior a horaInicio");
        }
    }

    private String normalizar(String observacao) {
        if (observacao == null || observacao.isBlank()) {
            return null;
        }
        return observacao.trim();
    }
}
