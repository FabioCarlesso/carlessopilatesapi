package com.carlesso.pilatesapi.service;

import com.carlesso.pilatesapi.dto.SessaoPilatesRequestDTO;
import com.carlesso.pilatesapi.dto.SessaoPilatesResponseDTO;
import com.carlesso.pilatesapi.dto.SessaoPilatesUpdateDTO;
import com.carlesso.pilatesapi.entity.Paciente;
import com.carlesso.pilatesapi.entity.PlanoTratamento;
import com.carlesso.pilatesapi.entity.Profissional;
import com.carlesso.pilatesapi.entity.SessaoPilates;
import com.carlesso.pilatesapi.entity.enums.StatusSessao;
import com.carlesso.pilatesapi.entity.enums.TipoSessao;
import com.carlesso.pilatesapi.exception.BusinessException;
import com.carlesso.pilatesapi.exception.ResourceNotFoundException;
import com.carlesso.pilatesapi.repository.EvolucaoSessaoRepository;
import com.carlesso.pilatesapi.repository.PacienteRepository;
import com.carlesso.pilatesapi.repository.PlanoTratamentoRepository;
import com.carlesso.pilatesapi.repository.ProfissionalRepository;
import com.carlesso.pilatesapi.repository.SessaoPilatesRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessaoPilatesServiceTest {

    @Mock
    private SessaoPilatesRepository sessaoRepository;

    @Mock
    private PacienteRepository pacienteRepository;

    @Mock
    private ProfissionalRepository profissionalRepository;

    @Mock
    private PlanoTratamentoRepository planoTratamentoRepository;

    @Mock
    private EvolucaoSessaoRepository evolucaoSessaoRepository;

    @InjectMocks
    private SessaoPilatesService service;

    private Paciente paciente() {
        Paciente p = new Paciente();
        p.setNome("Ana Oliveira");
        p.setEmail("ana@email.com");
        p.setCpf("12345678900");
        setId(p, Paciente.class, 1L);
        return p;
    }

    private SessaoPilates sessao(Paciente paciente) {
        SessaoPilates s = new SessaoPilates();
        s.setPaciente(paciente);
        s.setTipo(TipoSessao.PILATES);
        s.setStatus(StatusSessao.AGENDADA);
        s.setData(LocalDate.of(2026, 5, 10));
        s.setHorario(LocalTime.of(9, 0));
        s.setLocal("Sala 1");
        s.setDuracaoMinutos(50);
        s.setObservacoes("Observação teste");
        s.setDataCriacao(LocalDateTime.of(2026, 5, 4, 10, 0));
        setId(s, SessaoPilates.class, 1L);
        return s;
    }

    private PlanoTratamento planoTratamento(Paciente paciente) {
        PlanoTratamento plano = new PlanoTratamento();
        plano.setPaciente(paciente);
        plano.setDataInicio(LocalDate.of(2026, 5, 1));
        plano.setObjetivosTratamento("Fortalecimento");
        setId(plano, PlanoTratamento.class, 10L);
        return plano;
    }

    private SessaoPilatesRequestDTO requestDTO() {
        return new SessaoPilatesRequestDTO(
                1L, null, null,
                TipoSessao.PILATES,
                LocalDate.of(2026, 5, 10),
                LocalTime.of(9, 0),
                "Sala 1",
                50,
                "Observação teste"
        );
    }

    private <T> void setId(T obj, Class<T> clazz, Long id) {
        try {
            var field = clazz.getDeclaredField("id");
            field.setAccessible(true);
            field.set(obj, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void criar_comPacienteValido_deveRetornarResponseDTO() {
        Paciente p = paciente();
        when(pacienteRepository.findByIdAndAtivoTrue(1L)).thenReturn(Optional.of(p));
        when(sessaoRepository.save(any(SessaoPilates.class))).thenReturn(sessao(p));

        SessaoPilatesResponseDTO response = service.criar(requestDTO());

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.pacienteId()).isEqualTo(1L);
        assertThat(response.nomePaciente()).isEqualTo("Ana Oliveira");
        assertThat(response.tipo()).isEqualTo(TipoSessao.PILATES);
        assertThat(response.status()).isEqualTo(StatusSessao.AGENDADA);
        assertThat(response.duracaoMinutos()).isEqualTo(50);
        verify(sessaoRepository).save(any(SessaoPilates.class));
    }

    @Test
    void criar_comPacienteInexistente_deveLancarResourceNotFoundException() {
        when(pacienteRepository.findByIdAndAtivoTrue(99L)).thenReturn(Optional.empty());

        var dto = new SessaoPilatesRequestDTO(
                99L, null, null, TipoSessao.PILATES, LocalDate.of(2026, 5, 10),
                null, null, null, null
        );

        assertThatThrownBy(() -> service.criar(dto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void criar_comProfissionalInexistente_deveLancarResourceNotFoundException() {
        Paciente p = paciente();
        when(pacienteRepository.findByIdAndAtivoTrue(1L)).thenReturn(Optional.of(p));
        when(profissionalRepository.findById(99L)).thenReturn(Optional.empty());

        var dto = new SessaoPilatesRequestDTO(
                1L, 99L, null, TipoSessao.PILATES, LocalDate.of(2026, 5, 10),
                null, null, null, null
        );

        assertThatThrownBy(() -> service.criar(dto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void criar_comProfissionalInativo_deveLancarBusinessException() {
        Paciente p = paciente();
        Profissional prof = new Profissional();
        prof.setAtivo(false);
        setId(prof, Profissional.class, 2L);

        when(pacienteRepository.findByIdAndAtivoTrue(1L)).thenReturn(Optional.of(p));
        when(profissionalRepository.findById(2L)).thenReturn(Optional.of(prof));

        var dto = new SessaoPilatesRequestDTO(
                1L, 2L, null, TipoSessao.PILATES, LocalDate.of(2026, 5, 10),
                null, null, null, null
        );

        assertThatThrownBy(() -> service.criar(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Profissional inativo");
    }

    @Test
    void criar_comPlanoTratamentoInexistente_deveLancarResourceNotFoundException() {
        Paciente p = paciente();
        when(pacienteRepository.findByIdAndAtivoTrue(1L)).thenReturn(Optional.of(p));
        when(planoTratamentoRepository.findAtivoById(99L)).thenReturn(Optional.empty());

        var dto = new SessaoPilatesRequestDTO(
                1L, null, 99L, TipoSessao.FISIOTERAPIA, LocalDate.of(2026, 5, 10),
                null, null, null, null
        );

        assertThatThrownBy(() -> service.criar(dto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void criar_comPlanoTratamentoDeOutroPaciente_deveLancarBusinessException() {
        Paciente pacienteSessao = paciente();
        Paciente outroPaciente = paciente();
        outroPaciente.setNome("Bruna Souza");
        setId(outroPaciente, Paciente.class, 2L);

        PlanoTratamento plano = planoTratamento(outroPaciente);

        when(pacienteRepository.findByIdAndAtivoTrue(1L)).thenReturn(Optional.of(pacienteSessao));
        when(planoTratamentoRepository.findAtivoById(10L)).thenReturn(Optional.of(plano));

        var dto = new SessaoPilatesRequestDTO(
                1L, null, 10L, TipoSessao.FISIOTERAPIA, LocalDate.of(2026, 5, 10),
                null, null, null, null
        );

        assertThatThrownBy(() -> service.criar(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("não pertence ao paciente");
    }

    @Test
    void criar_comProfissionalValido_deveAssociarProfissional() {
        Paciente p = paciente();
        Profissional prof = new Profissional();
        prof.setNome("Dr. Carlos");
        setId(prof, Profissional.class, 2L);

        SessaoPilates s = sessao(p);
        s.setProfissional(prof);

        when(pacienteRepository.findByIdAndAtivoTrue(1L)).thenReturn(Optional.of(p));
        when(profissionalRepository.findById(2L)).thenReturn(Optional.of(prof));
        when(sessaoRepository.save(any(SessaoPilates.class))).thenReturn(s);

        var dto = new SessaoPilatesRequestDTO(
                1L, 2L, null, TipoSessao.PILATES, LocalDate.of(2026, 5, 10),
                null, null, null, null
        );

        SessaoPilatesResponseDTO response = service.criar(dto);

        assertThat(response.profissionalId()).isEqualTo(2L);
        assertThat(response.nomeProfissional()).isEqualTo("Dr. Carlos");
    }

    @Test
    void buscarPorId_quandoExistente_deveRetornarResponseDTO() {
        when(sessaoRepository.findByIdComPaciente(1L)).thenReturn(Optional.of(sessao(paciente())));

        SessaoPilatesResponseDTO response = service.buscarPorId(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.tipo()).isEqualTo(TipoSessao.PILATES);
        assertThat(response.local()).isEqualTo("Sala 1");
    }

    @Test
    void buscarPorId_quandoNaoExistente_deveLancarResourceNotFoundException() {
        when(sessaoRepository.findByIdComPaciente(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscarPorId(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void listarPorPaciente_deveRetornarSessoesDoPaciente() {
        Paciente p = paciente();
        when(pacienteRepository.existsByIdAndAtivoTrue(1L)).thenReturn(true);
        when(sessaoRepository.findByPacienteOrdenadas(1L)).thenReturn(List.of(sessao(p)));

        List<SessaoPilatesResponseDTO> response = service.listarPorPaciente(1L);

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().pacienteId()).isEqualTo(1L);
        assertThat(response.getFirst().data()).isEqualTo(LocalDate.of(2026, 5, 10));
    }

    @Test
    void listarPorPaciente_comPacienteAtiveSemSessoes_deveRetornarListaVazia() {
        when(pacienteRepository.existsByIdAndAtivoTrue(1L)).thenReturn(true);
        when(sessaoRepository.findByPacienteOrdenadas(1L)).thenReturn(List.of());

        List<SessaoPilatesResponseDTO> response = service.listarPorPaciente(1L);

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
        SessaoPilates s = sessao(paciente());
        when(sessaoRepository.findByIdComPaciente(1L)).thenReturn(Optional.of(s));
        when(sessaoRepository.save(s)).thenReturn(s);

        var dto = new SessaoPilatesUpdateDTO(
                LocalDate.of(2026, 5, 15),
                LocalTime.of(10, 0),
                null,
                60,
                StatusSessao.REALIZADA,
                null
        );

        SessaoPilatesResponseDTO response = service.atualizar(1L, dto);

        assertThat(response.data()).isEqualTo(LocalDate.of(2026, 5, 15));
        assertThat(response.horario()).isEqualTo(LocalTime.of(10, 0));
        assertThat(response.local()).isEqualTo("Sala 1");
        assertThat(response.duracaoMinutos()).isEqualTo(60);
        assertThat(response.status()).isEqualTo(StatusSessao.REALIZADA);
        assertThat(response.dataAtualizacao()).isNotNull();
    }

    @Test
    void atualizar_comSessaoInexistente_deveLancarResourceNotFoundException() {
        when(sessaoRepository.findByIdComPaciente(99L)).thenReturn(Optional.empty());

        var dto = new SessaoPilatesUpdateDTO(null, null, null, null, null, null);

        assertThatThrownBy(() -> service.atualizar(99L, dto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void realizar_comSessaoAgendada_deveTransicionarParaRealizada() {
        SessaoPilates s = sessao(paciente());
        when(sessaoRepository.findByIdComPaciente(1L)).thenReturn(Optional.of(s));
        when(sessaoRepository.save(s)).thenReturn(s);

        SessaoPilatesResponseDTO response = service.realizar(1L);

        assertThat(response.status()).isEqualTo(StatusSessao.REALIZADA);
        assertThat(response.dataAtualizacao()).isNotNull();
        verify(sessaoRepository).save(s);
    }

    @Test
    void realizar_comSessaoRealizada_deveLancarBusinessException() {
        SessaoPilates s = sessao(paciente());
        s.setStatus(StatusSessao.REALIZADA);
        when(sessaoRepository.findByIdComPaciente(1L)).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.realizar(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Transição inválida");
    }

    @Test
    void realizar_comSessaoCancelada_deveLancarBusinessException() {
        SessaoPilates s = sessao(paciente());
        s.setStatus(StatusSessao.CANCELADA);
        when(sessaoRepository.findByIdComPaciente(1L)).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.realizar(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Transição inválida");
    }

    @Test
    void realizar_comSessaoInexistente_deveLancarResourceNotFoundException() {
        when(sessaoRepository.findByIdComPaciente(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.realizar(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void cancelar_comSessaoAgendada_deveTransicionarParaCancelada() {
        SessaoPilates s = sessao(paciente());
        when(sessaoRepository.findByIdComPaciente(1L)).thenReturn(Optional.of(s));
        when(sessaoRepository.save(s)).thenReturn(s);

        SessaoPilatesResponseDTO response = service.cancelar(1L);

        assertThat(response.status()).isEqualTo(StatusSessao.CANCELADA);
        assertThat(response.dataAtualizacao()).isNotNull();
    }

    @Test
    void cancelar_comSessaoRealizada_deveLancarBusinessException() {
        SessaoPilates s = sessao(paciente());
        s.setStatus(StatusSessao.REALIZADA);
        when(sessaoRepository.findByIdComPaciente(1L)).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.cancelar(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Transição inválida");
    }

    @Test
    void cancelar_comSessaoInexistente_deveLancarResourceNotFoundException() {
        when(sessaoRepository.findByIdComPaciente(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancelar(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void excluir_comSessaoExistente_deveRemoverSessao() {
        SessaoPilates s = sessao(paciente());
        when(sessaoRepository.findByIdComPaciente(1L)).thenReturn(Optional.of(s));

        service.excluir(1L);

        verify(evolucaoSessaoRepository).deleteBySessaoId(1L);
        verify(sessaoRepository).delete(s);
    }

    @Test
    void excluir_comSessaoInexistente_deveLancarResourceNotFoundException() {
        when(sessaoRepository.findByIdComPaciente(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.excluir(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }
}
