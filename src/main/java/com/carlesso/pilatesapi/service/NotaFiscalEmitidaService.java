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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

@Service
public class NotaFiscalEmitidaService {

    private final NotaFiscalEmitidaRepository repository;
    private final PacienteRepository pacienteRepository;

    public NotaFiscalEmitidaService(NotaFiscalEmitidaRepository repository,
                                    PacienteRepository pacienteRepository) {
        this.repository = repository;
        this.pacienteRepository = pacienteRepository;
    }

    @Transactional
    public NotaFiscalEmitidaResponseDTO registrar(NotaFiscalEmitidaRequestDTO dto) {
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
                    nova.setDataCriacao(LocalDateTime.now());
                    return nova;
                });

        if (nota.getId() != null) {
            nota.setDataAtualizacao(LocalDateTime.now());
        }
        nota.setNumeroNota(dto.numeroNota());
        nota.setDataEmissao(dto.dataEmissao());
        nota.setValor(dto.valor());
        nota.setObservacoes(dto.observacoes());

        return NotaFiscalEmitidaResponseDTO.from(repository.save(nota));
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
    }
}
