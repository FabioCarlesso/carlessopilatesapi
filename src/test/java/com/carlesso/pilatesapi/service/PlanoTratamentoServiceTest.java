package com.carlesso.pilatesapi.service;

import com.carlesso.pilatesapi.dto.PlanoTratamentoRequestDTO;
import com.carlesso.pilatesapi.dto.PlanoTratamentoResponseDTO;
import com.carlesso.pilatesapi.dto.PlanoTratamentoUpdateDTO;
import com.carlesso.pilatesapi.entity.Paciente;
import com.carlesso.pilatesapi.entity.PlanoTratamento;
import com.carlesso.pilatesapi.exception.ResourceNotFoundException;
import com.carlesso.pilatesapi.repository.PacienteRepository;
import com.carlesso.pilatesapi.repository.PlanoTratamentoRepository;
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
class PlanoTratamentoServiceTest {

    @Mock
    private PlanoTratamentoRepository planoTratamentoRepository;

    @Mock
    private PacienteRepository pacienteRepository;

    @InjectMocks
    private PlanoTratamentoService service;

    private Paciente paciente() {
        Paciente p = new Paciente();
        p.setNome("Maria Souza");
        p.setEmail("maria@email.com");
        p.setCpf("12345678900");
        setId(p, 1L);
        return p;
    }

    private PlanoTratamento plano(Paciente paciente) {
        PlanoTratamento p = new PlanoTratamento();
        p.setPaciente(paciente);
        p.setDataInicio(LocalDate.of(2026, 5, 1));
        p.setDataFimPrevista(LocalDate.of(2026, 8, 1));
        p.setObjetivosTratamento("Reduzir dor lombar e melhorar mobilidade");
        p.setIntervencoesPlanejadas("Exercícios de core e alongamentos");
        p.setNumeroSessoesPrevistas(24);
        p.setFrequenciaSessoes("2x por semana");
        p.setResponsavelTratamento("Dr. João");
        p.setObservacoes("Paciente deve evitar impacto");
        p.setDataCriacao(LocalDateTime.of(2026, 5, 1, 10, 0));
        setPlanoId(p, 1L);
        return p;
    }

    private PlanoTratamentoRequestDTO requestDTO() {
        return new PlanoTratamentoRequestDTO(
                1L,
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 8, 1),
                "Reduzir dor lombar e melhorar mobilidade",
                "Exercícios de core e alongamentos",
                24,
                "2x por semana",
                "Dr. João",
                "Paciente deve evitar impacto"
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

    private void setPlanoId(PlanoTratamento p, Long id) {
        try {
            var field = PlanoTratamento.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(p, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void criar_comPacienteValido_deveRetornarResponseDTO() {
        Paciente p = paciente();
        when(pacienteRepository.findByIdAndAtivoTrue(1L)).thenReturn(Optional.of(p));
        when(planoTratamentoRepository.save(any(PlanoTratamento.class))).thenReturn(plano(p));

        PlanoTratamentoResponseDTO response = service.criar(requestDTO());

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.pacienteId()).isEqualTo(1L);
        assertThat(response.nomePaciente()).isEqualTo("Maria Souza");
        assertThat(response.objetivosTratamento()).isEqualTo("Reduzir dor lombar e melhorar mobilidade");
        assertThat(response.numeroSessoesPrevistas()).isEqualTo(24);
        verify(planoTratamentoRepository).save(any(PlanoTratamento.class));
    }

    @Test
    void criar_comPacienteInexistente_deveLancarResourceNotFoundException() {
        when(pacienteRepository.findByIdAndAtivoTrue(99L)).thenReturn(Optional.empty());

        var dto = new PlanoTratamentoRequestDTO(
                99L, LocalDate.of(2026, 5, 1), null,
                "Objetivos", null, null, null, null, null
        );

        assertThatThrownBy(() -> service.criar(dto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void buscarPorId_quandoExistente_deveRetornarResponseDTO() {
        when(planoTratamentoRepository.findAtivoById(1L)).thenReturn(Optional.of(plano(paciente())));

        PlanoTratamentoResponseDTO response = service.buscarPorId(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.frequenciaSessoes()).isEqualTo("2x por semana");
    }

    @Test
    void buscarPorId_quandoNaoExistente_deveLancarResourceNotFoundException() {
        when(planoTratamentoRepository.findAtivoById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscarPorId(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void listarPorPaciente_deveRetornarPlanosDoPaciente() {
        Paciente p = paciente();
        when(pacienteRepository.existsByIdAndAtivoTrue(1L)).thenReturn(true);
        when(planoTratamentoRepository.findAtivosByPacienteOrdenados(1L)).thenReturn(List.of(plano(p)));

        List<PlanoTratamentoResponseDTO> response = service.listarPorPaciente(1L);

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().pacienteId()).isEqualTo(1L);
        assertThat(response.getFirst().dataInicio()).isEqualTo(LocalDate.of(2026, 5, 1));
    }

    @Test
    void listarPorPaciente_comPacienteAtivoSemPlanos_deveRetornarListaVazia() {
        when(pacienteRepository.existsByIdAndAtivoTrue(1L)).thenReturn(true);
        when(planoTratamentoRepository.findAtivosByPacienteOrdenados(1L)).thenReturn(List.of());

        List<PlanoTratamentoResponseDTO> response = service.listarPorPaciente(1L);

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
        PlanoTratamento p = plano(paciente());
        when(planoTratamentoRepository.findAtivoById(1L)).thenReturn(Optional.of(p));
        when(planoTratamentoRepository.save(p)).thenReturn(p);

        var dto = new PlanoTratamentoUpdateDTO(
                LocalDate.of(2026, 5, 10),
                LocalDate.of(2026, 9, 1),
                "Objetivos atualizados",
                null,
                30,
                null,
                null,
                "Evoluindo bem"
        );

        PlanoTratamentoResponseDTO response = service.atualizar(1L, dto);

        assertThat(response.dataInicio()).isEqualTo(LocalDate.of(2026, 5, 10));
        assertThat(response.dataFimPrevista()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(response.objetivosTratamento()).isEqualTo("Objetivos atualizados");
        assertThat(response.numeroSessoesPrevistas()).isEqualTo(30);
        assertThat(response.frequenciaSessoes()).isEqualTo("2x por semana");
        assertThat(response.dataAtualizacao()).isNotNull();
    }

    @Test
    void atualizar_comPlanoInexistente_deveLancarResourceNotFoundException() {
        when(planoTratamentoRepository.findAtivoById(99L)).thenReturn(Optional.empty());

        var dto = new PlanoTratamentoUpdateDTO(null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.atualizar(99L, dto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }
}
