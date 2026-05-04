package com.carlesso.pilatesapi.service;

import com.carlesso.pilatesapi.dto.PlanoTratamentoRequestDTO;
import com.carlesso.pilatesapi.dto.PlanoTratamentoResponseDTO;
import com.carlesso.pilatesapi.dto.PlanoTratamentoUpdateDTO;
import com.carlesso.pilatesapi.entity.PlanoTratamento;
import com.carlesso.pilatesapi.entity.Paciente;
import com.carlesso.pilatesapi.exception.ResourceNotFoundException;
import com.carlesso.pilatesapi.repository.PlanoTratamentoRepository;
import com.carlesso.pilatesapi.repository.PacienteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class PlanoTratamentoService {

    private final PlanoTratamentoRepository planoTratamentoRepository;
    private final PacienteRepository pacienteRepository;

    public PlanoTratamentoService(PlanoTratamentoRepository planoTratamentoRepository,
                                   PacienteRepository pacienteRepository) {
        this.planoTratamentoRepository = planoTratamentoRepository;
        this.pacienteRepository = pacienteRepository;
    }

    @Transactional
    public PlanoTratamentoResponseDTO criar(PlanoTratamentoRequestDTO dto) {
        validarPeriodo(dto.dataInicio(), dto.dataFimPrevista());

        Paciente paciente = pacienteRepository.findByIdAndAtivoTrue(dto.pacienteId())
                .orElseThrow(() -> new ResourceNotFoundException("Paciente não encontrado: " + dto.pacienteId()));

        PlanoTratamento plano = new PlanoTratamento();
        plano.setPaciente(paciente);
        plano.setDataInicio(dto.dataInicio());
        plano.setDataFimPrevista(dto.dataFimPrevista());
        plano.setObjetivosTratamento(dto.objetivosTratamento());
        plano.setIntervencoesPlanejadas(dto.intervencoesPlanejadas());
        plano.setNumeroSessoesPrevistas(dto.numeroSessoesPrevistas());
        plano.setFrequenciaSessoes(dto.frequenciaSessoes());
        plano.setResponsavelTratamento(dto.responsavelTratamento());
        plano.setObservacoes(dto.observacoes());

        return PlanoTratamentoResponseDTO.from(planoTratamentoRepository.save(plano));
    }

    @Transactional(readOnly = true)
    public PlanoTratamentoResponseDTO buscarPorId(Long id) {
        return PlanoTratamentoResponseDTO.from(encontrar(id));
    }

    @Transactional(readOnly = true)
    public List<PlanoTratamentoResponseDTO> listarPorPaciente(Long pacienteId) {
        if (!pacienteRepository.existsByIdAndAtivoTrue(pacienteId)) {
            throw new ResourceNotFoundException("Paciente não encontrado: " + pacienteId);
        }
        return planoTratamentoRepository.findAtivosByPacienteOrdenados(pacienteId)
                .stream()
                .map(PlanoTratamentoResponseDTO::from)
                .toList();
    }

    @Transactional
    public PlanoTratamentoResponseDTO atualizar(Long id, PlanoTratamentoUpdateDTO dto) {
        PlanoTratamento plano = encontrar(id);
        var dataInicio = dto.dataInicio() != null ? dto.dataInicio() : plano.getDataInicio();
        var dataFimPrevista = dto.dataFimPrevista() != null ? dto.dataFimPrevista() : plano.getDataFimPrevista();
        validarPeriodo(dataInicio, dataFimPrevista);

        if (dto.dataInicio() != null) plano.setDataInicio(dto.dataInicio());
        if (dto.dataFimPrevista() != null) plano.setDataFimPrevista(dto.dataFimPrevista());
        if (dto.objetivosTratamento() != null) plano.setObjetivosTratamento(dto.objetivosTratamento());
        if (dto.intervencoesPlanejadas() != null) plano.setIntervencoesPlanejadas(dto.intervencoesPlanejadas());
        if (dto.numeroSessoesPrevistas() != null) plano.setNumeroSessoesPrevistas(dto.numeroSessoesPrevistas());
        if (dto.frequenciaSessoes() != null) plano.setFrequenciaSessoes(dto.frequenciaSessoes());
        if (dto.responsavelTratamento() != null) plano.setResponsavelTratamento(dto.responsavelTratamento());
        if (dto.observacoes() != null) plano.setObservacoes(dto.observacoes());
        plano.setDataAtualizacao(LocalDateTime.now());

        return PlanoTratamentoResponseDTO.from(planoTratamentoRepository.save(plano));
    }

    @Transactional
    public void inativar(Long id) {
        PlanoTratamento plano = encontrar(id);
        plano.setAtivo(false);
        plano.setDataAtualizacao(LocalDateTime.now());
    }

    private PlanoTratamento encontrar(Long id) {
        return planoTratamentoRepository.findAtivoById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Plano de tratamento não encontrado: " + id));
    }

    private void validarPeriodo(LocalDate dataInicio, LocalDate dataFimPrevista) {
        if (dataInicio != null && dataFimPrevista != null && dataFimPrevista.isBefore(dataInicio)) {
            throw new IllegalArgumentException("dataFimPrevista não pode ser anterior a dataInicio");
        }
    }
}
