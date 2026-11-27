package com.carlesso.pilatesapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.carlesso.pilatesapi.dto.PontoDorRequestDTO;
import com.carlesso.pilatesapi.dto.PontoDorResponseDTO;
import com.carlesso.pilatesapi.dto.PontosDorRegistroDTO;
import com.carlesso.pilatesapi.dto.PontosDorRequestDTO;
import com.carlesso.pilatesapi.entity.AvaliacaoFisioterapeutica;
import com.carlesso.pilatesapi.entity.Paciente;
import com.carlesso.pilatesapi.entity.PontoDor;
import com.carlesso.pilatesapi.entity.Reavaliacao;
import com.carlesso.pilatesapi.entity.enums.OrigemPontoDor;
import com.carlesso.pilatesapi.entity.enums.VistaCorporal;
import com.carlesso.pilatesapi.exception.ResourceNotFoundException;
import com.carlesso.pilatesapi.repository.AvaliacaoFisioterapeuticaRepository;
import com.carlesso.pilatesapi.repository.PacienteRepository;
import com.carlesso.pilatesapi.repository.PontoDorRepository;
import com.carlesso.pilatesapi.repository.ReavaliacaoRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PontoDorServiceTest {

    @Mock
    private PontoDorRepository pontoDorRepository;

    @Mock
    private AvaliacaoFisioterapeuticaRepository avaliacaoRepository;

    @Mock
    private ReavaliacaoRepository reavaliacaoRepository;

    @Mock
    private PacienteRepository pacienteRepository;

    @InjectMocks
    private PontoDorService service;

    private final Paciente paciente = comId(new Paciente(), 7L);

    private static <T> T comId(T entidade, Long id) {
        ReflectionTestUtils.setField(entidade, "id", id);
        return entidade;
    }

    private AvaliacaoFisioterapeutica avaliacao(Long id, LocalDate data) {
        AvaliacaoFisioterapeutica avaliacao = comId(new AvaliacaoFisioterapeutica(), id);
        avaliacao.setPaciente(paciente);
        avaliacao.setDataAvaliacao(data);
        return avaliacao;
    }

    private Reavaliacao reavaliacao(Long id, LocalDate data) {
        Reavaliacao reavaliacao = comId(new Reavaliacao(), id);
        reavaliacao.setPaciente(paciente);
        reavaliacao.setDataReavaliacao(data);
        return reavaliacao;
    }

    private static PontoDor ponto(Long id, AvaliacaoFisioterapeutica avaliacao, Reavaliacao reavaliacao) {
        PontoDor ponto = comId(new PontoDor(), id);
        ponto.setAvaliacaoFisioterapeutica(avaliacao);
        ponto.setReavaliacao(reavaliacao);
        ponto.setVista(VistaCorporal.COSTAS);
        ponto.setX(new BigDecimal("0.500000"));
        ponto.setY(new BigDecimal("0.250000"));
        ponto.setDesenhoVersao(1);
        return ponto;
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<List<PontoDor>> captorDeLista() {
        return ArgumentCaptor.forClass(List.class);
    }

    @Test
    void substituirDaAvaliacao_apagaOConjuntoAntigoEGravaONovoLigadoAoPacienteDaAvaliacao() {
        AvaliacaoFisioterapeutica avaliacao = avaliacao(1L, LocalDate.of(2026, 10, 1));
        when(pontoDorRepository.travarAvaliacao(1L)).thenReturn(Optional.of(avaliacao));
        when(pontoDorRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        var dto = new PontosDorRequestDTO(List.of(
                new PontoDorRequestDTO(VistaCorporal.LATERAL_DIREITA, 0.1234567, 1.0, 1, 5, "  Dor lombar  "),
                new PontoDorRequestDTO(VistaCorporal.FRENTE, 0.0, 0.5, 2, null, "   ")));

        List<PontoDorResponseDTO> resposta = service.substituirDaAvaliacao(1L, dto);

        InOrder ordem = inOrder(pontoDorRepository);
        ordem.verify(pontoDorRepository).travarAvaliacao(1L);
        ordem.verify(pontoDorRepository).excluirDaAvaliacao(1L);
        ArgumentCaptor<List<PontoDor>> captor = captorDeLista();
        ordem.verify(pontoDorRepository).saveAll(captor.capture());

        List<PontoDor> gravados = captor.getValue();
        assertThat(gravados).hasSize(2);
        assertThat(gravados).allSatisfy(p -> {
            assertThat(p.getPaciente()).isSameAs(paciente);
            assertThat(p.getAvaliacaoFisioterapeutica()).isSameAs(avaliacao);
            assertThat(p.getReavaliacao()).isNull();
        });
        assertThat(gravados.get(0).getX()).isEqualByComparingTo("0.123457");
        assertThat(gravados.get(0).getObservacao()).isEqualTo("Dor lombar");
        assertThat(gravados.get(1).getObservacao()).isNull();
        assertThat(resposta)
                .extracting(PontoDorResponseDTO::vista, PontoDorResponseDTO::desenhoVersao)
                .containsExactly(tuple(VistaCorporal.LATERAL_DIREITA, 1), tuple(VistaCorporal.FRENTE, 2));
        assertThat(resposta.get(0).y()).isEqualTo(1.0);
    }

    @Test
    void substituirDaReavaliacao_comListaVazia_soApaga() {
        Reavaliacao reavaliacao = reavaliacao(2L, LocalDate.of(2026, 11, 1));
        when(pontoDorRepository.travarReavaliacao(2L)).thenReturn(Optional.of(reavaliacao));
        when(pontoDorRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        List<PontoDorResponseDTO> resposta = service.substituirDaReavaliacao(2L, new PontosDorRequestDTO(List.of()));

        assertThat(resposta).isEmpty();
        verify(pontoDorRepository).excluirDaReavaliacao(2L);
        verify(pontoDorRepository, never()).excluirDaAvaliacao(anyLong());
    }

    @Test
    void substituir_registroInexistente_lanca404SemApagarNada() {
        when(pontoDorRepository.travarAvaliacao(99L)).thenReturn(Optional.empty());
        when(pontoDorRepository.travarReavaliacao(98L)).thenReturn(Optional.empty());
        var dto = new PontosDorRequestDTO(List.of());

        assertThatThrownBy(() -> service.substituirDaAvaliacao(99L, dto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Avaliação fisioterapêutica não encontrada: 99");
        assertThatThrownBy(() -> service.substituirDaReavaliacao(98L, dto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Reavaliação não encontrada: 98");
        verify(pontoDorRepository, never()).excluirDaAvaliacao(99L);
        verify(pontoDorRepository, never()).excluirDaReavaliacao(98L);
    }

    @Test
    void substituir_acimaDoLimite_lancaIllegalArgument() {
        when(pontoDorRepository.travarAvaliacao(1L)).thenReturn(Optional.of(avaliacao(1L, LocalDate.now())));
        var ponto = new PontoDorRequestDTO(VistaCorporal.FRENTE, 0.5, 0.5, 1, null, null);
        var dto = new PontosDorRequestDTO(Collections.nCopies(PontosDorRequestDTO.MAXIMO_PONTOS + 1, ponto));

        assertThatThrownBy(() -> service.substituirDaAvaliacao(1L, dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("50");
        verify(pontoDorRepository, never()).saveAll(anyList());
    }

    @Test
    void listar_registroInexistente_lanca404() {
        when(avaliacaoRepository.existsById(1L)).thenReturn(false);
        when(reavaliacaoRepository.existsById(2L)).thenReturn(false);

        assertThatThrownBy(() -> service.listarDaAvaliacao(1L)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.listarDaReavaliacao(2L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listarDaAvaliacao_devolveOsPontosDela() {
        AvaliacaoFisioterapeutica avaliacao = avaliacao(1L, LocalDate.of(2026, 10, 1));
        when(avaliacaoRepository.existsById(1L)).thenReturn(true);
        when(pontoDorRepository.findByAvaliacaoFisioterapeuticaIdOrderByIdAsc(1L))
                .thenReturn(List.of(ponto(10L, avaliacao, null)));

        assertThat(service.listarDaAvaliacao(1L)).singleElement().satisfies(p -> {
            assertThat(p.id()).isEqualTo(10L);
            assertThat(p.x()).isEqualTo(0.5);
        });
    }

    @Test
    void historicoDoPaciente_agrupaPorRegistroEOrdenaPelaDataDoRegistro() {
        AvaliacaoFisioterapeutica avaliacao = avaliacao(1L, LocalDate.of(2026, 10, 1));
        Reavaliacao recente = reavaliacao(3L, LocalDate.of(2026, 11, 20));
        Reavaliacao mesmoDiaDaAvaliacao = reavaliacao(2L, LocalDate.of(2026, 10, 1));
        when(pacienteRepository.existsById(7L)).thenReturn(true);
        // Ordem do repositório é por id do ponto; o histórico reordena pela data do registro.
        when(pontoDorRepository.findByPacienteComRegistro(7L))
                .thenReturn(List.of(
                        ponto(10L, null, recente),
                        ponto(11L, null, mesmoDiaDaAvaliacao),
                        ponto(12L, avaliacao, null),
                        ponto(13L, null, recente)));

        List<PontosDorRegistroDTO> historico = service.historicoDoPaciente(7L);

        assertThat(historico)
                .extracting(PontosDorRegistroDTO::origem, PontosDorRegistroDTO::registroId, PontosDorRegistroDTO::data)
                .containsExactly(
                        tuple(OrigemPontoDor.AVALIACAO, 1L, LocalDate.of(2026, 10, 1)),
                        tuple(OrigemPontoDor.REAVALIACAO, 2L, LocalDate.of(2026, 10, 1)),
                        tuple(OrigemPontoDor.REAVALIACAO, 3L, LocalDate.of(2026, 11, 20)));
        assertThat(historico.get(2).pontos())
                .extracting(PontoDorResponseDTO::id)
                .containsExactly(10L, 13L);
    }

    @Test
    void historicoDoPaciente_pacienteInexistente_lanca404() {
        when(pacienteRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.historicoDoPaciente(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Paciente não encontrado: 99");
    }
}
