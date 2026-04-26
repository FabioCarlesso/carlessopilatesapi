package com.carlesso.pilatesapi.service;

import com.carlesso.pilatesapi.dto.PlanoRequestDTO;
import com.carlesso.pilatesapi.dto.PlanoResponseDTO;
import com.carlesso.pilatesapi.entity.Paciente;
import com.carlesso.pilatesapi.entity.Plano;
import com.carlesso.pilatesapi.exception.BusinessException;
import com.carlesso.pilatesapi.exception.ConflictException;
import com.carlesso.pilatesapi.exception.ResourceNotFoundException;
import com.carlesso.pilatesapi.repository.PacienteRepository;
import com.carlesso.pilatesapi.repository.PlanoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class PlanoService {

    private final PlanoRepository planoRepository;
    private final PacienteRepository pacienteRepository;

    public PlanoService(PlanoRepository planoRepository, PacienteRepository pacienteRepository) {
        this.planoRepository = planoRepository;
        this.pacienteRepository = pacienteRepository;
    }

    @Transactional
    public PlanoResponseDTO criar(PlanoRequestDTO dto) {
        Paciente paciente = pacienteRepository.findById(dto.pacienteId())
                .orElseThrow(() -> new ResourceNotFoundException("Paciente não encontrado: " + dto.pacienteId()));

        if (!paciente.isAtivo()) {
            throw new BusinessException("Paciente inativo não pode receber novo plano");
        }

        if (dto.diasSemana().size() != dto.frequenciaSemanal().getVezesPorSemana()) {
            throw new IllegalArgumentException(
                    "Número de dias da semana (%d) incompatível com a frequência contratada (%s = %d dia(s) por semana)"
                            .formatted(
                                    dto.diasSemana().size(),
                                    dto.frequenciaSemanal(),
                                    dto.frequenciaSemanal().getVezesPorSemana()
                            )
            );
        }

        planoRepository.findByPacienteIdAndAtivoTrue(dto.pacienteId())
                .ifPresent(planoAtivo -> {
                    planoAtivo.setAtivo(false);
                    planoRepository.save(planoAtivo);
                });

        Plano plano = new Plano();
        plano.setPaciente(paciente);
        plano.setTipo(dto.tipo());
        plano.setValor(dto.valor());
        plano.setFrequenciaSemanal(dto.frequenciaSemanal());
        plano.setDiasSemana(dto.diasSemana());
        plano.setDataInicio(dto.dataInicio() != null ? dto.dataInicio() : LocalDate.now());

        return PlanoResponseDTO.from(planoRepository.save(plano));
    }

    @Transactional(readOnly = true)
    public PlanoResponseDTO buscarPorId(Long id) {
        return PlanoResponseDTO.from(encontrar(id));
    }

    @Transactional(readOnly = true)
    public Optional<PlanoResponseDTO> buscarAtivoPorPaciente(Long pacienteId) {
        return planoRepository.findByPacienteIdAndAtivoTrue(pacienteId)
                .map(PlanoResponseDTO::from);
    }

    @Transactional(readOnly = true)
    public List<PlanoResponseDTO> listarPorPaciente(Long pacienteId) {
        return planoRepository.findByPacienteId(pacienteId)
                .stream()
                .map(PlanoResponseDTO::from)
                .toList();
    }

    @Transactional
    public void inativar(Long id) {
        Plano plano = encontrar(id);
        if (!plano.isAtivo()) {
            throw new ConflictException("Plano já está inativo");
        }
        plano.setAtivo(false);
    }

    private Plano encontrar(Long id) {
        return planoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Plano não encontrado: " + id));
    }
}
