package com.carlesso.pilatesapi.service;

import com.carlesso.pilatesapi.dto.AnamneseRequestDTO;
import com.carlesso.pilatesapi.dto.AnamneseResponseDTO;
import com.carlesso.pilatesapi.dto.AnamneseUpdateDTO;
import com.carlesso.pilatesapi.entity.Anamnese;
import com.carlesso.pilatesapi.entity.Paciente;
import com.carlesso.pilatesapi.exception.ConflictException;
import com.carlesso.pilatesapi.exception.ResourceNotFoundException;
import com.carlesso.pilatesapi.repository.AnamneseRepository;
import com.carlesso.pilatesapi.repository.PacienteRepository;
import java.time.LocalDateTime;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnamneseService {

    private final AnamneseRepository anamneseRepository;
    private final PacienteRepository pacienteRepository;

    public AnamneseService(AnamneseRepository anamneseRepository, PacienteRepository pacienteRepository) {
        this.anamneseRepository = anamneseRepository;
        this.pacienteRepository = pacienteRepository;
    }

    @Transactional
    public AnamneseResponseDTO criar(AnamneseRequestDTO dto) {
        Paciente paciente = pacienteRepository
                .findByIdAndAtivoTrue(dto.pacienteId())
                .orElseThrow(() -> new ResourceNotFoundException("Paciente não encontrado: " + dto.pacienteId()));

        if (anamneseRepository.existsByPacienteId(dto.pacienteId())) {
            throw new ConflictException("Paciente já possui anamnese cadastrada: " + dto.pacienteId());
        }

        Anamnese anamnese = new Anamnese();
        anamnese.setPaciente(paciente);
        anamnese.setQueixaPrincipal(dto.queixaPrincipal());
        anamnese.setHistoricoDoencas(dto.historicoDoencas());
        anamnese.setHistoricoCirurgias(dto.historicoCirurgias());
        anamnese.setHistoricoLesoes(dto.historicoLesoes());
        anamnese.setMedicamentosUso(dto.medicamentosUso());
        anamnese.setAlergias(dto.alergias());
        anamnese.setNivelAtividadeFisica(dto.nivelAtividadeFisica());
        anamnese.setRestricoesMedicas(dto.restricoesMedicas());
        anamnese.setObjetivos(dto.objetivos());
        anamnese.setObservacoes(dto.observacoes());
        anamnese.setDataCriacao(LocalDateTime.now());

        try {
            return AnamneseResponseDTO.from(anamneseRepository.saveAndFlush(anamnese));
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Paciente já possui anamnese cadastrada: " + dto.pacienteId());
        }
    }

    @Transactional(readOnly = true)
    public AnamneseResponseDTO buscarPorId(Long id) {
        return AnamneseResponseDTO.from(encontrar(id));
    }

    @Transactional(readOnly = true)
    public AnamneseResponseDTO buscarPorPaciente(Long pacienteId) {
        if (!pacienteRepository.existsByIdAndAtivoTrue(pacienteId)) {
            throw new ResourceNotFoundException("Paciente não encontrado: " + pacienteId);
        }
        Anamnese anamnese = anamneseRepository
                .findByPacienteIdAndPacienteAtivoTrue(pacienteId)
                .orElseThrow(
                        () -> new ResourceNotFoundException("Anamnese não encontrada para o paciente: " + pacienteId));
        return AnamneseResponseDTO.from(anamnese);
    }

    @Transactional
    public AnamneseResponseDTO atualizar(Long id, AnamneseUpdateDTO dto) {
        Anamnese anamnese = encontrar(id);

        validarCampoObrigatorio("queixaPrincipal", dto.queixaPrincipal());
        validarCampoObrigatorio("objetivos", dto.objetivos());

        if (dto.queixaPrincipal() != null) anamnese.setQueixaPrincipal(dto.queixaPrincipal());
        if (dto.historicoDoencas() != null) anamnese.setHistoricoDoencas(dto.historicoDoencas());
        if (dto.historicoCirurgias() != null) anamnese.setHistoricoCirurgias(dto.historicoCirurgias());
        if (dto.historicoLesoes() != null) anamnese.setHistoricoLesoes(dto.historicoLesoes());
        if (dto.medicamentosUso() != null) anamnese.setMedicamentosUso(dto.medicamentosUso());
        if (dto.alergias() != null) anamnese.setAlergias(dto.alergias());
        if (dto.nivelAtividadeFisica() != null) anamnese.setNivelAtividadeFisica(dto.nivelAtividadeFisica());
        if (dto.restricoesMedicas() != null) anamnese.setRestricoesMedicas(dto.restricoesMedicas());
        if (dto.objetivos() != null) anamnese.setObjetivos(dto.objetivos());
        if (dto.observacoes() != null) anamnese.setObservacoes(dto.observacoes());
        anamnese.setDataAtualizacao(LocalDateTime.now());

        return AnamneseResponseDTO.from(anamnese);
    }

    private Anamnese encontrar(Long id) {
        return anamneseRepository
                .findByIdAndPacienteAtivoTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Anamnese não encontrada: " + id));
    }

    private void validarCampoObrigatorio(String campo, String valor) {
        if (valor != null && valor.isBlank()) {
            throw new IllegalArgumentException(campo + " não pode ser vazio");
        }
    }
}
