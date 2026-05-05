package com.carlesso.pilatesapi.service;

import com.carlesso.pilatesapi.dto.EvolucaoSessaoRequestDTO;
import com.carlesso.pilatesapi.dto.EvolucaoSessaoResponseDTO;
import com.carlesso.pilatesapi.dto.EvolucaoSessaoUpdateDTO;
import com.carlesso.pilatesapi.entity.EvolucaoSessao;
import com.carlesso.pilatesapi.entity.Paciente;
import com.carlesso.pilatesapi.entity.SessaoPilates;
import com.carlesso.pilatesapi.entity.enums.StatusSessao;
import com.carlesso.pilatesapi.entity.enums.TipoSessao;
import com.carlesso.pilatesapi.exception.ConflictException;
import com.carlesso.pilatesapi.exception.ResourceNotFoundException;
import com.carlesso.pilatesapi.repository.EvolucaoSessaoRepository;
import com.carlesso.pilatesapi.repository.SessaoPilatesRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvolucaoSessaoServiceTest {

    @Mock
    private EvolucaoSessaoRepository evolucaoRepository;

    @Mock
    private SessaoPilatesRepository sessaoRepository;

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

    private SessaoPilates sessao(Paciente paciente) {
        SessaoPilates s = new SessaoPilates();
        s.setPaciente(paciente);
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
                null
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
    void criar_comSessaoInexistente_deveLancarResourceNotFoundException() {
        when(sessaoRepository.findByIdComPaciente(99L)).thenReturn(Optional.empty());

        var dto = new EvolucaoSessaoRequestDTO(99L, LocalDateTime.now(), null, null, null, null, null, null, null, null, null);

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
    void atualizar_deveAtualizarApenasOsCamposInformados() {
        SessaoPilates s = sessao(paciente());
        EvolucaoSessao e = evolucao(s);
        when(evolucaoRepository.findByIdComSessao(1L)).thenReturn(Optional.of(e));
        when(evolucaoRepository.save(e)).thenReturn(e);

        var dto = new EvolucaoSessaoUpdateDTO(
                null,
                "Reformer, Chair",
                null,
                "Mola 3",
                3,
                1,
                "Melhorou muito",
                null,
                null,
                null
        );

        EvolucaoSessaoResponseDTO response = service.atualizar(1L, dto);

        assertThat(response.exerciciosRealizados()).isEqualTo("Reformer, Chair");
        assertThat(response.cargasMolas()).isEqualTo("Mola 3");
        assertThat(response.dorAntes()).isEqualTo(3);
        assertThat(response.dorDepois()).isEqualTo(1);
        assertThat(response.respostaPaciente()).isEqualTo("Melhorou muito");
        assertThat(response.dataAtualizacao()).isNotNull();
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
