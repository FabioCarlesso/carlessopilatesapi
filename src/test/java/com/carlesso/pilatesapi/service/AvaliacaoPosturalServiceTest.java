package com.carlesso.pilatesapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.carlesso.pilatesapi.dto.AvaliacaoPosturalRequestDTO;
import com.carlesso.pilatesapi.dto.AvaliacaoPosturalUpdateDTO;
import com.carlesso.pilatesapi.dto.LandmarkDTO;
import com.carlesso.pilatesapi.entity.AvaliacaoFisioterapeutica;
import com.carlesso.pilatesapi.entity.AvaliacaoPostural;
import com.carlesso.pilatesapi.entity.Paciente;
import com.carlesso.pilatesapi.entity.enums.CodigoLandmark;
import com.carlesso.pilatesapi.entity.enums.StatusAvaliacaoPostural;
import com.carlesso.pilatesapi.entity.enums.VistaPostural;
import com.carlesso.pilatesapi.exception.BusinessException;
import com.carlesso.pilatesapi.exception.ConflictException;
import com.carlesso.pilatesapi.exception.ResourceNotFoundException;
import com.carlesso.pilatesapi.repository.AvaliacaoFisioterapeuticaRepository;
import com.carlesso.pilatesapi.repository.AvaliacaoPosturalRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AvaliacaoPosturalServiceTest {

    @Mock
    private AvaliacaoPosturalRepository avaliacaoPosturalRepository;

    @Mock
    private AvaliacaoFisioterapeuticaRepository avaliacaoFisioterapeuticaRepository;

    private AvaliacaoPosturalService service;

    @BeforeEach
    void setUp() {
        service = new AvaliacaoPosturalService(
                avaliacaoPosturalRepository, avaliacaoFisioterapeuticaRepository, new ObjectMapper());
    }

    private AvaliacaoFisioterapeutica avaliacaoFisioterapeutica() {
        Paciente paciente = new Paciente();
        paciente.setNome("Maria Souza");
        setId(Paciente.class, paciente, 1L);

        AvaliacaoFisioterapeutica a = new AvaliacaoFisioterapeutica();
        a.setPaciente(paciente);
        a.setDataAvaliacao(LocalDate.of(2026, 4, 20));
        setId(AvaliacaoFisioterapeutica.class, a, 1L);
        return a;
    }

    private AvaliacaoPostural analise(VistaPostural vista, StatusAvaliacaoPostural status) {
        AvaliacaoPostural analise = new AvaliacaoPostural();
        analise.setAvaliacaoFisioterapeutica(avaliacaoFisioterapeutica());
        analise.setVista(vista);
        analise.setStatus(status);
        analise.setDataCriacao(LocalDateTime.of(2026, 4, 20, 10, 0));
        setId(AvaliacaoPostural.class, analise, 10L);
        return analise;
    }

    private LandmarkDTO landmark(CodigoLandmark codigo, String x, String y) {
        return new LandmarkDTO(codigo, new BigDecimal(x), new BigDecimal(y));
    }

    private AvaliacaoPosturalUpdateDTO updateComLandmarks(List<LandmarkDTO> landmarks) {
        return new AvaliacaoPosturalUpdateDTO(null, null, null, null, landmarks);
    }

    private void devolverAnaliseSalva() {
        when(avaliacaoPosturalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private void setId(Class<?> tipo, Object alvo, Long id) {
        try {
            var field = tipo.getDeclaredField("id");
            field.setAccessible(true);
            field.set(alvo, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void criar_comAvaliacaoValida_deveNascerEmRascunho() {
        when(avaliacaoFisioterapeuticaRepository.findAtivaById(1L))
                .thenReturn(Optional.of(avaliacaoFisioterapeutica()));
        when(avaliacaoPosturalRepository.existsByAvaliacaoFisioterapeuticaIdAndVistaAndAtivoTrue(
                        1L, VistaPostural.FRENTE))
                .thenReturn(false);
        devolverAnaliseSalva();

        var response = service.criar(new AvaliacaoPosturalRequestDTO(1L, VistaPostural.FRENTE));

        assertThat(response.status()).isEqualTo(StatusAvaliacaoPostural.RASCUNHO);
        assertThat(response.vista()).isEqualTo(VistaPostural.FRENTE);
        assertThat(response.avaliacaoFisioterapeuticaId()).isEqualTo(1L);
        assertThat(response.temFoto()).isFalse();
        assertThat(response.landmarks()).isEmpty();
    }

    @Test
    void criar_comAvaliacaoInexistenteOuPacienteInativo_deveLancarResourceNotFound() {
        when(avaliacaoFisioterapeuticaRepository.findAtivaById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.criar(new AvaliacaoPosturalRequestDTO(99L, VistaPostural.FRENTE)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Avaliação fisioterapêutica não encontrada: 99");

        verify(avaliacaoPosturalRepository, never()).save(any());
    }

    @Test
    void criar_comVistaJaAtivaNaAvaliacao_deveLancarConflict() {
        when(avaliacaoFisioterapeuticaRepository.findAtivaById(1L))
                .thenReturn(Optional.of(avaliacaoFisioterapeutica()));
        when(avaliacaoPosturalRepository.existsByAvaliacaoFisioterapeuticaIdAndVistaAndAtivoTrue(
                        1L, VistaPostural.FRENTE))
                .thenReturn(true);

        assertThatThrownBy(() -> service.criar(new AvaliacaoPosturalRequestDTO(1L, VistaPostural.FRENTE)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("FRENTE");

        verify(avaliacaoPosturalRepository, never()).save(any());
    }

    @Test
    void buscarPorId_quandoInexistenteOuCancelada_deveLancarResourceNotFound() {
        when(avaliacaoPosturalRepository.findAtivaById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscarPorId(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Análise postural não encontrada: 99");
    }

    @Test
    void listarPorAvaliacaoFisioterapeutica_comAvaliacaoInexistente_deveLancarResourceNotFound() {
        when(avaliacaoFisioterapeuticaRepository.findAtivaById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listarPorAvaliacaoFisioterapeutica(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void atualizar_comLandmarksValidos_devePersistirECalcularMetricas() {
        when(avaliacaoPosturalRepository.findAtivaById(10L))
                .thenReturn(Optional.of(analise(VistaPostural.FRENTE, StatusAvaliacaoPostural.RASCUNHO)));
        devolverAnaliseSalva();

        // Ombros com Δx = 0.2 e Δy = 0.2 → reta a 45°.
        var dto = new AvaliacaoPosturalUpdateDTO(
                new BigDecimal("0.480"),
                null,
                null,
                "Elevação de ombro D",
                List.of(
                        landmark(CodigoLandmark.OMBRO_ESQ, "0.4", "0.2"),
                        landmark(CodigoLandmark.OMBRO_DIR, "0.6", "0.4")));

        var response = service.atualizar(10L, dto);

        assertThat(response.metricas().desnivelOmbrosGraus()).isEqualByComparingTo("45.00");
        assertThat(response.metricas().inclinacaoCabecaGraus()).isNull();
        assertThat(response.metricas().desnivelQuadrilGraus()).isNull();
        assertThat(response.landmarks()).hasSize(2);
        assertThat(response.observacoes()).isEqualTo("Elevação de ombro D");
        assertThat(response.dataAtualizacao()).isNotNull();
    }

    @Test
    void atualizar_comPontosNivelados_deveCalcularZeroGraus() {
        when(avaliacaoPosturalRepository.findAtivaById(10L))
                .thenReturn(Optional.of(analise(VistaPostural.FRENTE, StatusAvaliacaoPostural.RASCUNHO)));
        devolverAnaliseSalva();

        var response = service.atualizar(
                10L,
                updateComLandmarks(List.of(
                        landmark(CodigoLandmark.OLHO_ESQ, "0.45", "0.12"),
                        landmark(CodigoLandmark.OLHO_DIR, "0.55", "0.12"))));

        assertThat(response.metricas().inclinacaoCabecaGraus()).isEqualByComparingTo("0.00");
    }

    @Test
    void atualizar_comProporcaoImagem_deveCorrigirOAnguloPelaRazaoDaFoto() {
        when(avaliacaoPosturalRepository.findAtivaById(10L))
                .thenReturn(Optional.of(analise(VistaPostural.FRENTE, StatusAvaliacaoPostural.RASCUNHO)));
        devolverAnaliseSalva();

        // Δx normalizado 0.2 * proporção 0.5 = 0.1 efetivo; Δy = 0.2 → atan2(0.2, 0.1).
        var dto = new AvaliacaoPosturalUpdateDTO(
                null,
                null,
                new BigDecimal("0.5"),
                null,
                List.of(
                        landmark(CodigoLandmark.OMBRO_ESQ, "0.4", "0.2"),
                        landmark(CodigoLandmark.OMBRO_DIR, "0.6", "0.4")));

        var response = service.atualizar(10L, dto);

        assertThat(response.metricas().desnivelOmbrosGraus()).isEqualByComparingTo("63.43");
        assertThat(response.proporcaoImagem()).isEqualByComparingTo("0.5");
    }

    @Test
    void atualizar_semCalibracao_deveTrazerDesvioApenasNormalizado() {
        when(avaliacaoPosturalRepository.findAtivaById(10L))
                .thenReturn(Optional.of(analise(VistaPostural.FRENTE, StatusAvaliacaoPostural.RASCUNHO)));
        devolverAnaliseSalva();

        // Ponto médio dos ombros = 0.5; prumo em 0.48 → desvio 0.02.
        var dto = new AvaliacaoPosturalUpdateDTO(
                new BigDecimal("0.48"),
                null,
                null,
                null,
                List.of(
                        landmark(CodigoLandmark.OMBRO_ESQ, "0.4", "0.2"),
                        landmark(CodigoLandmark.OMBRO_DIR, "0.6", "0.2")));

        var response = service.atualizar(10L, dto);

        assertThat(response.metricas().desvioPrumoNormalizado()).isEqualByComparingTo("0.0200");
        assertThat(response.metricas().desvioPrumoCm()).isNull();
    }

    @Test
    void atualizar_comCalibracao_deveConverterDesvioParaCentimetros() {
        when(avaliacaoPosturalRepository.findAtivaById(10L))
                .thenReturn(Optional.of(analise(VistaPostural.FRENTE, StatusAvaliacaoPostural.RASCUNHO)));
        devolverAnaliseSalva();

        var dto = new AvaliacaoPosturalUpdateDTO(
                new BigDecimal("0.48"),
                new BigDecimal("50"),
                null,
                null,
                List.of(
                        landmark(CodigoLandmark.OMBRO_ESQ, "0.4", "0.2"),
                        landmark(CodigoLandmark.OMBRO_DIR, "0.6", "0.2")));

        var response = service.atualizar(10L, dto);

        assertThat(response.metricas().desvioPrumoCm()).isEqualByComparingTo("1.00");
    }

    @Test
    void atualizar_comLandmarkDeOutraVista_deveLancarBusinessException() {
        when(avaliacaoPosturalRepository.findAtivaById(10L))
                .thenReturn(Optional.of(analise(VistaPostural.FRENTE, StatusAvaliacaoPostural.RASCUNHO)));

        assertThatThrownBy(() -> service.atualizar(
                        10L, updateComLandmarks(List.of(landmark(CodigoLandmark.ORELHA, "0.4", "0.2")))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ORELHA");

        verify(avaliacaoPosturalRepository, never()).save(any());
    }

    @Test
    void atualizar_comLandmarkDuplicado_deveLancarBusinessException() {
        when(avaliacaoPosturalRepository.findAtivaById(10L))
                .thenReturn(Optional.of(analise(VistaPostural.FRENTE, StatusAvaliacaoPostural.RASCUNHO)));

        assertThatThrownBy(() -> service.atualizar(
                        10L,
                        updateComLandmarks(List.of(
                                landmark(CodigoLandmark.OMBRO_ESQ, "0.4", "0.2"),
                                landmark(CodigoLandmark.OMBRO_ESQ, "0.41", "0.21")))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("duplicidade");
    }

    @Test
    void atualizar_emAnaliseConcluida_deveLancarBusinessException() {
        when(avaliacaoPosturalRepository.findAtivaById(10L))
                .thenReturn(Optional.of(analise(VistaPostural.FRENTE, StatusAvaliacaoPostural.CONCLUIDA)));

        assertThatThrownBy(() -> service.atualizar(
                        10L, updateComLandmarks(List.of(landmark(CodigoLandmark.OMBRO_ESQ, "0.4", "0.2")))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("concluída");

        verify(avaliacaoPosturalRepository, never()).save(any());
    }

    @Test
    void concluir_comPontosIncompletos_deveLancarBusinessException() {
        AvaliacaoPostural analise = analise(VistaPostural.LADO_DIREITO, StatusAvaliacaoPostural.RASCUNHO);
        analise.setFotoContentType("image/jpeg");
        analise.setLandmarks("[{\"codigo\":\"ORELHA\",\"x\":0.5,\"y\":0.1}]");
        when(avaliacaoPosturalRepository.findAtivaById(10L)).thenReturn(Optional.of(analise));

        assertThatThrownBy(() -> service.concluir(10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Pontos obrigatórios não marcados");
    }

    @Test
    void concluir_semFoto_deveLancarBusinessException() {
        AvaliacaoPostural analise = analise(VistaPostural.LADO_DIREITO, StatusAvaliacaoPostural.RASCUNHO);
        analise.setLandmarks(landmarksCompletosLaterais());
        when(avaliacaoPosturalRepository.findAtivaById(10L)).thenReturn(Optional.of(analise));

        assertThatThrownBy(() -> service.concluir(10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("sem foto");
    }

    @Test
    void concluir_comPontosCompletosEFoto_deveMarcarComoConcluida() {
        AvaliacaoPostural analise = analise(VistaPostural.LADO_DIREITO, StatusAvaliacaoPostural.RASCUNHO);
        analise.setLandmarks(landmarksCompletosLaterais());
        analise.setFotoContentType("image/jpeg");
        when(avaliacaoPosturalRepository.findAtivaById(10L)).thenReturn(Optional.of(analise));
        devolverAnaliseSalva();

        var response = service.concluir(10L);

        assertThat(response.status()).isEqualTo(StatusAvaliacaoPostural.CONCLUIDA);
        assertThat(response.temFoto()).isTrue();
    }

    @Test
    void concluir_emAnaliseJaConcluida_deveLancarBusinessException() {
        when(avaliacaoPosturalRepository.findAtivaById(10L))
                .thenReturn(Optional.of(analise(VistaPostural.FRENTE, StatusAvaliacaoPostural.CONCLUIDA)));

        assertThatThrownBy(() -> service.concluir(10L)).isInstanceOf(BusinessException.class);
    }

    @Test
    void cancelar_deveFazerExclusaoLogica() {
        AvaliacaoPostural analise = analise(VistaPostural.FRENTE, StatusAvaliacaoPostural.CONCLUIDA);
        when(avaliacaoPosturalRepository.findAtivaById(10L)).thenReturn(Optional.of(analise));
        devolverAnaliseSalva();

        service.cancelar(10L);

        assertThat(analise.isAtivo()).isFalse();
        assertThat(analise.getDataAtualizacao()).isNotNull();
        verify(avaliacaoPosturalRepository).save(analise);
    }

    private String landmarksCompletosLaterais() {
        return """
                [
                  {"codigo":"ORELHA","x":0.50,"y":0.10},
                  {"codigo":"OMBRO","x":0.49,"y":0.25},
                  {"codigo":"QUADRIL","x":0.50,"y":0.50},
                  {"codigo":"JOELHO","x":0.51,"y":0.75},
                  {"codigo":"TORNOZELO","x":0.50,"y":0.95}
                ]
                """;
    }
}
