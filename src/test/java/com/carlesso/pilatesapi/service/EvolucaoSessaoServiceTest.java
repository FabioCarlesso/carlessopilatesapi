package com.carlesso.pilatesapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.carlesso.pilatesapi.dto.EvolucaoSessaoRequestDTO;
import com.carlesso.pilatesapi.dto.EvolucaoSessaoResponseDTO;
import com.carlesso.pilatesapi.dto.EvolucaoSessaoUpdateDTO;
import com.carlesso.pilatesapi.entity.EvolucaoSessao;
import com.carlesso.pilatesapi.entity.Paciente;
import com.carlesso.pilatesapi.entity.Profissional;
import com.carlesso.pilatesapi.entity.SessaoPilates;
import com.carlesso.pilatesapi.entity.enums.StatusSessao;
import com.carlesso.pilatesapi.entity.enums.TipoSessao;
import com.carlesso.pilatesapi.exception.BusinessException;
import com.carlesso.pilatesapi.exception.ConflictException;
import com.carlesso.pilatesapi.exception.ResourceNotFoundException;
import com.carlesso.pilatesapi.repository.EvolucaoSessaoRepository;
import com.carlesso.pilatesapi.repository.PacienteRepository;
import com.carlesso.pilatesapi.repository.SessaoPilatesRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EvolucaoSessaoServiceTest {

    @Mock
    private EvolucaoSessaoRepository evolucaoRepository;

    @Mock
    private SessaoPilatesRepository sessaoRepository;

    @Mock
    private PacienteRepository pacienteRepository;

    @InjectMocks
    private EvolucaoSessaoService service;

    private Paciente paciente() {
        Paciente p = new Paciente();
        p.setNome("Ana Oliveira");
        p.setEmail("ana@email.com");
        p.setCpf("12345678900");
        setId(p, Paciente.class, 1L);
        return p;
    }

    private Profissional profissional() {
        Profissional p = new Profissional();
        p.setId(7L);
        p.setNome("Paula Mendes");
        p.setNumeroRegistro("350544-F");
        return p;
    }

    private SessaoPilates sessao(Paciente paciente) {
        return sessao(paciente, profissional());
    }

    private SessaoPilates sessao(Paciente paciente, Profissional profissional) {
        SessaoPilates s = new SessaoPilates();
        s.setPaciente(paciente);
        s.setProfissional(profissional);
        s.setTipo(TipoSessao.PILATES);
        s.setStatus(StatusSessao.REALIZADA);
        s.setData(LocalDate.of(2026, 5, 10));
        s.setDataCriacao(LocalDateTime.of(2026, 5, 4, 10, 0));
        setId(s, SessaoPilates.class, 1L);
        return s;
    }

    private EvolucaoSessao evolucao(SessaoPilates sessao) {
        EvolucaoSessao e = new EvolucaoSessao();
        e.setSessao(sessao);
        if (sessao.getProfissional() != null) {
            e.setProfissionalId(sessao.getProfissional().getId());
            e.setProfissionalNome(sessao.getProfissional().getNome());
            e.setProfissionalNumeroRegistro(sessao.getProfissional().getNumeroRegistro());
        }
        e.setDataHoraRegistro(LocalDateTime.of(2026, 5, 10, 10, 30));
        e.setExerciciosRealizados("Reformer, Cadillac");
        e.setEquipamentosUtilizados("Reformer");
        e.setDorAntes(5);
        e.setDorDepois(2);
        e.setRespostaPaciente("Boa evolução");
        e.setDataCriacao(LocalDateTime.of(2026, 5, 10, 10, 30));
        setId(e, EvolucaoSessao.class, 1L);
        return e;
    }

    private EvolucaoSessaoRequestDTO requestDTO() {
        return new EvolucaoSessaoRequestDTO(
                1L,
                LocalDateTime.of(2026, 5, 10, 10, 30),
                "Reformer, Cadillac",
                "Reformer",
                null,
                5,
                2,
                "Boa evolução",
                null,
                "Manter exercícios",
                null);
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
    void criar_comPacienteInativo_deveLancarBusinessException() {
        Paciente p = paciente();
        p.setAtivo(false);
        when(sessaoRepository.findByIdComPaciente(1L)).thenReturn(Optional.of(sessao(p)));

        assertThatThrownBy(() -> service.criar(requestDTO()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Paciente inativo não aceita novos registros clínicos");

        verify(evolucaoRepository, never()).save(any());
    }

    @Test
    void criar_comSessaoValida_deveRetornarResponseDTO() {
        Paciente p = paciente();
        SessaoPilates s = sessao(p);
        when(sessaoRepository.findByIdComPaciente(1L)).thenReturn(Optional.of(s));
        when(evolucaoRepository.existsBySessaoId(1L)).thenReturn(false);
        when(evolucaoRepository.save(any(EvolucaoSessao.class))).thenReturn(evolucao(s));

        EvolucaoSessaoResponseDTO response = service.criar(requestDTO());

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.sessaoId()).isEqualTo(1L);
        assertThat(response.dorAntes()).isEqualTo(5);
        assertThat(response.dorDepois()).isEqualTo(2);
        assertThat(response.respostaPaciente()).isEqualTo("Boa evolução");
        verify(evolucaoRepository).save(any(EvolucaoSessao.class));
    }

    @Test
    void criar_deveCongelarOSnapshotDoProfissionalDaSessao() {
        SessaoPilates s = sessao(paciente());
        when(sessaoRepository.findByIdComPaciente(1L)).thenReturn(Optional.of(s));
        when(evolucaoRepository.existsBySessaoId(1L)).thenReturn(false);
        when(evolucaoRepository.save(any(EvolucaoSessao.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EvolucaoSessaoResponseDTO response = service.criar(requestDTO());

        assertThat(response.profissionalId()).isEqualTo(7L);
        assertThat(response.profissionalNome()).isEqualTo("Paula Mendes");
        assertThat(response.profissionalNumeroRegistro()).isEqualTo("350544-F");
    }

    @Test
    void criar_comSessaoSemProfissional_deveDeixarOSnapshotNulo() {
        SessaoPilates s = sessao(paciente(), null);
        when(sessaoRepository.findByIdComPaciente(1L)).thenReturn(Optional.of(s));
        when(evolucaoRepository.existsBySessaoId(1L)).thenReturn(false);
        when(evolucaoRepository.save(any(EvolucaoSessao.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EvolucaoSessaoResponseDTO response = service.criar(requestDTO());

        assertThat(response.profissionalId()).isNull();
        assertThat(response.profissionalNome()).isNull();
        assertThat(response.profissionalNumeroRegistro()).isNull();
    }

    @Test
    void criar_alterarCadastroDepois_naoDeveAlterarOSnapshotJaGravado() {
        Profissional prof = profissional();
        SessaoPilates s = sessao(paciente(), prof);
        var salva = new AtomicReference<EvolucaoSessao>();
        when(sessaoRepository.findByIdComPaciente(1L)).thenReturn(Optional.of(s));
        when(evolucaoRepository.existsBySessaoId(1L)).thenReturn(false);
        when(evolucaoRepository.save(any(EvolucaoSessao.class))).thenAnswer(invocation -> {
            salva.set(invocation.getArgument(0));
            return invocation.getArgument(0);
        });

        service.criar(requestDTO());

        prof.setNumeroRegistro("999999-X");
        prof.setNome("Paula Mendes Silva");

        assertThat(salva.get().getProfissionalNumeroRegistro()).isEqualTo("350544-F");
        assertThat(salva.get().getProfissionalNome()).isEqualTo("Paula Mendes");
    }

    @Test
    void criar_comSessaoInexistente_deveLancarResourceNotFoundException() {
        when(sessaoRepository.findByIdComPaciente(99L)).thenReturn(Optional.empty());

        var dto = new EvolucaoSessaoRequestDTO(
                99L, LocalDateTime.now(), null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.criar(dto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void criar_comSessaoJaComEvolucao_deveLancarConflictException() {
        Paciente p = paciente();
        SessaoPilates s = sessao(p);
        when(sessaoRepository.findByIdComPaciente(1L)).thenReturn(Optional.of(s));
        when(evolucaoRepository.existsBySessaoId(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.criar(requestDTO()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("já possui evolução");
    }

    @Test
    void buscarPorId_quandoExistente_deveRetornarResponseDTO() {
        SessaoPilates s = sessao(paciente());
        when(evolucaoRepository.findByIdComSessao(1L)).thenReturn(Optional.of(evolucao(s)));

        EvolucaoSessaoResponseDTO response = service.buscarPorId(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.exerciciosRealizados()).isEqualTo("Reformer, Cadillac");
        assertThat(response.profissionalId()).isEqualTo(7L);
        assertThat(response.profissionalNome()).isEqualTo("Paula Mendes");
        assertThat(response.profissionalNumeroRegistro()).isEqualTo("350544-F");
    }

    @Test
    void buscarPorId_quandoNaoExistente_deveLancarResourceNotFoundException() {
        when(evolucaoRepository.findByIdComSessao(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscarPorId(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void buscarPorSessao_quandoExistente_deveRetornarResponseDTO() {
        SessaoPilates s = sessao(paciente());
        when(sessaoRepository.findByIdComPaciente(1L)).thenReturn(Optional.of(s));
        when(evolucaoRepository.findBySessaoId(1L)).thenReturn(Optional.of(evolucao(s)));

        EvolucaoSessaoResponseDTO response = service.buscarPorSessao(1L);

        assertThat(response.sessaoId()).isEqualTo(1L);
        assertThat(response.dorAntes()).isEqualTo(5);
        assertThat(response.profissionalId()).isEqualTo(7L);
        assertThat(response.profissionalNome()).isEqualTo("Paula Mendes");
        assertThat(response.profissionalNumeroRegistro()).isEqualTo("350544-F");
    }

    @Test
    void buscarPorSessao_comSessaoInexistente_deveLancarResourceNotFoundException() {
        when(sessaoRepository.findByIdComPaciente(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscarPorSessao(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void buscarPorSessao_semEvolucao_deveLancarResourceNotFoundException() {
        when(sessaoRepository.findByIdComPaciente(1L)).thenReturn(Optional.of(sessao(paciente())));
        when(evolucaoRepository.findBySessaoId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscarPorSessao(1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Evolução não encontrada para a sessão");
    }

    @Test
    void listarPorPaciente_comEvolucoes_devePreservarAOrdemDoRepositorio() {
        Paciente p = paciente();
        SessaoPilates recente = sessao(p);
        // A antiga não tem profissional vinculado: garante que o snapshot é mapeado
        // item a item, e não copiado de um valor constante da lista.
        SessaoPilates antiga = sessao(p, null);
        antiga.setData(LocalDate.of(2026, 4, 20));
        setId(antiga, SessaoPilates.class, 2L);
        EvolucaoSessao evolucaoAntiga = evolucao(antiga);
        setId(evolucaoAntiga, EvolucaoSessao.class, 2L);
        when(pacienteRepository.existsById(1L)).thenReturn(true);
        when(evolucaoRepository.findByPacienteOrdenadas(1L)).thenReturn(List.of(evolucao(recente), evolucaoAntiga));

        List<EvolucaoSessaoResponseDTO> response = service.listarPorPaciente(1L);

        assertThat(response).hasSize(2);
        assertThat(response.getFirst().sessaoId()).isEqualTo(1L);
        assertThat(response.getFirst().dorAntes()).isEqualTo(5);
        assertThat(response.getFirst().exerciciosRealizados()).isEqualTo("Reformer, Cadillac");
        assertThat(response.getFirst().profissionalId()).isEqualTo(7L);
        assertThat(response.getFirst().profissionalNome()).isEqualTo("Paula Mendes");
        assertThat(response.getFirst().profissionalNumeroRegistro()).isEqualTo("350544-F");
        assertThat(response.get(1).sessaoId()).isEqualTo(2L);
        assertThat(response.get(1).profissionalId()).isNull();
        assertThat(response.get(1).profissionalNome()).isNull();
        assertThat(response.get(1).profissionalNumeroRegistro()).isNull();
    }

    @Test
    void listarPorPaciente_semEvolucoes_deveRetornarListaVazia() {
        when(pacienteRepository.existsById(1L)).thenReturn(true);
        when(evolucaoRepository.findByPacienteOrdenadas(1L)).thenReturn(List.of());

        assertThat(service.listarPorPaciente(1L)).isEmpty();
    }

    @Test
    void listarPorPaciente_comPacienteInexistente_deveLancarResourceNotFoundException() {
        when(pacienteRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.listarPorPaciente(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");

        verify(evolucaoRepository, never()).findByPacienteOrdenadas(any());
    }

    @Test
    void atualizar_deveAtualizarApenasOsCamposInformados() {
        SessaoPilates s = sessao(paciente());
        EvolucaoSessao e = evolucao(s);
        when(evolucaoRepository.findByIdComSessao(1L)).thenReturn(Optional.of(e));
        when(evolucaoRepository.save(e)).thenReturn(e);

        var dto = new EvolucaoSessaoUpdateDTO(
                null, "Reformer, Chair", null, "Mola 3", 3, 1, "Melhorou muito", null, null, null);

        EvolucaoSessaoResponseDTO response = service.atualizar(1L, dto);

        assertThat(response.exerciciosRealizados()).isEqualTo("Reformer, Chair");
        assertThat(response.cargasMolas()).isEqualTo("Mola 3");
        assertThat(response.dorAntes()).isEqualTo(3);
        assertThat(response.dorDepois()).isEqualTo(1);
        assertThat(response.respostaPaciente()).isEqualTo("Melhorou muito");
        assertThat(response.dataAtualizacao()).isNotNull();
    }

    @Test
    void atualizar_naoDeveAlterarOSnapshotDoProfissional() {
        SessaoPilates s = sessao(paciente());
        EvolucaoSessao e = evolucao(s);
        when(evolucaoRepository.findByIdComSessao(1L)).thenReturn(Optional.of(e));
        when(evolucaoRepository.save(e)).thenReturn(e);

        // Depois da criação o cadastro do profissional mudou; a evolução não acompanha.
        s.getProfissional().setNome("Outro Nome");
        s.getProfissional().setNumeroRegistro("999999-X");

        var dto = new EvolucaoSessaoUpdateDTO(null, "Reformer, Chair", null, null, null, null, null, null, null, null);

        EvolucaoSessaoResponseDTO response = service.atualizar(1L, dto);

        assertThat(response.profissionalId()).isEqualTo(7L);
        assertThat(response.profissionalNome()).isEqualTo("Paula Mendes");
        assertThat(response.profissionalNumeroRegistro()).isEqualTo("350544-F");
    }

    @Test
    void atualizar_comEvolucaoInexistente_deveLancarResourceNotFoundException() {
        when(evolucaoRepository.findByIdComSessao(99L)).thenReturn(Optional.empty());

        var dto = new EvolucaoSessaoUpdateDTO(null, null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.atualizar(99L, dto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }
}
