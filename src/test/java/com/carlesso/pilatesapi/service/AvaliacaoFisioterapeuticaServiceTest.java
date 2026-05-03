package com.carlesso.pilatesapi.service;

import com.carlesso.pilatesapi.dto.AvaliacaoFisioterapeuticaRequestDTO;
import com.carlesso.pilatesapi.dto.AvaliacaoFisioterapeuticaResponseDTO;
import com.carlesso.pilatesapi.dto.AvaliacaoFisioterapeuticaUpdateDTO;
import com.carlesso.pilatesapi.entity.AvaliacaoFisioterapeutica;
import com.carlesso.pilatesapi.entity.Paciente;
import com.carlesso.pilatesapi.exception.ResourceNotFoundException;
import com.carlesso.pilatesapi.repository.AvaliacaoFisioterapeuticaRepository;
import com.carlesso.pilatesapi.repository.PacienteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AvaliacaoFisioterapeuticaServiceTest {

    @Mock
    private AvaliacaoFisioterapeuticaRepository avaliacaoRepository;

    @Mock
    private PacienteRepository pacienteRepository;

    @InjectMocks
    private AvaliacaoFisioterapeuticaService service;

    private Paciente paciente() {
        Paciente p = new Paciente();
        p.setNome("Maria Souza");
        p.setEmail("maria@email.com");
        p.setCpf("12345678900");
        setId(p, 1L);
        return p;
    }

    private AvaliacaoFisioterapeutica avaliacao(Paciente paciente) {
        AvaliacaoFisioterapeutica a = new AvaliacaoFisioterapeutica();
        a.setPaciente(paciente);
        a.setDataAvaliacao(LocalDate.of(2026, 4, 20));
        a.setQueixaFuncional("Dor ao agachar");
        a.setAvaliacaoPostural("Anteriorização de cabeça");
        a.setMobilidadeArticular("Restrição em quadril direito");
        a.setForcaMuscular("Glúteo médio grau 4");
        a.setFlexibilidade("Encurtamento de cadeia posterior");
        a.setEquilibrio("Instável em apoio unipodal");
        a.setCoordenacaoMotora("Boa coordenação");
        a.setPadraoRespiratorio("Respiração apical");
        a.setEscalaDor(6);
        a.setTestesFuncionaisRealizados("Agachamento, ponte, apoio unipodal");
        a.setDiagnosticoFisioterapeutico("Disfunção lombopélvica");
        a.setObservacoesGerais("Reavaliar em 30 dias");
        a.setDataCriacao(LocalDateTime.of(2026, 4, 20, 10, 0));
        setAvaliacaoId(a, 1L);
        return a;
    }

    private AvaliacaoFisioterapeuticaRequestDTO requestDTO() {
        return new AvaliacaoFisioterapeuticaRequestDTO(
                1L,
                LocalDate.of(2026, 4, 20),
                "Dor ao agachar",
                "Anteriorização de cabeça",
                "Restrição em quadril direito",
                "Glúteo médio grau 4",
                "Encurtamento de cadeia posterior",
                "Instável em apoio unipodal",
                "Boa coordenação",
                "Respiração apical",
                6,
                "Agachamento, ponte, apoio unipodal",
                "Disfunção lombopélvica",
                "Reavaliar em 30 dias"
        );
    }

    private void setId(Paciente p, Long id) {
        try {
            var field = Paciente.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(p, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setAvaliacaoId(AvaliacaoFisioterapeutica a, Long id) {
        try {
            var field = AvaliacaoFisioterapeutica.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(a, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void criar_comPacienteValido_deveRetornarResponseDTO() {
        Paciente p = paciente();
        when(pacienteRepository.findByIdAndAtivoTrue(1L)).thenReturn(Optional.of(p));
        when(avaliacaoRepository.save(any(AvaliacaoFisioterapeutica.class))).thenReturn(avaliacao(p));

        AvaliacaoFisioterapeuticaResponseDTO response = service.criar(requestDTO());

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.pacienteId()).isEqualTo(1L);
        assertThat(response.nomePaciente()).isEqualTo("Maria Souza");
        assertThat(response.queixaFuncional()).isEqualTo("Dor ao agachar");
        assertThat(response.escalaDor()).isEqualTo(6);
        verify(avaliacaoRepository).save(any(AvaliacaoFisioterapeutica.class));
    }

    @Test
    void criar_comPacienteInexistente_deveLancarResourceNotFoundException() {
        when(pacienteRepository.findByIdAndAtivoTrue(99L)).thenReturn(Optional.empty());

        var dto = new AvaliacaoFisioterapeuticaRequestDTO(
                99L, LocalDate.of(2026, 4, 20), "Dor", null, null, null, null,
                null, null, null, 5, null, "Diagnóstico", null
        );

        assertThatThrownBy(() -> service.criar(dto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void buscarPorId_quandoExistente_deveRetornarResponseDTO() {
        when(avaliacaoRepository.findAtivaById(1L)).thenReturn(Optional.of(avaliacao(paciente())));

        AvaliacaoFisioterapeuticaResponseDTO response = service.buscarPorId(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.diagnosticoFisioterapeutico()).isEqualTo("Disfunção lombopélvica");
    }

    @Test
    void buscarPorId_quandoNaoExistente_deveLancarResourceNotFoundException() {
        when(avaliacaoRepository.findAtivaById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscarPorId(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void listarPorPaciente_deveRetornarAvaliacoesDoPaciente() {
        Paciente p = paciente();
        when(pacienteRepository.existsByIdAndAtivoTrue(1L)).thenReturn(true);
        when(avaliacaoRepository.findAtivasByPacienteOrdenadas(1L)).thenReturn(List.of(avaliacao(p)));

        List<AvaliacaoFisioterapeuticaResponseDTO> response = service.listarPorPaciente(1L);

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().pacienteId()).isEqualTo(1L);
        assertThat(response.getFirst().dataAvaliacao()).isEqualTo(LocalDate.of(2026, 4, 20));
    }

    @Test
    void listarPorPaciente_comPacienteAtivoSemAvaliacoes_deveRetornarListaVazia() {
        when(pacienteRepository.existsByIdAndAtivoTrue(1L)).thenReturn(true);
        when(avaliacaoRepository.findAtivasByPacienteOrdenadas(1L)).thenReturn(List.of());

        List<AvaliacaoFisioterapeuticaResponseDTO> response = service.listarPorPaciente(1L);

        assertThat(response).isEmpty();
    }

    @Test
    void listarPorPaciente_comPacienteInexistente_deveLancarResourceNotFoundException() {
        when(pacienteRepository.existsByIdAndAtivoTrue(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.listarPorPaciente(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void atualizar_deveAtualizarApenasOsCamposInformados() {
        AvaliacaoFisioterapeutica a = avaliacao(paciente());
        when(avaliacaoRepository.findAtivaById(1L)).thenReturn(Optional.of(a));
        when(avaliacaoRepository.save(a)).thenReturn(a);

        var dto = new AvaliacaoFisioterapeuticaUpdateDTO(
                LocalDate.of(2026, 5, 5),
                "Dor reduzida ao agachar",
                null, null, null, null, null, null, null,
                3,
                null,
                null,
                "Evoluiu com exercícios"
        );

        AvaliacaoFisioterapeuticaResponseDTO response = service.atualizar(1L, dto);

        assertThat(response.dataAvaliacao()).isEqualTo(LocalDate.of(2026, 5, 5));
        assertThat(response.queixaFuncional()).isEqualTo("Dor reduzida ao agachar");
        assertThat(response.escalaDor()).isEqualTo(3);
        assertThat(response.diagnosticoFisioterapeutico()).isEqualTo("Disfunção lombopélvica");
        assertThat(response.dataAtualizacao()).isNotNull();
    }

    @Test
    void atualizar_comAvaliacaoInexistente_deveLancarResourceNotFoundException() {
        when(avaliacaoRepository.findAtivaById(99L)).thenReturn(Optional.empty());

        var dto = new AvaliacaoFisioterapeuticaUpdateDTO(
                null, null, null, null, null, null, null, null, null, null, null, null, null
        );

        assertThatThrownBy(() -> service.atualizar(99L, dto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }
}
