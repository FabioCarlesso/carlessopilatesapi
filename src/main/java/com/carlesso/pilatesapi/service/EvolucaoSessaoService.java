package com.carlesso.pilatesapi.service;

import com.carlesso.pilatesapi.dto.EvolucaoSessaoRequestDTO;
import com.carlesso.pilatesapi.dto.EvolucaoSessaoResponseDTO;
import com.carlesso.pilatesapi.dto.EvolucaoSessaoUpdateDTO;
import com.carlesso.pilatesapi.entity.EvolucaoSessao;
import com.carlesso.pilatesapi.entity.SessaoPilates;
import com.carlesso.pilatesapi.exception.ConflictException;
import com.carlesso.pilatesapi.exception.ResourceNotFoundException;
import com.carlesso.pilatesapi.repository.EvolucaoSessaoRepository;
import com.carlesso.pilatesapi.repository.SessaoPilatesRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class EvolucaoSessaoService {

    private final EvolucaoSessaoRepository evolucaoRepository;
    private final SessaoPilatesRepository sessaoRepository;

    public EvolucaoSessaoService(EvolucaoSessaoRepository evolucaoRepository,
                                 SessaoPilatesRepository sessaoRepository) {
        this.evolucaoRepository = evolucaoRepository;
        this.sessaoRepository = sessaoRepository;
    }

    @Transactional
    public EvolucaoSessaoResponseDTO criar(EvolucaoSessaoRequestDTO dto) {
        SessaoPilates sessao = sessaoRepository.findByIdComPaciente(dto.sessaoId())
                .orElseThrow(() -> new ResourceNotFoundException("Sessão não encontrada: " + dto.sessaoId()));

        if (evolucaoRepository.existsBySessaoId(dto.sessaoId())) {
            throw new ConflictException("Sessão já possui evolução registrada: " + dto.sessaoId());
        }

        EvolucaoSessao evolucao = new EvolucaoSessao();
        evolucao.setSessao(sessao);
        evolucao.setDataHoraRegistro(dto.dataHoraRegistro());
        evolucao.setExerciciosRealizados(dto.exerciciosRealizados());
        evolucao.setEquipamentosUtilizados(dto.equipamentosUtilizados());
        evolucao.setCargasMolas(dto.cargasMolas());
        evolucao.setDorAntes(dto.dorAntes());
        evolucao.setDorDepois(dto.dorDepois());
        evolucao.setRespostaPaciente(dto.respostaPaciente());
        evolucao.setIntercorrencias(dto.intercorrencias());
        evolucao.setOrientacoes(dto.orientacoes());
        evolucao.setObservacoesFisioterapeuta(dto.observacoesFisioterapeuta());

        return EvolucaoSessaoResponseDTO.from(evolucaoRepository.save(evolucao));
    }

    @Transactional(readOnly = true)
    public EvolucaoSessaoResponseDTO buscarPorId(Long id) {
        return EvolucaoSessaoResponseDTO.from(encontrar(id));
    }

    @Transactional(readOnly = true)
    public EvolucaoSessaoResponseDTO buscarPorSessao(Long sessaoId) {
        if (!sessaoRepository.existsById(sessaoId)) {
            throw new ResourceNotFoundException("Sessão não encontrada: " + sessaoId);
        }
        return evolucaoRepository.findBySessaoId(sessaoId)
                .map(EvolucaoSessaoResponseDTO::from)
                .orElseThrow(() -> new ResourceNotFoundException("Evolução não encontrada para a sessão: " + sessaoId));
    }

    @Transactional
    public EvolucaoSessaoResponseDTO atualizar(Long id, EvolucaoSessaoUpdateDTO dto) {
        EvolucaoSessao evolucao = encontrar(id);

        if (dto.dataHoraRegistro() != null) evolucao.setDataHoraRegistro(dto.dataHoraRegistro());
        if (dto.exerciciosRealizados() != null) evolucao.setExerciciosRealizados(dto.exerciciosRealizados());
        if (dto.equipamentosUtilizados() != null) evolucao.setEquipamentosUtilizados(dto.equipamentosUtilizados());
        if (dto.cargasMolas() != null) evolucao.setCargasMolas(dto.cargasMolas());
        if (dto.dorAntes() != null) evolucao.setDorAntes(dto.dorAntes());
        if (dto.dorDepois() != null) evolucao.setDorDepois(dto.dorDepois());
        if (dto.respostaPaciente() != null) evolucao.setRespostaPaciente(dto.respostaPaciente());
        if (dto.intercorrencias() != null) evolucao.setIntercorrencias(dto.intercorrencias());
        if (dto.orientacoes() != null) evolucao.setOrientacoes(dto.orientacoes());
        if (dto.observacoesFisioterapeuta() != null) evolucao.setObservacoesFisioterapeuta(dto.observacoesFisioterapeuta());
        evolucao.setDataAtualizacao(LocalDateTime.now());

        return EvolucaoSessaoResponseDTO.from(evolucaoRepository.save(evolucao));
    }

    private EvolucaoSessao encontrar(Long id) {
        return evolucaoRepository.findByIdComSessao(id)
                .orElseThrow(() -> new ResourceNotFoundException("Evolução não encontrada: " + id));
    }
}
