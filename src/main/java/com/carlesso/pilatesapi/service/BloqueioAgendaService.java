package com.carlesso.pilatesapi.service;

import com.carlesso.pilatesapi.dto.BloqueioAgendaRequestDTO;
import com.carlesso.pilatesapi.dto.BloqueioAgendaResponseDTO;
import com.carlesso.pilatesapi.entity.BloqueioAgenda;
import com.carlesso.pilatesapi.entity.enums.TipoBloqueio;
import com.carlesso.pilatesapi.exception.ResourceNotFoundException;
import com.carlesso.pilatesapi.repository.BloqueioAgendaRepository;
import com.carlesso.pilatesapi.util.PeriodoGuard;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BloqueioAgendaService {

    private final BloqueioAgendaRepository repository;
    private final AulaService aulaService;

    public BloqueioAgendaService(BloqueioAgendaRepository repository, AulaService aulaService) {
        this.repository = repository;
        this.aulaService = aulaService;
    }

    @Transactional
    public BloqueioAgendaResponseDTO criar(BloqueioAgendaRequestDTO dto) {
        validar(dto);
        BloqueioAgenda bloqueio = new BloqueioAgenda();
        aplicar(dto, bloqueio);
        BloqueioAgenda salvo = repository.save(bloqueio);
        aulaService.sincronizarFeriado(null, salvo);
        return BloqueioAgendaResponseDTO.from(salvo);
    }

    /**
     * Bloqueios que intersectam o período.
     *
     * <p>Reusa o {@link PeriodoGuard} das listagens de agenda — mesmas regras de período obrigatório e fechado, mesmas
     * mensagens de erro —, mas com o teto de {@link PeriodoGuard#LIMITE_DIAS_BLOQUEIOS} dias em vez do trimestre da
     * agenda: feriados são conferidos para o ano inteiro de uma vez, e o volume que justifica o teto curto de
     * {@code GET /aulas} não existe aqui.
     */
    @Transactional(readOnly = true)
    public List<BloqueioAgendaResponseDTO> listarPorPeriodo(LocalDate inicio, LocalDate fim) {
        PeriodoGuard.exigirPeriodoValido(inicio, fim, PeriodoGuard.LIMITE_DIAS_BLOQUEIOS);
        return repository.findQueIntersectamPeriodo(inicio, fim).stream()
                .map(BloqueioAgendaResponseDTO::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public BloqueioAgendaResponseDTO buscarPorId(Long id) {
        return BloqueioAgendaResponseDTO.from(encontrar(id));
    }

    /** Substituição completa: o {@code PUT} redefine inclusive a faixa de horário, que some se vier ausente. */
    @Transactional
    public BloqueioAgendaResponseDTO atualizar(Long id, BloqueioAgendaRequestDTO dto) {
        validar(dto);
        BloqueioAgenda bloqueio = encontrar(id);
        aplicar(dto, bloqueio);
        // Desfaz o que o bloqueio marcou com o período e o tipo antigos e remarca com os novos (#244).
        aulaService.sincronizarFeriado(bloqueio.getId(), bloqueio);
        return BloqueioAgendaResponseDTO.from(bloqueio);
    }

    /**
     * Exclusão física: um feriado cadastrado por engano não precisa sobreviver como histórico. As aulas que o feriado
     * marcou voltam a não realizadas antes (#244), e as realizadas de verdade não são tocadas.
     */
    @Transactional
    public void excluir(Long id) {
        BloqueioAgenda bloqueio = encontrar(id);
        aulaService.sincronizarFeriado(id, null);
        repository.delete(bloqueio);
    }

    private void aplicar(BloqueioAgendaRequestDTO dto, BloqueioAgenda bloqueio) {
        bloqueio.setDataInicio(dto.dataInicio());
        bloqueio.setDataFim(dto.dataFim());
        bloqueio.setHoraInicio(dto.horaInicio());
        bloqueio.setHoraFim(dto.horaFim());
        bloqueio.setMotivo(dto.motivo().trim());
        bloqueio.setTipo(dto.tipo() != null ? dto.tipo() : TipoBloqueio.OUTRO);
    }

    /**
     * Regras que a Bean Validation do DTO não alcança por envolverem mais de um campo.
     *
     * @throws IllegalArgumentException mapeada para {@code 400} pelo GlobalExceptionHandler
     */
    private void validar(BloqueioAgendaRequestDTO dto) {
        if (dto.dataFim().isBefore(dto.dataInicio())) {
            throw new IllegalArgumentException("dataFim não pode ser anterior a dataInicio");
        }
        if ((dto.horaInicio() == null) != (dto.horaFim() == null)) {
            throw new IllegalArgumentException(
                    "horaInicio e horaFim devem ser informadas juntas; omita as duas para bloquear o dia inteiro");
        }
        if (dto.horaInicio() != null && !dto.horaFim().isAfter(dto.horaInicio())) {
            throw new IllegalArgumentException("horaFim deve ser posterior a horaInicio");
        }
    }

    private BloqueioAgenda encontrar(Long id) {
        return repository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bloqueio de agenda não encontrado: " + id));
    }
}
