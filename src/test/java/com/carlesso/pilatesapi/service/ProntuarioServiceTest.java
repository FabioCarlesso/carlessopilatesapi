package com.carlesso.pilatesapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.carlesso.pilatesapi.entity.AvaliacaoFisioterapeutica;
import com.carlesso.pilatesapi.entity.Paciente;
import com.carlesso.pilatesapi.repository.AnamneseRepository;
import com.carlesso.pilatesapi.repository.AvaliacaoFisioterapeuticaRepository;
import com.carlesso.pilatesapi.repository.EvolucaoSessaoRepository;
import com.carlesso.pilatesapi.repository.PacienteRepository;
import com.carlesso.pilatesapi.repository.PlanoTratamentoRepository;
import com.carlesso.pilatesapi.repository.ReavaliacaoRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ProntuarioServiceTest {

    private final PacienteRepository pacienteRepository = mock(PacienteRepository.class);
    private final AnamneseRepository anamneseRepository = mock(AnamneseRepository.class);
    private final AvaliacaoFisioterapeuticaRepository avaliacaoRepository =
            mock(AvaliacaoFisioterapeuticaRepository.class);
    private final PlanoTratamentoRepository planoRepository = mock(PlanoTratamentoRepository.class);
    private final ReavaliacaoRepository reavaliacaoRepository = mock(ReavaliacaoRepository.class);
    private final EvolucaoSessaoRepository evolucaoRepository = mock(EvolucaoSessaoRepository.class);
    private final ProntuarioPdfExporterService exporter = mock(ProntuarioPdfExporterService.class);

    private final ProntuarioService service = new ProntuarioService(
            pacienteRepository,
            anamneseRepository,
            avaliacaoRepository,
            planoRepository,
            reavaliacaoRepository,
            evolucaoRepository,
            exporter,
            () -> Optional.of("paula@email.com"),
            // 13:30 UTC = 10:30 em São Paulo: a data de geração sai no fuso do estúdio.
            Clock.fixed(Instant.parse("2026-11-27T13:30:00Z"), ZoneId.of("America/Sao_Paulo")));

    @Test
    void exportarProntuario_inicioDepoisDoFim_recusaSemConsultar() {
        assertThatThrownBy(() -> service.exportarProntuario(1L, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 6, 30)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Período inicial não pode ser maior que o período final");
        verifyNoInteractions(pacienteRepository, exporter);
    }

    @Test
    void exportarProntuario_semPeriodo_consultaComSentinelasEMantemOPeriodoAbertoNoPdf() {
        Paciente paciente = new Paciente();
        paciente.setNome("Ana Silva");
        when(pacienteRepository.findById(1L)).thenReturn(Optional.of(paciente));
        when(anamneseRepository.findByPacienteId(1L)).thenReturn(Optional.empty());
        when(exporter.exportarProntuario(any())).thenReturn(new byte[] {1});

        var arquivo = service.exportarProntuario(1L, null, null);

        assertThat(arquivo.nomeArquivo()).isEqualTo("prontuario-ana-silva-completo.pdf");
        verify(evolucaoRepository)
                .findProntuario(eq(1L), eq(ProntuarioService.INICIO_ABERTO), eq(ProntuarioService.FIM_ABERTO));
        var captor = ArgumentCaptor.forClass(ProntuarioPdfExporterService.Prontuario.class);
        verify(exporter).exportarProntuario(captor.capture());
        assertThat(captor.getValue().inicio()).isNull();
        assertThat(captor.getValue().fim()).isNull();
        assertThat(captor.getValue().geradoEm()).isEqualTo(LocalDateTime.of(2026, 11, 27, 10, 30));
    }

    @Test
    void avaliacoesVigentes_mantemAUltimaAnteriorAoInicioEAsDoPeriodo() {
        var antiga = avaliacao(LocalDate.of(2025, 1, 1));
        var vigenteNoInicio = avaliacao(LocalDate.of(2025, 12, 1));
        var doPeriodo = avaliacao(LocalDate.of(2026, 3, 1));

        assertThat(ProntuarioService.avaliacoesVigentes(
                        List.of(antiga, vigenteNoInicio, doPeriodo), LocalDate.of(2026, 1, 1)))
                .containsExactly(vigenteNoInicio, doPeriodo);
        assertThat(ProntuarioService.avaliacoesVigentes(List.of(antiga, vigenteNoInicio), null))
                .containsExactly(antiga, vigenteNoInicio);
    }

    @Test
    void nomeArquivoProntuario_segueOContrato() {
        assertThat(ProntuarioService.nomeArquivoProntuario(
                        "Ana Silva", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30)))
                .isEqualTo("prontuario-ana-silva-2026-01-01-a-2026-06-30.pdf");
        assertThat(ProntuarioService.nomeArquivoProntuario("Ana Silva", LocalDate.of(2026, 1, 1), null))
                .isEqualTo("prontuario-ana-silva-2026-01-01-a-hoje.pdf");
        assertThat(ProntuarioService.nomeArquivoProntuario("Ana Silva", null, LocalDate.of(2026, 6, 30)))
                .isEqualTo("prontuario-ana-silva-inicio-a-2026-06-30.pdf");
    }

    @Test
    void slug_removeAcentosESimbolos() {
        assertThat(ProntuarioService.slug("  João D'Ávila & Conceição ")).isEqualTo("joao-d-avila-conceicao");
        assertThat(ProntuarioService.slug("***")).isEqualTo("paciente");
    }

    private AvaliacaoFisioterapeutica avaliacao(LocalDate data) {
        AvaliacaoFisioterapeutica avaliacao = new AvaliacaoFisioterapeutica();
        avaliacao.setDataAvaliacao(data);
        return avaliacao;
    }
}
