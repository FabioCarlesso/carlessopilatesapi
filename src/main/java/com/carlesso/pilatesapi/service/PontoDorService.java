package com.carlesso.pilatesapi.service;

import com.carlesso.pilatesapi.dto.PontoDorRequestDTO;
import com.carlesso.pilatesapi.dto.PontoDorResponseDTO;
import com.carlesso.pilatesapi.dto.PontosDorRegistroDTO;
import com.carlesso.pilatesapi.dto.PontosDorRequestDTO;
import com.carlesso.pilatesapi.entity.AvaliacaoFisioterapeutica;
import com.carlesso.pilatesapi.entity.Paciente;
import com.carlesso.pilatesapi.entity.PontoDor;
import com.carlesso.pilatesapi.entity.Reavaliacao;
import com.carlesso.pilatesapi.entity.enums.OrigemPontoDor;
import com.carlesso.pilatesapi.exception.ResourceNotFoundException;
import com.carlesso.pilatesapi.repository.AvaliacaoFisioterapeuticaRepository;
import com.carlesso.pilatesapi.repository.PacienteRepository;
import com.carlesso.pilatesapi.repository.PontoDorRepository;
import com.carlesso.pilatesapi.repository.ReavaliacaoRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pontos de dor do mapa corporal (#249), ligados à avaliação fisioterapêutica ou à reavaliação.
 *
 * <p>O conjunto de um registro é sempre substituído por inteiro: o FE edita o mapa como um todo e envia o estado
 * final, o que dispensa identificar ponto a ponto o que foi incluído, movido ou removido. Como os pontos não são
 * editados individualmente, também não há locking otimista neles — a concorrência entre dois PUTs é serializada
 * pela trava pessimista na linha do registro.
 */
@Service
public class PontoDorService {

    /** Casas decimais gravadas em x/y (NUMERIC(7,6)): bem abaixo de um pixel do desenho de 240 × 520. */
    static final int ESCALA_COORDENADA = 6;

    private final PontoDorRepository pontoDorRepository;
    private final AvaliacaoFisioterapeuticaRepository avaliacaoRepository;
    private final ReavaliacaoRepository reavaliacaoRepository;
    private final PacienteRepository pacienteRepository;

    public PontoDorService(
            PontoDorRepository pontoDorRepository,
            AvaliacaoFisioterapeuticaRepository avaliacaoRepository,
            ReavaliacaoRepository reavaliacaoRepository,
            PacienteRepository pacienteRepository) {
        this.pontoDorRepository = pontoDorRepository;
        this.avaliacaoRepository = avaliacaoRepository;
        this.reavaliacaoRepository = reavaliacaoRepository;
        this.pacienteRepository = pacienteRepository;
    }

    @Transactional(readOnly = true)
    public List<PontoDorResponseDTO> listarDaAvaliacao(Long avaliacaoId) {
        if (!avaliacaoRepository.existsById(avaliacaoId)) {
            throw avaliacaoNaoEncontrada(avaliacaoId);
        }
        return paraResposta(pontoDorRepository.findByAvaliacaoFisioterapeuticaIdOrderByIdAsc(avaliacaoId));
    }

    @Transactional(readOnly = true)
    public List<PontoDorResponseDTO> listarDaReavaliacao(Long reavaliacaoId) {
        if (!reavaliacaoRepository.existsById(reavaliacaoId)) {
            throw reavaliacaoNaoEncontrada(reavaliacaoId);
        }
        return paraResposta(pontoDorRepository.findByReavaliacaoIdOrderByIdAsc(reavaliacaoId));
    }

    /**
     * Substitui os pontos da avaliação. Não exige paciente ativo: como a atualização da própria avaliação, mexer no
     * registro existente de um ex-aluno é permitido.
     */
    @Transactional
    public List<PontoDorResponseDTO> substituirDaAvaliacao(Long avaliacaoId, PontosDorRequestDTO dto) {
        AvaliacaoFisioterapeutica avaliacao =
                pontoDorRepository.travarAvaliacao(avaliacaoId).orElseThrow(() -> avaliacaoNaoEncontrada(avaliacaoId));
        pontoDorRepository.excluirDaAvaliacao(avaliacaoId);
        return gravar(dto, avaliacao.getPaciente(), ponto -> ponto.setAvaliacaoFisioterapeutica(avaliacao));
    }

    /** Substitui os pontos da reavaliação, com as mesmas regras de {@link #substituirDaAvaliacao}. */
    @Transactional
    public List<PontoDorResponseDTO> substituirDaReavaliacao(Long reavaliacaoId, PontosDorRequestDTO dto) {
        Reavaliacao reavaliacao = pontoDorRepository
                .travarReavaliacao(reavaliacaoId)
                .orElseThrow(() -> reavaliacaoNaoEncontrada(reavaliacaoId));
        pontoDorRepository.excluirDaReavaliacao(reavaliacaoId);
        return gravar(dto, reavaliacao.getPaciente(), ponto -> ponto.setReavaliacao(reavaliacao));
    }

    /**
     * Histórico do paciente agrupado por registro, do mais antigo para o mais recente. Só entram os registros com
     * pontos: o FE compara a localização da dor, e um registro sem marcação não tem o que comparar. No mesmo dia, a
     * avaliação vem antes da reavaliação; depois, o id desempata.
     */
    @Transactional(readOnly = true)
    public List<PontosDorRegistroDTO> historicoDoPaciente(Long pacienteId) {
        if (!pacienteRepository.existsById(pacienteId)) {
            throw new ResourceNotFoundException("Paciente não encontrado: " + pacienteId);
        }
        Map<RegistroChave, List<PontoDorResponseDTO>> porRegistro = new LinkedHashMap<>();
        for (PontoDor ponto : pontoDorRepository.findByPacienteComRegistro(pacienteId)) {
            porRegistro
                    .computeIfAbsent(RegistroChave.de(ponto), chave -> new ArrayList<>())
                    .add(PontoDorResponseDTO.from(ponto));
        }
        return porRegistro.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(RegistroChave.ORDEM))
                .map(e -> new PontosDorRegistroDTO(
                        e.getKey().origem(), e.getKey().registroId(), e.getKey().data(), e.getValue()))
                .toList();
    }

    private List<PontoDorResponseDTO> gravar(
            PontosDorRequestDTO dto, Paciente paciente, Consumer<PontoDor> vincularRegistro) {
        List<PontoDorRequestDTO> pontos = dto.pontos() == null ? List.of() : dto.pontos();
        // A validação do controller já barra; o guarda fica para quem chamar o service por outro caminho.
        if (pontos.size() > PontosDorRequestDTO.MAXIMO_PONTOS) {
            throw new IllegalArgumentException(
                    "Um registro aceita no máximo " + PontosDorRequestDTO.MAXIMO_PONTOS + " pontos de dor");
        }
        List<PontoDor> novos = pontos.stream()
                .map(p -> novoPonto(p, paciente, vincularRegistro))
                .toList();
        return paraResposta(pontoDorRepository.saveAll(novos));
    }

    private static PontoDor novoPonto(PontoDorRequestDTO dto, Paciente paciente, Consumer<PontoDor> vincularRegistro) {
        PontoDor ponto = new PontoDor();
        ponto.setPaciente(paciente);
        vincularRegistro.accept(ponto);
        ponto.setVista(dto.vista());
        ponto.setX(coordenada(dto.x()));
        ponto.setY(coordenada(dto.y()));
        ponto.setDesenhoVersao(dto.desenhoVersao());
        ponto.setIntensidade(dto.intensidade());
        ponto.setObservacao(
                dto.observacao() == null || dto.observacao().isBlank()
                        ? null
                        : dto.observacao().strip());
        return ponto;
    }

    /** Arredonda para a escala da coluna aqui, para o valor gravado e o devolvido não dependerem do banco. */
    private static BigDecimal coordenada(Double valor) {
        return BigDecimal.valueOf(valor).setScale(ESCALA_COORDENADA, RoundingMode.HALF_UP);
    }

    private static List<PontoDorResponseDTO> paraResposta(List<PontoDor> pontos) {
        return pontos.stream().map(PontoDorResponseDTO::from).toList();
    }

    private static ResourceNotFoundException avaliacaoNaoEncontrada(Long id) {
        return new ResourceNotFoundException("Avaliação fisioterapêutica não encontrada: " + id);
    }

    private static ResourceNotFoundException reavaliacaoNaoEncontrada(Long id) {
        return new ResourceNotFoundException("Reavaliação não encontrada: " + id);
    }

    private record RegistroChave(OrigemPontoDor origem, Long registroId, LocalDate data) {

        static final Comparator<RegistroChave> ORDEM = Comparator.comparing(RegistroChave::data)
                .thenComparing(RegistroChave::origem)
                .thenComparing(RegistroChave::registroId);

        static RegistroChave de(PontoDor ponto) {
            if (ponto.getAvaliacaoFisioterapeutica() != null) {
                AvaliacaoFisioterapeutica avaliacao = ponto.getAvaliacaoFisioterapeutica();
                return new RegistroChave(OrigemPontoDor.AVALIACAO, avaliacao.getId(), avaliacao.getDataAvaliacao());
            }
            Reavaliacao reavaliacao = ponto.getReavaliacao();
            return new RegistroChave(OrigemPontoDor.REAVALIACAO, reavaliacao.getId(), reavaliacao.getDataReavaliacao());
        }
    }
}
