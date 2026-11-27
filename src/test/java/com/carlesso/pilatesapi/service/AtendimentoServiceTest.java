package com.carlesso.pilatesapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.carlesso.pilatesapi.dto.AtendimentoEvolucaoDTO;
import com.carlesso.pilatesapi.dto.AtendimentoRequestDTO;
import com.carlesso.pilatesapi.dto.EvolucaoSessaoRequestDTO;
import com.carlesso.pilatesapi.entity.Aula;
import com.carlesso.pilatesapi.entity.Paciente;
import com.carlesso.pilatesapi.entity.Profissional;
import com.carlesso.pilatesapi.entity.SessaoPilates;
import com.carlesso.pilatesapi.entity.enums.StatusSessao;
import com.carlesso.pilatesapi.entity.enums.TipoSessao;
import com.carlesso.pilatesapi.exception.BusinessException;
import com.carlesso.pilatesapi.exception.ConflictException;
import com.carlesso.pilatesapi.exception.ResourceNotFoundException;
import com.carlesso.pilatesapi.repository.EvolucaoSessaoRepository;
import com.carlesso.pilatesapi.repository.SessaoPilatesRepository;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class AtendimentoServiceTest {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 11, 18, 21, 30);

    @Mock
    AulaService aulaService;

    @Mock
    SessaoPilatesRepository sessaoRepository;

    @Mock
    EvolucaoSessaoService evolucaoService;

    @Mock
    EvolucaoSessaoRepository evolucaoRepository;

    AtendimentoService service;

    private Aula aula;
    private Profissional camila;

    @BeforeEach
    void setUp() {
        // 21h30 em São Paulo já é o dia seguinte em UTC: o registro precisa sair no fuso do estúdio.
        service = new AtendimentoService(
                aulaService,
                sessaoRepository,
                evolucaoService,
                evolucaoRepository,
                Clock.fixed(AGORA.atZone(FUSO).toInstant(), FUSO));

        Paciente paciente = new Paciente();
        paciente.setNome("Ana");
        camila = new Profissional();
        camila.setNome("Camila");
        aula = new Aula();
        aula.setPaciente(paciente);
        aula.setProfissional(camila);
        aula.setData(LocalDate.of(2026, 11, 18));
        aula.setHorario(LocalTime.of(8, 0));
    }

    @Test
    void buscar_eTransacionalReadOnly() throws Exception {
        Method buscar = AtendimentoService.class.getMethod("buscar", Long.class);
        assertThat(buscar.getAnnotation(Transactional.class).readOnly()).isTrue();
    }

    @Test
    void registrar_aulaNova_criaSessaoRealizadaComOsDadosDaAulaERegistraAEvolucaoNoFusoDoEstudio() {
        when(aulaService.encontrar(1L)).thenReturn(aula);
        when(sessaoRepository.findByAulaId(1L)).thenReturn(Optional.empty());
        when(sessaoRepository.saveAndFlush(any(SessaoPilates.class))).thenAnswer(inv -> inv.getArgument(0));

        service.registrar(
                1L,
                new AtendimentoRequestDTO(
                        null, null, new AtendimentoEvolucaoDTO(null, null, null, null, 3, 1, null, null, null, null)));

        ArgumentCaptor<SessaoPilates> sessao = ArgumentCaptor.forClass(SessaoPilates.class);
        verify(sessaoRepository).saveAndFlush(sessao.capture());
        assertThat(sessao.getValue().getAula()).isSameAs(aula);
        assertThat(sessao.getValue().getProfissional()).isSameAs(camila);
        assertThat(sessao.getValue().getTipo()).isEqualTo(TipoSessao.PILATES);
        assertThat(sessao.getValue().getStatus()).isEqualTo(StatusSessao.REALIZADA);
        assertThat(sessao.getValue().getData()).isEqualTo(aula.getData());
        assertThat(sessao.getValue().getHorario()).isEqualTo(aula.getHorario());
        verify(aulaService).marcarRealizada(aula, null);

        ArgumentCaptor<EvolucaoSessaoRequestDTO> evolucao = ArgumentCaptor.forClass(EvolucaoSessaoRequestDTO.class);
        verify(evolucaoService).criar(evolucao.capture());
        assertThat(evolucao.getValue().dataHoraRegistro()).isEqualTo(AGORA);
        assertThat(evolucao.getValue().dorAntes()).isEqualTo(3);
    }

    @Test
    void registrar_aulaDeFeriado_recusaCom422AntesDeTocarNaSessao() {
        aula.marcarFeriado(null);
        when(aulaService.encontrar(1L)).thenReturn(aula);

        assertThatThrownBy(() -> service.registrar(1L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Aula de feriado não aceita atendimento");
        verifyNoInteractions(sessaoRepository, evolucaoService);
    }

    @Test
    void registrar_aulaInexistente_propagaO404() {
        when(aulaService.encontrar(9L)).thenThrow(new ResourceNotFoundException("Aula não encontrada: 9"));

        assertThatThrownBy(() -> service.registrar(9L, null)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(sessaoRepository);
    }

    @Test
    void registrar_sessaoJaComEvolucao_eNovaEvolucao_recusaCom409SemRealizar() {
        SessaoPilates existente = sessao(StatusSessao.REALIZADA);
        when(aulaService.encontrar(1L)).thenReturn(aula);
        when(sessaoRepository.findByAulaId(1L)).thenReturn(Optional.of(existente));
        when(evolucaoRepository.existsBySessaoId(existente.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.registrar(
                        1L,
                        new AtendimentoRequestDTO(
                                null,
                                null,
                                new AtendimentoEvolucaoDTO(
                                        null, null, null, null, null, null, null, null, null, null))))
                .isInstanceOf(ConflictException.class);
        verify(aulaService, never()).marcarRealizada(any(), any());
        verify(evolucaoService, never()).criar(any());
    }

    @Test
    void registrar_sessaoVinculadaCancelada_recusaCom409() {
        when(aulaService.encontrar(1L)).thenReturn(aula);
        when(sessaoRepository.findByAulaId(1L)).thenReturn(Optional.of(sessao(StatusSessao.CANCELADA)));

        assertThatThrownBy(() -> service.registrar(1L, null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("cancelada");
    }

    @Test
    void registrar_sessaoVinculadaAgendada_eReaproveitadaComoRealizadaSemCriarOutra() {
        SessaoPilates existente = sessao(StatusSessao.AGENDADA);
        aula.setRealizada(true);
        when(aulaService.encontrar(1L)).thenReturn(aula);
        when(sessaoRepository.findByAulaId(1L)).thenReturn(Optional.of(existente));

        var atendimento = service.registrar(1L, null);

        assertThat(existente.getStatus()).isEqualTo(StatusSessao.REALIZADA);
        assertThat(atendimento.evolucao()).isNull();
        verify(sessaoRepository, never()).saveAndFlush(any());
        verify(aulaService, never()).marcarRealizada(any(), any());
    }

    @Test
    void registrar_atendimentoSimultaneo_violacaoDaUnique_viraConflito() {
        when(aulaService.encontrar(1L)).thenReturn(aula);
        when(sessaoRepository.findByAulaId(1L)).thenReturn(Optional.empty());
        when(sessaoRepository.saveAndFlush(any(SessaoPilates.class)))
                .thenThrow(new DataIntegrityViolationException("uk_sessoes_pilates_aula"));

        assertThatThrownBy(() -> service.registrar(1L, null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("simultânea");
    }

    @Test
    void buscar_aulaSemSessao_responde404() {
        when(aulaService.encontrar(1L)).thenReturn(aula);
        when(sessaoRepository.findByAulaId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscar(1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Aula sem atendimento registrado: 1");
    }

    private SessaoPilates sessao(StatusSessao status) {
        SessaoPilates sessao = new SessaoPilates();
        sessao.setAula(aula);
        sessao.setPaciente(aula.getPaciente());
        sessao.setTipo(TipoSessao.PILATES);
        sessao.setStatus(status);
        sessao.setData(aula.getData());
        return sessao;
    }
}
