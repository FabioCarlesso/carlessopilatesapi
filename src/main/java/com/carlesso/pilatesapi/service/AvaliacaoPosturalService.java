package com.carlesso.pilatesapi.service;

import com.carlesso.pilatesapi.dto.AvaliacaoPosturalFotoResponseDTO;
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
import com.carlesso.pilatesapi.storage.FotoArmazenada;
import com.carlesso.pilatesapi.storage.FotoStorage;
import com.carlesso.pilatesapi.util.MetricasPosturaisCalculator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Regras do simetrógrafo virtual: a análise vive dentro de uma avaliação
 * fisioterapêutica, admite no máximo uma vista ativa por avaliação e tem suas
 * métricas sempre derivadas dos landmarks — nunca informadas pelo cliente.
 */
@Service
public class AvaliacaoPosturalService {

    private static final Logger log = LoggerFactory.getLogger(AvaliacaoPosturalService.class);

    /** Limite do MVP: o frontend comprime a foto (~1080px no maior lado) antes de enviar. */
    static final long TAMANHO_MAXIMO_FOTO_BYTES = 2L * 1024 * 1024;

    /**
     * Teto defensivo contra decompression bomb: um PNG de 2 MB pode declarar
     * dimensões que expandiriam para gigabytes se decodificado. As dimensões são
     * lidas apenas do header, e acima deste teto o arquivo é rejeitado.
     */
    static final int DIMENSAO_MAXIMA_PX = 10_000;

    private static final byte[] MAGIC_JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] MAGIC_PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    private final AvaliacaoPosturalRepository avaliacaoPosturalRepository;
    private final AvaliacaoFisioterapeuticaRepository avaliacaoFisioterapeuticaRepository;
    private final ObjectMapper objectMapper;
    private final FotoStorage fotoStorage;

    /**
     * Referência ao próprio bean (proxy transacional) para que {@link #substituirFoto}
     * execute em uma nova transação a cada tentativa, permitindo o retry após
     * colisão da constraint única de uploads concorrentes.
     */
    private AvaliacaoPosturalService self;

    public AvaliacaoPosturalService(
            AvaliacaoPosturalRepository avaliacaoPosturalRepository,
            AvaliacaoFisioterapeuticaRepository avaliacaoFisioterapeuticaRepository,
            ObjectMapper objectMapper,
            FotoStorage fotoStorage) {
        this.avaliacaoPosturalRepository = avaliacaoPosturalRepository;
        this.avaliacaoFisioterapeuticaRepository = avaliacaoFisioterapeuticaRepository;
        this.objectMapper = objectMapper;
        this.fotoStorage = fotoStorage;
    }

    @Autowired
    void setSelf(@Lazy AvaliacaoPosturalService self) {
        this.self = self;
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

    /**
     * Uploads concorrentes na mesma análise podem ambos seguir o caminho de INSERT
     * e colidir na constraint única da foto; nesse caso a operação é repetida uma
     * vez, quando a busca passa a encontrar a linha já gravada e segue pelo caminho
     * de substituição — preservando a semântica idempotente do PUT (200).
     */
    public AvaliacaoPosturalFotoResponseDTO enviarFoto(Long id, MultipartFile foto) {
        try {
            return self.substituirFoto(id, foto);
        } catch (DataIntegrityViolationException e) {
            log.warn("Colisão de constraint ao enviar a foto da análise postural {}; repetindo como substituição", id);
            return self.substituirFoto(id, foto);
        }
    }

    @Transactional
    public AvaliacaoPosturalFotoResponseDTO substituirFoto(Long id, MultipartFile foto) {
        AvaliacaoPostural analise = encontrar(id);
        if (analise.getStatus() != StatusAvaliacaoPostural.RASCUNHO) {
            throw new BusinessException(
                    "Foto de análise postural concluída não pode ser substituída; cancele a análise e crie outra: "
                            + id);
        }

        byte[] conteudo = lerConteudo(foto);
        String contentType = detectarContentType(conteudo);
        Dimensao dimensao = lerDimensoes(conteudo);

        FotoArmazenada salva = fotoStorage.salvar(id, conteudo, contentType, dimensao.largura(), dimensao.altura());

        analise.setFotoContentType(contentType);
        analise.setDataAtualizacao(LocalDateTime.now());
        avaliacaoPosturalRepository.save(analise);

        return AvaliacaoPosturalFotoResponseDTO.from(id, salva);
    }

    @Transactional(readOnly = true)
    public FotoArmazenada buscarFoto(Long id) {
        AvaliacaoPostural analise = encontrar(id);
        return fotoStorage
                .recuperar(analise.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Análise postural sem foto: " + id));
    }

    private byte[] lerConteudo(MultipartFile foto) {
        if (foto == null || foto.isEmpty()) {
            throw new IllegalArgumentException("foto é obrigatória");
        }
        if (foto.getSize() > TAMANHO_MAXIMO_FOTO_BYTES) {
            throw new BusinessException("Foto excede o tamanho máximo permitido de 2 MB");
        }
        try {
            return foto.getBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao ler o conteúdo da foto", e);
        }
    }

    /** Formato validado pelos magic bytes do conteúdo — extensão e Content-Type informados não contam. */
    private String detectarContentType(byte[] conteudo) {
        if (comecaCom(conteudo, MAGIC_JPEG)) {
            return MediaType.IMAGE_JPEG_VALUE;
        }
        if (comecaCom(conteudo, MAGIC_PNG)) {
            return MediaType.IMAGE_PNG_VALUE;
        }
        throw new IllegalArgumentException("Formato de foto não suportado: envie JPEG ou PNG");
    }

    private boolean comecaCom(byte[] conteudo, byte[] prefixo) {
        if (conteudo.length < prefixo.length) {
            return false;
        }
        for (int i = 0; i < prefixo.length; i++) {
            if (conteudo[i] != prefixo[i]) {
                return false;
            }
        }
        return true;
    }

    private record Dimensao(int largura, int altura) {}

    /**
     * Largura/altura (exigidas pelo cálculo fiel dos ângulos) lidas apenas do
     * header da imagem, sem decodificar os pixels — evita alocar a imagem inteira
     * em memória e neutraliza decompression bombs junto com o teto de dimensões.
     */
    private Dimensao lerDimensoes(byte[] conteudo) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(conteudo))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IllegalArgumentException("Arquivo de foto inválido ou corrompido");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                int largura = reader.getWidth(0);
                int altura = reader.getHeight(0);
                if (largura > DIMENSAO_MAXIMA_PX || altura > DIMENSAO_MAXIMA_PX) {
                    throw new IllegalArgumentException(
                            "Foto excede a dimensão máxima de " + DIMENSAO_MAXIMA_PX + " px por lado");
                }
                return new Dimensao(largura, altura);
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Arquivo de foto inválido ou corrompido");
        }
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
