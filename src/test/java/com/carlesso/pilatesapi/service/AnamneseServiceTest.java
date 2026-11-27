package com.carlesso.pilatesapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.carlesso.pilatesapi.dto.AnamneseRequestDTO;
import com.carlesso.pilatesapi.dto.AnamneseResponseDTO;
import com.carlesso.pilatesapi.dto.AnamneseUpdateDTO;
import com.carlesso.pilatesapi.entity.Anamnese;
import com.carlesso.pilatesapi.entity.Paciente;
import com.carlesso.pilatesapi.exception.BusinessException;
import com.carlesso.pilatesapi.exception.ConflictException;
import com.carlesso.pilatesapi.exception.ResourceNotFoundException;
import com.carlesso.pilatesapi.repository.AnamneseRepository;
import com.carlesso.pilatesapi.repository.PacienteRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class AnamneseServiceTest {

    @Mock
    private AnamneseRepository anamneseRepository;

    @Mock
    private PacienteRepository pacienteRepository;

    @InjectMocks
    private AnamneseService service;

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Paciente paciente() {
        Paciente p = new Paciente();
        p.setNome("Maria Souza");
        p.setEmail("maria@email.com");
        p.setCpf("12345678900");
        setId(p, 1L);
        return p;
    }

    private Paciente pacienteInativo() {
        Paciente p = paciente();
        p.setAtivo(false);
        return p;
    }

    private Anamnese anamnese(Paciente paciente) {
        Anamnese a = new Anamnese();
        a.setPaciente(paciente);
        a.setQueixaPrincipal("Dor lombar");
        a.setHistoricoDoencas("Hipertensão");
        a.setHistoricoCirurgias("Nenhuma");
        a.setHistoricoLesoes("Entorse tornozelo");
        a.setMedicamentosUso("Losartana");
        a.setAlergias("Nenhuma");
        a.setNivelAtividadeFisica("Sedentário");
        a.setRestricoesMedicas("Evitar impacto");
        a.setObjetivos("Melhorar postura e reduzir dores");
        a.setObservacoes("Paciente relata estresse");
        a.setDataCriacao(LocalDateTime.of(2026, 1, 15, 10, 0));
        setAnamneseId(a, 1L);
        return a;
    }

    private AnamneseRequestDTO requestDTO() {
        return new AnamneseRequestDTO(
                1L,
                "Dor lombar",
                "Hipertensão",
                "Nenhuma",
                "Entorse tornozelo",
                "Losartana",
                "Nenhuma",
                "Sedentário",
                "Evitar impacto",
                "Melhorar postura e reduzir dores",
                "Paciente relata estresse",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
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

    private void setAnamneseId(Anamnese a, Long id) {
        try {
            var field = Anamnese.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(a, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // -------------------------------------------------------------------------
    // criar
    // -------------------------------------------------------------------------

    @Test
    void criar_comPacienteValido_deveRetornarResponseDTO() {
        Paciente p = paciente();
        when(pacienteRepository.findById(1L)).thenReturn(Optional.of(p));
        when(anamneseRepository.existsByPacienteId(1L)).thenReturn(false);
        when(anamneseRepository.saveAndFlush(any(Anamnese.class))).thenReturn(anamnese(p));

        AnamneseResponseDTO response = service.criar(requestDTO());

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.pacienteId()).isEqualTo(1L);
        assertThat(response.nomePaciente()).isEqualTo("Maria Souza");
        assertThat(response.queixaPrincipal()).isEqualTo("Dor lombar");
        assertThat(response.objetivos()).isEqualTo("Melhorar postura e reduzir dores");
        verify(anamneseRepository).saveAndFlush(any(Anamnese.class));
    }

    @Test
    void criar_comPacienteInexistente_deveLancarResourceNotFoundException() {
        when(pacienteRepository.findById(99L)).thenReturn(Optional.empty());

        var dto = new AnamneseRequestDTO(
                99L,
                "Dor",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "Objetivo",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);

        assertThatThrownBy(() -> service.criar(dto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void criar_comPacienteInativo_deveLancarBusinessException() {
        when(pacienteRepository.findById(1L)).thenReturn(Optional.of(pacienteInativo()));

        assertThatThrownBy(() -> service.criar(requestDTO()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Paciente inativo não aceita novos registros clínicos");

        verify(anamneseRepository, never()).saveAndFlush(any());
    }

    @Test
    void criar_quandoPacienteJaPossuiAnamnese_deveLancarConflictException() {
        when(pacienteRepository.findById(1L)).thenReturn(Optional.of(paciente()));
        when(anamneseRepository.existsByPacienteId(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.criar(requestDTO()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("1");
    }

    @Test
    void criar_quandoConstraintUnicaFalha_deveLancarConflictException() {
        when(pacienteRepository.findById(1L)).thenReturn(Optional.of(paciente()));
        when(anamneseRepository.existsByPacienteId(1L)).thenReturn(false);
        when(anamneseRepository.saveAndFlush(any(Anamnese.class)))
                .thenThrow(new DataIntegrityViolationException("violação unique"));

        assertThatThrownBy(() -> service.criar(requestDTO()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("1");
    }

    // -------------------------------------------------------------------------
    // buscarPorId
    // -------------------------------------------------------------------------

    @Test
    void buscarPorId_quandoExistente_deveRetornarResponseDTO() {
        Anamnese a = anamnese(paciente());
        when(anamneseRepository.findById(1L)).thenReturn(Optional.of(a));

        AnamneseResponseDTO response = service.buscarPorId(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.queixaPrincipal()).isEqualTo("Dor lombar");
    }

    @Test
    void buscarPorId_quandoNaoExistente_deveLancarResourceNotFoundException() {
        when(anamneseRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscarPorId(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void buscarPorId_quandoPacienteInativo_deveRetornarAnamnese() {
        Anamnese a = anamnese(pacienteInativo());
        when(anamneseRepository.findById(1L)).thenReturn(Optional.of(a));

        AnamneseResponseDTO response = service.buscarPorId(1L);

        assertThat(a.getPaciente().isAtivo()).isFalse();
        assertThat(response.pacienteId()).isEqualTo(1L);
    }

    // -------------------------------------------------------------------------
    // buscarPorPaciente
    // -------------------------------------------------------------------------

    @Test
    void buscarPorPaciente_quandoExistente_deveRetornarResponseDTO() {
        Paciente p = paciente();
        when(pacienteRepository.existsById(1L)).thenReturn(true);
        when(anamneseRepository.findByPacienteId(1L)).thenReturn(Optional.of(anamnese(p)));

        AnamneseResponseDTO response = service.buscarPorPaciente(1L);

        assertThat(response.pacienteId()).isEqualTo(1L);
        assertThat(response.nomePaciente()).isEqualTo("Maria Souza");
    }

    @Test
    void buscarPorPaciente_quandoPacienteInexistente_deveLancarResourceNotFoundException() {
        when(pacienteRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.buscarPorPaciente(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void buscarPorPaciente_quandoPacienteInativo_deveRetornarAnamnese() {
        when(pacienteRepository.existsById(1L)).thenReturn(true);
        when(anamneseRepository.findByPacienteId(1L)).thenReturn(Optional.of(anamnese(pacienteInativo())));

        AnamneseResponseDTO response = service.buscarPorPaciente(1L);

        assertThat(response.pacienteId()).isEqualTo(1L);
    }

    @Test
    void buscarPorPaciente_quandoAnamneseNaoEncontrada_deveLancarResourceNotFoundException() {
        when(pacienteRepository.existsById(1L)).thenReturn(true);
        when(anamneseRepository.findByPacienteId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscarPorPaciente(1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("1");
    }

    // -------------------------------------------------------------------------
    // atualizar
    // -------------------------------------------------------------------------

    @Test
    void atualizar_deveAtualizarApenasOsCamposInformados() {
        Anamnese a = anamnese(paciente());
        when(anamneseRepository.findById(1L)).thenReturn(Optional.of(a));
        when(anamneseRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        var dto = new AnamneseUpdateDTO(
                "Nova queixa",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
        AnamneseResponseDTO response = service.atualizar(1L, dto);

        assertThat(response.queixaPrincipal()).isEqualTo("Nova queixa");
        assertThat(response.historicoCirurgias()).isEqualTo("Nenhuma");
        assertThat(response.objetivos()).isEqualTo("Melhorar postura e reduzir dores");
    }

    @Test
    void atualizar_quandoNaoExistente_deveLancarResourceNotFoundException() {
        when(anamneseRepository.findById(99L)).thenReturn(Optional.empty());

        var dto = new AnamneseUpdateDTO(
                "Queixa", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null);

        assertThatThrownBy(() -> service.atualizar(99L, dto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void atualizar_comQueixaPrincipalEmBranco_deveLancarIllegalArgumentException() {
        when(anamneseRepository.findById(1L)).thenReturn(Optional.of(anamnese(paciente())));

        var dto = new AnamneseUpdateDTO(
                " ", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null);

        assertThatThrownBy(() -> service.atualizar(1L, dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("queixaPrincipal");
    }

    @Test
    void atualizar_comObjetivosEmBranco_deveLancarIllegalArgumentException() {
        when(anamneseRepository.findById(1L)).thenReturn(Optional.of(anamnese(paciente())));

        var dto = new AnamneseUpdateDTO(
                null, null, null, null, null, null, null, null, "", null, null, null, null, null, null, null, null,
                null, null);

        assertThatThrownBy(() -> service.atualizar(1L, dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("objetivos");
    }

    @Test
    void atualizar_comTodosOsCampos_deveAtualizarTudo() {
        Anamnese a = anamnese(paciente());
        when(anamneseRepository.findById(1L)).thenReturn(Optional.of(a));
        when(anamneseRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        var dto = new AnamneseUpdateDTO(
                "Dor cervical",
                "Diabetes",
                "Apendicectomia",
                "Fratura punho",
                "Metformina",
                "Dipirona",
                "Ativo",
                "Sem restrições",
                "Ganhar massa muscular",
                "Perfil atlético",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
        AnamneseResponseDTO response = service.atualizar(1L, dto);

        assertThat(response.queixaPrincipal()).isEqualTo("Dor cervical");
        assertThat(response.historicoDoencas()).isEqualTo("Diabetes");
        assertThat(response.historicoCirurgias()).isEqualTo("Apendicectomia");
        assertThat(response.historicoLesoes()).isEqualTo("Fratura punho");
        assertThat(response.medicamentosUso()).isEqualTo("Metformina");
        assertThat(response.alergias()).isEqualTo("Dipirona");
        assertThat(response.nivelAtividadeFisica()).isEqualTo("Ativo");
        assertThat(response.restricoesMedicas()).isEqualTo("Sem restrições");
        assertThat(response.objetivos()).isEqualTo("Ganhar massa muscular");
        assertThat(response.observacoes()).isEqualTo("Perfil atlético");
    }

    // -------------------------------------------------------------------------
    // Opções pré-definidas (#248)
    // -------------------------------------------------------------------------

    @Test
    void criar_comOpcoes_gravaDeduplicadoEMantemOsTextos() {
        when(pacienteRepository.findById(1L)).thenReturn(Optional.of(paciente()));
        when(anamneseRepository.existsByPacienteId(1L)).thenReturn(false);
        when(anamneseRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        AnamneseResponseDTO response = service.criar(new AnamneseRequestDTO(
                1L,
                "Dor",
                null,
                null,
                null,
                null,
                null,
                "Caminha",
                null,
                "Postura",
                null,
                List.of("LOMBAR", "LOMBAR", "CEFALEIA"),
                "Fim do dia",
                List.of("DIABETES", "DIABETES"),
                null,
                "SEDENTARIO",
                null,
                List.of("POSTURA"),
                "Trabalho sentado"));

        assertThat(response.regioesDor()).containsExactly("LOMBAR", "CEFALEIA");
        assertThat(response.regioesDorComplemento()).isEqualTo("Fim do dia");
        assertThat(response.condicoesSaude()).containsExactly("DIABETES");
        assertThat(response.nivelAtividadeFisicaOpcao()).isEqualTo("SEDENTARIO");
        assertThat(response.nivelAtividadeFisica()).isEqualTo("Caminha");
        assertThat(response.objetivosOpcoes()).containsExactly("POSTURA");
        assertThat(response.objetivosComplemento()).isEqualTo("Trabalho sentado");
    }

    @Test
    void criar_semOpcoes_devolveListasVaziasENuncaNulas() {
        when(pacienteRepository.findById(1L)).thenReturn(Optional.of(paciente()));
        when(anamneseRepository.existsByPacienteId(1L)).thenReturn(false);
        when(anamneseRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        AnamneseResponseDTO response = service.criar(requestDTO());

        assertThat(response.regioesDor()).isEmpty();
        assertThat(response.condicoesSaude()).isEmpty();
        assertThat(response.objetivosOpcoes()).isEmpty();
        assertThat(response.nivelAtividadeFisicaOpcao()).isNull();
    }

    @Test
    void criar_comOpcaoInexistente_deveLancarIllegalArgumentESemGravar() {
        when(pacienteRepository.findById(1L)).thenReturn(Optional.of(paciente()));
        when(anamneseRepository.existsByPacienteId(1L)).thenReturn(false);

        var dto = new AnamneseRequestDTO(
                1L, "Dor", null, null, null, null, null, null, null, "Postura", null, null, null, null, null, "ATLETA",
                null, null, null);

        assertThatThrownBy(() -> service.criar(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Opção inválida em nivelAtividadeFisicaOpcao: 'ATLETA'");
        verify(anamneseRepository, never()).saveAndFlush(any());
    }

    @Test
    void atualizar_opcoesAusentesMantem_listaVaziaESelecaoEmBrancoLimpam() {
        Anamnese a = anamnese(paciente());
        a.setRegioesDor(List.of("LOMBAR"));
        a.setCondicoesSaude(List.of("ARTROSE"));
        a.setNivelAtividadeFisicaOpcao("ATIVO");
        when(anamneseRepository.findById(1L)).thenReturn(Optional.of(a));
        when(anamneseRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        AnamneseResponseDTO response = service.atualizar(
                1L,
                new AnamneseUpdateDTO(
                        null, null, null, null, null, null, null, null, null, null, List.of(), null, null, null, "",
                        null, null, null, null));

        assertThat(response.regioesDor()).isEmpty();
        assertThat(response.condicoesSaude()).containsExactly("ARTROSE");
        assertThat(response.nivelAtividadeFisicaOpcao()).isNull();
        ArgumentCaptor<Anamnese> captor = ArgumentCaptor.forClass(Anamnese.class);
        verify(anamneseRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getQueixaPrincipal()).isEqualTo("Dor lombar");
    }
}
