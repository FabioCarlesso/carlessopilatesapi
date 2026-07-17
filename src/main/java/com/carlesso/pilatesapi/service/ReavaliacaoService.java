package com.carlesso.pilatesapi.service;

import com.carlesso.pilatesapi.dto.ReavaliacaoRequestDTO;
import com.carlesso.pilatesapi.dto.ReavaliacaoResponseDTO;
import com.carlesso.pilatesapi.dto.ReavaliacaoUpdateDTO;
import com.carlesso.pilatesapi.entity.AvaliacaoFisioterapeutica;
import com.carlesso.pilatesapi.entity.Paciente;
import com.carlesso.pilatesapi.entity.PlanoTratamento;
import com.carlesso.pilatesapi.entity.Reavaliacao;
import com.carlesso.pilatesapi.exception.BusinessException;
import com.carlesso.pilatesapi.exception.ResourceNotFoundException;
import com.carlesso.pilatesapi.repository.AvaliacaoFisioterapeuticaRepository;
import com.carlesso.pilatesapi.repository.PacienteRepository;
import com.carlesso.pilatesapi.repository.PlanoTratamentoRepository;
import com.carlesso.pilatesapi.repository.ReavaliacaoRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReavaliacaoService {

    private final ReavaliacaoRepository reavaliacaoRepository;
    private final PacienteRepository pacienteRepository;
    private final AvaliacaoFisioterapeuticaRepository avaliacaoRepository;
    private final PlanoTratamentoRepository planoTratamentoRepository;

    public ReavaliacaoService(
            ReavaliacaoRepository reavaliacaoRepository,
            PacienteRepository pacienteRepository,
            AvaliacaoFisioterapeuticaRepository avaliacaoRepository,
            PlanoTratamentoRepository planoTratamentoRepository) {
        this.reavaliacaoRepository = reavaliacaoRepository;
        this.pacienteRepository = pacienteRepository;
        this.avaliacaoRepository = avaliacaoRepository;
        this.planoTratamentoRepository = planoTratamentoRepository;
    }

    @Transactional
    public ReavaliacaoResponseDTO criar(ReavaliacaoRequestDTO dto) {
        Paciente paciente = pacienteRepository
                .findByIdAndAtivoTrue(dto.pacienteId())
                .orElseThrow(() -> new ResourceNotFoundException("Paciente não encontrado: " + dto.pacienteId()));

        Reavaliacao reavaliacao = new Reavaliacao();
        reavaliacao.setPaciente(paciente);
        reavaliacao.setDataReavaliacao(dto.dataReavaliacao());
        reavaliacao.setComparativoAvaliacaoAnterior(dto.comparativoAvaliacaoAnterior());
        reavaliacao.setEvolucaoDor(dto.evolucaoDor());
        reavaliacao.setEvolucaoForca(dto.evolucaoForca());
        reavaliacao.setEvolucaoMobilidade(dto.evolucaoMobilidade());
        reavaliacao.setEvolucaoFuncional(dto.evolucaoFuncional());
        reavaliacao.setObjetivosAlcancados(dto.objetivosAlcancados());
        reavaliacao.setPontosAtencao(dto.pontosAtencao());
        reavaliacao.setAjustesRecomendados(dto.ajustesRecomendados());
        reavaliacao.setObservacoesGerais(dto.observacoesGerais());

        if (dto.avaliacaoFisioterapeuticaId() != null) {
            AvaliacaoFisioterapeutica avaliacao = avaliacaoRepository
                    .findAtivaById(dto.avaliacaoFisioterapeuticaId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Avaliação fisioterapêutica não encontrada: " + dto.avaliacaoFisioterapeuticaId()));
            if (!avaliacao.getPaciente().getId().equals(paciente.getId())) {
                throw new BusinessException("Avaliação fisioterapêutica não pertence ao paciente informado");
            }
            reavaliacao.setAvaliacaoFisioterapeutica(avaliacao);
        }

        if (dto.planoTratamentoId() != null) {
            PlanoTratamento plano = planoTratamentoRepository
                    .findAtivoById(dto.planoTratamentoId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Plano de tratamento não encontrado: " + dto.planoTratamentoId()));
            if (!plano.getPaciente().getId().equals(paciente.getId())) {
                throw new BusinessException("Plano de tratamento não pertence ao paciente informado");
            }
            reavaliacao.setPlanoTratamento(plano);
        }

        return ReavaliacaoResponseDTO.from(reavaliacaoRepository.save(reavaliacao));
    }

    @Transactional(readOnly = true)
    public ReavaliacaoResponseDTO buscarPorId(Long id) {
        return ReavaliacaoResponseDTO.from(encontrar(id));
    }

    @Transactional(readOnly = true)
    public List<ReavaliacaoResponseDTO> listarPorPaciente(Long pacienteId) {
        if (!pacienteRepository.existsByIdAndAtivoTrue(pacienteId)) {
            throw new ResourceNotFoundException("Paciente não encontrado: " + pacienteId);
        }
        return reavaliacaoRepository.findAtivasByPacienteOrdenadas(pacienteId).stream()
                .map(ReavaliacaoResponseDTO::from)
                .toList();
    }

    @Transactional
    public ReavaliacaoResponseDTO atualizar(Long id, ReavaliacaoUpdateDTO dto) {
        Reavaliacao reavaliacao = encontrar(id);

        if (dto.dataReavaliacao() != null) reavaliacao.setDataReavaliacao(dto.dataReavaliacao());
        if (dto.comparativoAvaliacaoAnterior() != null)
            reavaliacao.setComparativoAvaliacaoAnterior(dto.comparativoAvaliacaoAnterior());
        if (dto.evolucaoDor() != null) reavaliacao.setEvolucaoDor(dto.evolucaoDor());
        if (dto.evolucaoForca() != null) reavaliacao.setEvolucaoForca(dto.evolucaoForca());
        if (dto.evolucaoMobilidade() != null) reavaliacao.setEvolucaoMobilidade(dto.evolucaoMobilidade());
        if (dto.evolucaoFuncional() != null) reavaliacao.setEvolucaoFuncional(dto.evolucaoFuncional());
        if (dto.objetivosAlcancados() != null) reavaliacao.setObjetivosAlcancados(dto.objetivosAlcancados());
        if (dto.pontosAtencao() != null) reavaliacao.setPontosAtencao(dto.pontosAtencao());
        if (dto.ajustesRecomendados() != null) reavaliacao.setAjustesRecomendados(dto.ajustesRecomendados());
        if (dto.observacoesGerais() != null) reavaliacao.setObservacoesGerais(dto.observacoesGerais());
        reavaliacao.setDataAtualizacao(LocalDateTime.now());

        return ReavaliacaoResponseDTO.from(reavaliacaoRepository.save(reavaliacao));
    }

    private Reavaliacao encontrar(Long id) {
        return reavaliacaoRepository
                .findAtivaById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reavaliação não encontrada: " + id));
    }
}
