package com.carlesso.pilatesapi.service;

import com.carlesso.pilatesapi.dto.SessaoPilatesRequestDTO;
import com.carlesso.pilatesapi.dto.SessaoPilatesResponseDTO;
import com.carlesso.pilatesapi.dto.SessaoPilatesUpdateDTO;
import com.carlesso.pilatesapi.entity.Paciente;
import com.carlesso.pilatesapi.entity.PlanoTratamento;
import com.carlesso.pilatesapi.entity.Profissional;
import com.carlesso.pilatesapi.entity.SessaoPilates;
import com.carlesso.pilatesapi.entity.enums.StatusSessao;
import com.carlesso.pilatesapi.exception.BusinessException;
import com.carlesso.pilatesapi.exception.ResourceNotFoundException;
import com.carlesso.pilatesapi.repository.EvolucaoSessaoRepository;
import com.carlesso.pilatesapi.repository.PacienteRepository;
import com.carlesso.pilatesapi.repository.PlanoTratamentoRepository;
import com.carlesso.pilatesapi.repository.ProfissionalRepository;
import com.carlesso.pilatesapi.repository.SessaoPilatesRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SessaoPilatesService {

    private final SessaoPilatesRepository sessaoRepository;
    private final PacienteRepository pacienteRepository;
    private final ProfissionalRepository profissionalRepository;
    private final PlanoTratamentoRepository planoTratamentoRepository;
    private final EvolucaoSessaoRepository evolucaoSessaoRepository;

    public SessaoPilatesService(SessaoPilatesRepository sessaoRepository,
                                PacienteRepository pacienteRepository,
                                ProfissionalRepository profissionalRepository,
                                PlanoTratamentoRepository planoTratamentoRepository,
                                EvolucaoSessaoRepository evolucaoSessaoRepository) {
        this.sessaoRepository = sessaoRepository;
        this.pacienteRepository = pacienteRepository;
        this.profissionalRepository = profissionalRepository;
        this.planoTratamentoRepository = planoTratamentoRepository;
        this.evolucaoSessaoRepository = evolucaoSessaoRepository;
    }

    @Transactional
    public SessaoPilatesResponseDTO criar(SessaoPilatesRequestDTO dto) {
        Paciente paciente = pacienteRepository.findByIdAndAtivoTrue(dto.pacienteId())
                .orElseThrow(() -> new ResourceNotFoundException("Paciente não encontrado: " + dto.pacienteId()));

        SessaoPilates sessao = new SessaoPilates();
        sessao.setPaciente(paciente);
        sessao.setTipo(dto.tipo());
        sessao.setData(dto.data());
        sessao.setHorario(dto.horario());
        sessao.setLocal(dto.local());
        sessao.setDuracaoMinutos(dto.duracaoMinutos());
        sessao.setObservacoes(dto.observacoes());

        if (dto.profissionalId() != null) {
            Profissional profissional = profissionalRepository.findById(dto.profissionalId())
                    .orElseThrow(() -> new ResourceNotFoundException("Profissional não encontrado: " + dto.profissionalId()));
            if (!profissional.isAtivo()) {
                throw new BusinessException("Profissional inativo não pode ser vinculado à sessão");
            }
            sessao.setProfissional(profissional);
        }

        if (dto.planoTratamentoId() != null) {
            PlanoTratamento plano = planoTratamentoRepository.findAtivoById(dto.planoTratamentoId())
                    .orElseThrow(() -> new ResourceNotFoundException("Plano de tratamento não encontrado: " + dto.planoTratamentoId()));
            if (!plano.getPaciente().getId().equals(paciente.getId())) {
                throw new BusinessException("Plano de tratamento não pertence ao paciente informado");
            }
            sessao.setPlanoTratamento(plano);
        }

        return SessaoPilatesResponseDTO.from(sessaoRepository.save(sessao));
    }

    @Transactional(readOnly = true)
    public SessaoPilatesResponseDTO buscarPorId(Long id) {
        return SessaoPilatesResponseDTO.from(encontrar(id));
    }

    @Transactional(readOnly = true)
    public List<SessaoPilatesResponseDTO> listarPorPaciente(Long pacienteId) {
        if (!pacienteRepository.existsByIdAndAtivoTrue(pacienteId)) {
            throw new ResourceNotFoundException("Paciente não encontrado: " + pacienteId);
        }
        return sessaoRepository.findByPacienteOrdenadas(pacienteId)
                .stream()
                .map(SessaoPilatesResponseDTO::from)
                .toList();
    }

    @Transactional
    public SessaoPilatesResponseDTO atualizar(Long id, SessaoPilatesUpdateDTO dto) {
        SessaoPilates sessao = encontrar(id);

        if (dto.data() != null) sessao.setData(dto.data());
        if (dto.horario() != null) sessao.setHorario(dto.horario());
        if (dto.local() != null) sessao.setLocal(dto.local());
        if (dto.duracaoMinutos() != null) sessao.setDuracaoMinutos(dto.duracaoMinutos());
        if (dto.observacoes() != null) sessao.setObservacoes(dto.observacoes());
        sessao.setDataAtualizacao(LocalDateTime.now());

        return SessaoPilatesResponseDTO.from(sessaoRepository.save(sessao));
    }

    @Transactional
    public SessaoPilatesResponseDTO realizar(Long id) {
        return aplicarTransicaoStatus(id, StatusSessao.REALIZADA);
    }

    @Transactional
    public SessaoPilatesResponseDTO cancelar(Long id) {
        return aplicarTransicaoStatus(id, StatusSessao.CANCELADA);
    }

    private SessaoPilatesResponseDTO aplicarTransicaoStatus(Long id, StatusSessao novoStatus) {
        int atualizadas = sessaoRepository.transicionarStatusSeAgendada(id, novoStatus, LocalDateTime.now());
        if (atualizadas == 1) {
            return SessaoPilatesResponseDTO.from(encontrar(id));
        }

        SessaoPilates sessao = encontrar(id);
        if (sessao.getStatus() != StatusSessao.AGENDADA) {
            throw new BusinessException("Transição inválida: sessão " + sessao.getStatus()
                    + " não pode ser alterada para " + novoStatus);
        }
        throw new BusinessException("Transição inválida: sessão não pode ser alterada para " + novoStatus);
    }

    @Transactional
    public void excluir(Long id) {
        SessaoPilates sessao = encontrar(id);
        evolucaoSessaoRepository.deleteBySessaoId(id);
        sessaoRepository.delete(sessao);
    }

    private SessaoPilates encontrar(Long id) {
        return sessaoRepository.findByIdComPaciente(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sessão não encontrada: " + id));
    }
}
