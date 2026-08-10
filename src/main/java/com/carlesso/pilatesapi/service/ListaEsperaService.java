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
import com.carlesso.pilatesapi.util.PeriodoGuard;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListaEsperaService {

    private static final String DUPLICIDADE = "Paciente já está na lista de espera para esse dia e faixa de horário";

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
            throw new ConflictException(DUPLICIDADE);
        }

        ListaEspera entrada = new ListaEspera();
        entrada.setPaciente(paciente);
        entrada.setDiaSemana(dto.diaSemana());
        entrada.setHoraInicio(dto.horaInicio());
        entrada.setHoraFim(dto.horaFim());
        entrada.setObservacao(normalizar(dto.observacao()));

        // A checagem acima não fecha a janela entre o exists e o insert: duas requisições simultâneas passam as duas.
        // O índice parcial da V37 é a única garantia real, e sem este flush a violação só apareceria no commit, longe
        // deste catch, escapando como 500. Mesmo padrão de AulaService.remarcar (#170).
        try {
            return ListaEsperaResponseDTO.from(repository.saveAndFlush(entrada));
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException(DUPLICIDADE);
        }
    }

    @Transactional(readOnly = true)
    public ListaEsperaResponseDTO buscarPorId(Long id) {
        return ListaEsperaResponseDTO.from(encontrarAtiva(id));
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
        List<ListaEspera> fila = repository.buscarAtivas(diaSemana, horaInicio, horaFim, pacienteId);
        exigirVolumeSuportado(fila.size());
        return fila.stream().map(ListaEsperaResponseDTO::from).toList();
    }

    /**
     * Saída da fila por remoção lógica: o contrato HTTP é {@code DELETE}, mas apagar a linha perderia o registro de
     * quem foi chamado a partir da lista — a única memória de que a espera funcionou.
     */
    @Transactional
    public void remover(Long id) {
        encontrarAtiva(id).setAtivo(false);
    }

    private ListaEspera encontrarAtiva(Long id) {
        return repository
                .findAtivaById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Entrada da lista de espera não encontrada: " + id));
    }

    /**
     * @throws IllegalArgumentException mapeada para {@code 400} pelo GlobalExceptionHandler
     */
    private void exigirFaixaValida(LocalTime horaInicio, LocalTime horaFim) {
        if (!horaFim.isAfter(horaInicio)) {
            throw new IllegalArgumentException("horaFim deve ser posterior a horaInicio");
        }
    }

    /**
     * Recusa respostas grandes demais para um payload único, como as listagens de agenda.
     *
     * <p>Reusa o número de {@link PeriodoGuard#LIMITE_REGISTROS_AGENDA} — o teto é a capacidade de uma resposta sem
     * paginação, e essa não muda de rota para rota —, mas não o guarda em si: a mensagem dele manda reduzir o
     * intervalo, e aqui não existe período a reduzir. A checagem vem depois da consulta pelo mesmo motivo documentado
     * lá: um {@code count} antes dobraria a ida ao banco em toda requisição para proteger o caso raro.
     *
     * @throws IllegalArgumentException mapeada para {@code 400} pelo GlobalExceptionHandler
     */
    private void exigirVolumeSuportado(int quantidade) {
        if (quantidade > PeriodoGuard.LIMITE_REGISTROS_AGENDA) {
            throw new IllegalArgumentException("Lista de espera limitada a " + PeriodoGuard.LIMITE_REGISTROS_AGENDA
                    + " registros por consulta; aplique filtros");
        }
    }

    private String normalizar(String observacao) {
        if (observacao == null || observacao.isBlank()) {
            return null;
        }
        return observacao.trim();
    }
}
