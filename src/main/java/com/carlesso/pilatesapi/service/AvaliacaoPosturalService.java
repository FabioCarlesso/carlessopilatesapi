package com.carlesso.pilatesapi.service;

import com.carlesso.pilatesapi.dto.AvaliacaoPosturalRequestDTO;
import com.carlesso.pilatesapi.dto.AvaliacaoPosturalResponseDTO;
import com.carlesso.pilatesapi.dto.AvaliacaoPosturalUpdateDTO;
import com.carlesso.pilatesapi.dto.LandmarkDTO;
import com.carlesso.pilatesapi.dto.MetricasPosturaisDTO;
import com.carlesso.pilatesapi.entity.AvaliacaoFisioterapeutica;
import com.carlesso.pilatesapi.entity.AvaliacaoPostural;
import com.carlesso.pilatesapi.entity.enums.CodigoLandmark;
import com.carlesso.pilatesapi.entity.enums.StatusAvaliacaoPostural;
import com.carlesso.pilatesapi.exception.BusinessException;
import com.carlesso.pilatesapi.exception.ConflictException;
import com.carlesso.pilatesapi.exception.ResourceNotFoundException;
import com.carlesso.pilatesapi.repository.AvaliacaoFisioterapeuticaRepository;
import com.carlesso.pilatesapi.repository.AvaliacaoPosturalRepository;
import com.carlesso.pilatesapi.util.MetricasPosturaisCalculator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regras do simetrógrafo virtual: a análise vive dentro de uma avaliação
 * fisioterapêutica, admite no máximo uma vista ativa por avaliação e tem suas
 * métricas sempre derivadas dos landmarks — nunca informadas pelo cliente.
 */
@Service
public class AvaliacaoPosturalService {

    private final AvaliacaoPosturalRepository avaliacaoPosturalRepository;
    private final AvaliacaoFisioterapeuticaRepository avaliacaoFisioterapeuticaRepository;
    private final ObjectMapper objectMapper;

    public AvaliacaoPosturalService(
            AvaliacaoPosturalRepository avaliacaoPosturalRepository,
            AvaliacaoFisioterapeuticaRepository avaliacaoFisioterapeuticaRepository,
            ObjectMapper objectMapper) {
        this.avaliacaoPosturalRepository = avaliacaoPosturalRepository;
        this.avaliacaoFisioterapeuticaRepository = avaliacaoFisioterapeuticaRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AvaliacaoPosturalResponseDTO criar(AvaliacaoPosturalRequestDTO dto) {
        AvaliacaoFisioterapeutica avaliacaoFisioterapeutica = encontrarAvaliacao(dto.avaliacaoFisioterapeuticaId());

        if (avaliacaoPosturalRepository.existsByAvaliacaoFisioterapeuticaIdAndVistaAndAtivoTrue(
                dto.avaliacaoFisioterapeuticaId(), dto.vista())) {
            throw new ConflictException("Avaliação já possui análise postural ativa da vista " + dto.vista());
        }

        AvaliacaoPostural analise = new AvaliacaoPostural();
        analise.setAvaliacaoFisioterapeutica(avaliacaoFisioterapeutica);
        analise.setVista(dto.vista());
        analise.setStatus(StatusAvaliacaoPostural.RASCUNHO);
        // dataCriacao definida por @PrePersist na entidade

        return montarResposta(avaliacaoPosturalRepository.save(analise));
    }

    @Transactional(readOnly = true)
    public AvaliacaoPosturalResponseDTO buscarPorId(Long id) {
        return montarResposta(encontrar(id));
    }

    @Transactional(readOnly = true)
    public List<AvaliacaoPosturalResponseDTO> listarPorAvaliacaoFisioterapeutica(Long avaliacaoFisioterapeuticaId) {
        encontrarAvaliacao(avaliacaoFisioterapeuticaId);
        return avaliacaoPosturalRepository.findAtivasByAvaliacaoFisioterapeutica(avaliacaoFisioterapeuticaId).stream()
                .map(this::montarResposta)
                .toList();
    }

    @Transactional
    public AvaliacaoPosturalResponseDTO atualizar(Long id, AvaliacaoPosturalUpdateDTO dto) {
        AvaliacaoPostural analise = encontrar(id);
        garantirRascunho(analise, "alterada");

        if (dto.landmarks() != null) {
            validarLandmarks(analise, dto.landmarks());
            analise.setLandmarks(serializar(dto.landmarks()));
        }
        if (dto.linhaPrumoX() != null) analise.setLinhaPrumoX(dto.linhaPrumoX());
        if (dto.calibracaoCmPorUnidade() != null) analise.setCalibracaoCmPorUnidade(dto.calibracaoCmPorUnidade());
        if (dto.proporcaoImagem() != null) analise.setProporcaoImagem(dto.proporcaoImagem());
        if (dto.observacoes() != null) analise.setObservacoes(dto.observacoes());
        analise.setDataAtualizacao(LocalDateTime.now());

        return montarResposta(avaliacaoPosturalRepository.save(analise));
    }

    @Transactional
    public AvaliacaoPosturalResponseDTO concluir(Long id) {
        AvaliacaoPostural analise = encontrar(id);
        garantirRascunho(analise, "concluída novamente");

        Set<CodigoLandmark> obrigatorios = CodigoLandmark.daVista(analise.getVista());
        Set<CodigoLandmark> marcados = EnumSet.noneOf(CodigoLandmark.class);
        desserializar(analise.getLandmarks()).forEach(l -> marcados.add(l.codigo()));

        Set<CodigoLandmark> faltantes = EnumSet.copyOf(obrigatorios);
        faltantes.removeAll(marcados);
        if (!faltantes.isEmpty()) {
            throw new BusinessException("Pontos obrigatórios não marcados: " + faltantes);
        }
        if (analise.getFotoContentType() == null) {
            throw new BusinessException("Análise postural não pode ser concluída sem foto: " + id);
        }

        analise.setStatus(StatusAvaliacaoPostural.CONCLUIDA);
        analise.setDataAtualizacao(LocalDateTime.now());

        return montarResposta(avaliacaoPosturalRepository.save(analise));
    }

    @Transactional
    public AvaliacaoPosturalResponseDTO cancelar(Long id) {
        AvaliacaoPostural analise = encontrar(id);

        analise.setAtivo(false);
        analise.setDataAtualizacao(LocalDateTime.now());

        return montarResposta(avaliacaoPosturalRepository.save(analise));
    }

    private AvaliacaoPostural encontrar(Long id) {
        return avaliacaoPosturalRepository
                .findAtivaById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Análise postural não encontrada: " + id));
    }

    private AvaliacaoFisioterapeutica encontrarAvaliacao(Long avaliacaoFisioterapeuticaId) {
        return avaliacaoFisioterapeuticaRepository
                .findAtivaById(avaliacaoFisioterapeuticaId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Avaliação fisioterapêutica não encontrada: " + avaliacaoFisioterapeuticaId));
    }

    /** Análise concluída é imutável: se ficou ruim, cancela-se e cria-se outra. */
    private void garantirRascunho(AvaliacaoPostural analise, String acao) {
        if (analise.getStatus() != StatusAvaliacaoPostural.RASCUNHO) {
            throw new BusinessException("Análise postural concluída não pode ser " + acao + ": " + analise.getId());
        }
    }

    private void validarLandmarks(AvaliacaoPostural analise, List<LandmarkDTO> landmarks) {
        Set<CodigoLandmark> permitidos = CodigoLandmark.daVista(analise.getVista());
        Set<CodigoLandmark> vistos = EnumSet.noneOf(CodigoLandmark.class);

        for (LandmarkDTO landmark : landmarks) {
            if (!permitidos.contains(landmark.codigo())) {
                throw new BusinessException(
                        "Ponto " + landmark.codigo() + " não pertence à vista " + analise.getVista());
            }
            if (!vistos.add(landmark.codigo())) {
                throw new BusinessException("Ponto marcado em duplicidade: " + landmark.codigo());
            }
        }
    }

    private AvaliacaoPosturalResponseDTO montarResposta(AvaliacaoPostural analise) {
        List<LandmarkDTO> landmarks = desserializar(analise.getLandmarks());
        MetricasPosturaisDTO metricas = MetricasPosturaisCalculator.calcular(
                analise.getVista(),
                landmarks,
                analise.getLinhaPrumoX(),
                analise.getCalibracaoCmPorUnidade(),
                analise.getProporcaoImagem());
        return AvaliacaoPosturalResponseDTO.from(analise, landmarks, metricas);
    }

    private String serializar(List<LandmarkDTO> landmarks) {
        try {
            return objectMapper.writeValueAsString(landmarks);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar landmarks da análise postural", e);
        }
    }

    private List<LandmarkDTO> desserializar(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<LandmarkDTO>>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao ler landmarks da análise postural", e);
        }
    }
}
