package com.carlesso.pilatesapi.service;

import com.carlesso.pilatesapi.dto.NotaFiscalEmitidaRequestDTO;
import com.carlesso.pilatesapi.dto.NotaFiscalEmitidaResponseDTO;
import com.carlesso.pilatesapi.entity.NotaFiscalEmitida;
import com.carlesso.pilatesapi.entity.Paciente;
import com.carlesso.pilatesapi.exception.BusinessException;
import com.carlesso.pilatesapi.exception.ResourceNotFoundException;
import com.carlesso.pilatesapi.repository.NotaFiscalEmitidaRepository;
import com.carlesso.pilatesapi.repository.PacienteRepository;
import com.carlesso.pilatesapi.util.CompetenciaUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Service
public class NotaFiscalEmitidaService {

    private static final Logger log = LoggerFactory.getLogger(NotaFiscalEmitidaService.class);

    private final NotaFiscalEmitidaRepository repository;
    private final PacienteRepository pacienteRepository;

    /**
     * Referência ao próprio bean (proxy transacional) para que {@link #upsert}
     * execute em uma nova transação a cada tentativa, permitindo o retry após
     * colisão da constraint única.
     */
    private NotaFiscalEmitidaService self;

    public NotaFiscalEmitidaService(NotaFiscalEmitidaRepository repository,
                                    PacienteRepository pacienteRepository) {
        this.repository = repository;
        this.pacienteRepository = pacienteRepository;
    }

    @Autowired
    void setSelf(@Lazy NotaFiscalEmitidaService self) {
        this.self = self;
    }

    /**
     * Registra (cria ou atualiza) a NFSE emitida de forma idempotente por
     * {@code (paciente, competência)}. Sob concorrência, dois inserts podem
     * colidir na constraint única; nesse caso a operação é repetida uma vez,
     * quando a busca passa a encontrar o registro já gravado e segue pelo
     * caminho de atualização — preservando a semântica idempotente (200).
     */
    public NotaFiscalEmitidaResponseDTO registrar(NotaFiscalEmitidaRequestDTO dto) {
        try {
            return self.upsert(dto);
        } catch (DataIntegrityViolationException e) {
            log.warn("Colisão de constraint ao registrar NFSE (pacienteId={}, competencia={}); repetindo como atualização",
                    dto.pacienteId(), dto.competencia());
            return self.upsert(dto);
        }
    }

    @Transactional
    public NotaFiscalEmitidaResponseDTO upsert(NotaFiscalEmitidaRequestDTO dto) {
        Paciente paciente = pacienteRepository.findByIdAndAtivoTrue(dto.pacienteId())
                .orElseThrow(() -> new ResourceNotFoundException("Paciente não encontrado: " + dto.pacienteId()));

        YearMonth periodo = CompetenciaUtils.parse(dto.competencia());
        LocalDate competencia = periodo.atDay(1);
        validar(dto);

        NotaFiscalEmitida nota = repository.findByPacienteIdAndCompetencia(paciente.getId(), competencia)
                .orElseGet(() -> {
                    NotaFiscalEmitida nova = new NotaFiscalEmitida();
                    nova.setPaciente(paciente);
                    nova.setCompetencia(competencia);
                    return nova;
                });

        boolean novo = nota.getId() == null;
        nota.setNumeroNota(dto.numeroNota());
        nota.setDataEmissao(dto.dataEmissao());
        nota.setValor(dto.valor());
        nota.setObservacoes(dto.observacoes());

        NotaFiscalEmitida salva = repository.save(nota);
        log.info("NFSE {}: id={}, pacienteId={}, competencia={}, numeroNota={}",
                novo ? "registrada" : "atualizada", salva.getId(), paciente.getId(), dto.competencia(), dto.numeroNota());
        return NotaFiscalEmitidaResponseDTO.from(salva);
    }

    @Transactional(readOnly = true)
    public List<NotaFiscalEmitidaResponseDTO> listarPorPaciente(Long pacienteId) {
        if (!pacienteRepository.existsByIdAndAtivoTrue(pacienteId)) {
            throw new ResourceNotFoundException("Paciente não encontrado: " + pacienteId);
        }
        return repository.findByPacienteIdOrderByCompetenciaDesc(pacienteId).stream()
                .map(NotaFiscalEmitidaResponseDTO::from)
                .toList();
    }

    private void validar(NotaFiscalEmitidaRequestDTO dto) {
        if (dto.valor() != null && dto.valor().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("valor da NFSE deve ser maior que zero quando informado");
        }
        if (dto.dataEmissao() != null && dto.dataEmissao().isAfter(LocalDate.now())) {
            throw new BusinessException("dataEmissao não pode ser futura");
        }
    }
}
