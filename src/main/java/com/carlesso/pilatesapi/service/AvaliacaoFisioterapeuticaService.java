package com.carlesso.pilatesapi.service;

import com.carlesso.pilatesapi.dto.AvaliacaoFisioterapeuticaRequestDTO;
import com.carlesso.pilatesapi.dto.AvaliacaoFisioterapeuticaResponseDTO;
import com.carlesso.pilatesapi.dto.AvaliacaoFisioterapeuticaUpdateDTO;
import com.carlesso.pilatesapi.entity.AvaliacaoFisioterapeutica;
import com.carlesso.pilatesapi.entity.Paciente;
import com.carlesso.pilatesapi.exception.ResourceNotFoundException;
import com.carlesso.pilatesapi.repository.AvaliacaoFisioterapeuticaRepository;
import com.carlesso.pilatesapi.repository.PacienteRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AvaliacaoFisioterapeuticaService {

    private final AvaliacaoFisioterapeuticaRepository avaliacaoRepository;
    private final PacienteRepository pacienteRepository;

    public AvaliacaoFisioterapeuticaService(
            AvaliacaoFisioterapeuticaRepository avaliacaoRepository, PacienteRepository pacienteRepository) {
        this.avaliacaoRepository = avaliacaoRepository;
        this.pacienteRepository = pacienteRepository;
    }

    @Transactional
    public AvaliacaoFisioterapeuticaResponseDTO criar(AvaliacaoFisioterapeuticaRequestDTO dto) {
        Paciente paciente = pacienteRepository
                .findByIdAndAtivoTrue(dto.pacienteId())
                .orElseThrow(() -> new ResourceNotFoundException("Paciente não encontrado: " + dto.pacienteId()));

        AvaliacaoFisioterapeutica avaliacao = new AvaliacaoFisioterapeutica();
        avaliacao.setPaciente(paciente);
        avaliacao.setDataAvaliacao(dto.dataAvaliacao());
        avaliacao.setQueixaFuncional(dto.queixaFuncional());
        avaliacao.setAvaliacaoPostural(dto.avaliacaoPostural());
        avaliacao.setMobilidadeArticular(dto.mobilidadeArticular());
        avaliacao.setForcaMuscular(dto.forcaMuscular());
        avaliacao.setFlexibilidade(dto.flexibilidade());
        avaliacao.setEquilibrio(dto.equilibrio());
        avaliacao.setCoordenacaoMotora(dto.coordenacaoMotora());
        avaliacao.setPadraoRespiratorio(dto.padraoRespiratorio());
        avaliacao.setEscalaDor(dto.escalaDor());
        avaliacao.setTestesFuncionaisRealizados(dto.testesFuncionaisRealizados());
        avaliacao.setDiagnosticoFisioterapeutico(dto.diagnosticoFisioterapeutico());
        avaliacao.setObservacoesGerais(dto.observacoesGerais());
        // dataCriacao definida por @PrePersist na entidade

        return AvaliacaoFisioterapeuticaResponseDTO.from(avaliacaoRepository.save(avaliacao));
    }

    @Transactional(readOnly = true)
    public AvaliacaoFisioterapeuticaResponseDTO buscarPorId(Long id) {
        return AvaliacaoFisioterapeuticaResponseDTO.from(encontrar(id));
    }

    @Transactional(readOnly = true)
    public List<AvaliacaoFisioterapeuticaResponseDTO> listarPorPaciente(Long pacienteId) {
        if (!pacienteRepository.existsByIdAndAtivoTrue(pacienteId)) {
            throw new ResourceNotFoundException("Paciente não encontrado: " + pacienteId);
        }
        return avaliacaoRepository.findAtivasByPacienteOrdenadas(pacienteId).stream()
                .map(AvaliacaoFisioterapeuticaResponseDTO::from)
                .toList();
    }

    @Transactional
    public AvaliacaoFisioterapeuticaResponseDTO atualizar(Long id, AvaliacaoFisioterapeuticaUpdateDTO dto) {
        AvaliacaoFisioterapeutica avaliacao = encontrar(id);

        if (dto.dataAvaliacao() != null) avaliacao.setDataAvaliacao(dto.dataAvaliacao());
        if (dto.queixaFuncional() != null) avaliacao.setQueixaFuncional(dto.queixaFuncional());
        if (dto.avaliacaoPostural() != null) avaliacao.setAvaliacaoPostural(dto.avaliacaoPostural());
        if (dto.mobilidadeArticular() != null) avaliacao.setMobilidadeArticular(dto.mobilidadeArticular());
        if (dto.forcaMuscular() != null) avaliacao.setForcaMuscular(dto.forcaMuscular());
        if (dto.flexibilidade() != null) avaliacao.setFlexibilidade(dto.flexibilidade());
        if (dto.equilibrio() != null) avaliacao.setEquilibrio(dto.equilibrio());
        if (dto.coordenacaoMotora() != null) avaliacao.setCoordenacaoMotora(dto.coordenacaoMotora());
        if (dto.padraoRespiratorio() != null) avaliacao.setPadraoRespiratorio(dto.padraoRespiratorio());
        if (dto.escalaDor() != null) avaliacao.setEscalaDor(dto.escalaDor());
        if (dto.testesFuncionaisRealizados() != null)
            avaliacao.setTestesFuncionaisRealizados(dto.testesFuncionaisRealizados());
        if (dto.diagnosticoFisioterapeutico() != null)
            avaliacao.setDiagnosticoFisioterapeutico(dto.diagnosticoFisioterapeutico());
        if (dto.observacoesGerais() != null) avaliacao.setObservacoesGerais(dto.observacoesGerais());
        avaliacao.setDataAtualizacao(LocalDateTime.now());

        return AvaliacaoFisioterapeuticaResponseDTO.from(avaliacaoRepository.save(avaliacao));
    }

    private AvaliacaoFisioterapeutica encontrar(Long id) {
        return avaliacaoRepository
                .findAtivaById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Avaliação fisioterapêutica não encontrada: " + id));
    }
}
