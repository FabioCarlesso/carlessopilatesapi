package com.carlesso.pilatesapi.service;

import com.carlesso.pilatesapi.dto.DisponibilidadeProfissionalRequestDTO;
import com.carlesso.pilatesapi.dto.DisponibilidadeProfissionalResponseDTO;
import com.carlesso.pilatesapi.dto.VerificacaoDisponibilidadeDTO;
import com.carlesso.pilatesapi.entity.DisponibilidadeProfissional;
import com.carlesso.pilatesapi.entity.Profissional;
import com.carlesso.pilatesapi.exception.BusinessException;
import com.carlesso.pilatesapi.exception.ConflictException;
import com.carlesso.pilatesapi.exception.ResourceNotFoundException;
import com.carlesso.pilatesapi.repository.DisponibilidadeProfissionalRepository;
import com.carlesso.pilatesapi.repository.ProfissionalRepository;
import com.carlesso.pilatesapi.util.VersaoGuard;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Disponibilidade semanal dos profissionais (#216).
 *
 * <p>A não sobreposição das faixas é checada aqui, e a checagem sofre a mesma corrida da capacidade (#106): dois
 * {@code POST} simultâneos de faixas que se cruzam leriam a tabela sem o outro e passariam juntos. A escrita trava o
 * profissional com {@code SELECT ... FOR UPDATE} antes de consultar, serializando as alterações de disponibilidade de
 * um mesmo profissional — o mesmo lock que a capacidade usa, então o padrão é um só.
 */
@Service
public class DisponibilidadeProfissionalService {

    /** Ordem da semana do {@link java.time.DayOfWeek} (segunda a domingo) e, no mesmo dia, hora de início. */
    private static final Comparator<DisponibilidadeProfissional> ORDEM_DA_GRADE = Comparator.comparing(
                    DisponibilidadeProfissional::getDiaSemana)
            .thenComparing(DisponibilidadeProfissional::getHoraInicio);

    /** Nenhum {@code BIGSERIAL} vale 0: na criação não há faixa própria a ignorar na checagem de sobreposição. */
    private static final long SEM_FAIXA_PROPRIA = 0L;

    private final DisponibilidadeProfissionalRepository repository;
    private final ProfissionalRepository profissionalRepository;

    public DisponibilidadeProfissionalService(
            DisponibilidadeProfissionalRepository repository, ProfissionalRepository profissionalRepository) {
        this.repository = repository;
        this.profissionalRepository = profissionalRepository;
    }

    @Transactional(readOnly = true)
    public List<DisponibilidadeProfissionalResponseDTO> listar(Long profissionalId) {
        exigirProfissionalExistente(profissionalId);
        return repository.findByProfissionalId(profissionalId).stream()
                .sorted(ORDEM_DA_GRADE)
                .map(DisponibilidadeProfissionalResponseDTO::from)
                .toList();
    }

    @Transactional
    public DisponibilidadeProfissionalResponseDTO criar(
            Long profissionalId, DisponibilidadeProfissionalRequestDTO dto) {
        Profissional profissional = travarProfissionalAtivo(profissionalId);
        validarFaixa(dto);
        exigirSemSobreposicao(profissionalId, dto, SEM_FAIXA_PROPRIA);
        DisponibilidadeProfissional disponibilidade = new DisponibilidadeProfissional();
        disponibilidade.setProfissional(profissional);
        aplicar(dto, disponibilidade);
        return DisponibilidadeProfissionalResponseDTO.from(repository.save(disponibilidade));
    }

    /** Substituição completa da faixa; a própria faixa não conta como conflito. */
    @Transactional
    public DisponibilidadeProfissionalResponseDTO atualizar(Long id, DisponibilidadeProfissionalRequestDTO dto) {
        DisponibilidadeProfissional disponibilidade = encontrar(id);
        VersaoGuard.exigirVersaoAtual(
                dto.version(), disponibilidade.getVersion(), DisponibilidadeProfissional.class, id);
        Long profissionalId = disponibilidade.getProfissional().getId();
        travarProfissionalAtivo(profissionalId);
        validarFaixa(dto);
        exigirSemSobreposicao(profissionalId, dto, id);
        aplicar(dto, disponibilidade);
        return DisponibilidadeProfissionalResponseDTO.from(repository.saveAndFlush(disponibilidade));
    }

    /**
     * Exclusão física: a faixa é configuração, não histórico, e nenhum outro registro a referencia. Vale também para
     * profissional inativo — limpar a grade de quem saiu não deve ser recusado.
     */
    @Transactional
    public void excluir(Long id) {
        repository.delete(encontrar(id));
    }

    /**
     * O horário cai dentro de alguma faixa do dia da semana de {@code data}? Profissional sem nenhuma faixa responde
     * {@code true}: sem configuração não há aviso. Horários são locais do estúdio, sem conversão de fuso.
     */
    @Transactional(readOnly = true)
    public VerificacaoDisponibilidadeDTO verificar(Long profissionalId, LocalDate data, LocalTime horario) {
        exigirProfissionalExistente(profissionalId);
        if (!repository.existsByProfissionalId(profissionalId)) {
            return new VerificacaoDisponibilidadeDTO(true);
        }
        boolean dentro = repository.findByProfissionalIdAndDiaSemana(profissionalId, data.getDayOfWeek()).stream()
                .anyMatch(faixa -> faixa.contem(horario));
        return new VerificacaoDisponibilidadeDTO(dentro);
    }

    private Profissional travarProfissionalAtivo(Long profissionalId) {
        Profissional profissional = profissionalRepository
                .findByIdForUpdate(profissionalId)
                .orElseThrow(() -> new ResourceNotFoundException("Profissional não encontrado: " + profissionalId));
        if (!profissional.isAtivo()) {
            throw new BusinessException(
                    "Profissional inativo não pode ter disponibilidade cadastrada ou alterada: " + profissionalId);
        }
        return profissional;
    }

    private void exigirProfissionalExistente(Long profissionalId) {
        if (!profissionalRepository.existsById(profissionalId)) {
            throw new ResourceNotFoundException("Profissional não encontrado: " + profissionalId);
        }
    }

    /** @throws BusinessException mapeada para {@code 422}, como pede a #216 */
    private void validarFaixa(DisponibilidadeProfissionalRequestDTO dto) {
        if (!dto.horaInicio().isBefore(dto.horaFim())) {
            throw new BusinessException("horaInicio deve ser anterior a horaFim");
        }
    }

    private void exigirSemSobreposicao(Long profissionalId, DisponibilidadeProfissionalRequestDTO dto, long ignorarId) {
        if (repository.existeSobreposicao(
                profissionalId, dto.diaSemana(), dto.horaInicio(), dto.horaFim(), ignorarId)) {
            throw new ConflictException("A faixa %s %s–%s se sobrepõe a outra disponibilidade do profissional"
                    .formatted(dto.diaSemana(), dto.horaInicio(), dto.horaFim()));
        }
    }

    private void aplicar(DisponibilidadeProfissionalRequestDTO dto, DisponibilidadeProfissional disponibilidade) {
        disponibilidade.setDiaSemana(dto.diaSemana());
        disponibilidade.setHoraInicio(dto.horaInicio());
        disponibilidade.setHoraFim(dto.horaFim());
    }

    private DisponibilidadeProfissional encontrar(Long id) {
        return repository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Disponibilidade não encontrada: " + id));
    }
}
