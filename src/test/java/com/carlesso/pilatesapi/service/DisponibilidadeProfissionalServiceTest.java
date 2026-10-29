package com.carlesso.pilatesapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.carlesso.pilatesapi.dto.DisponibilidadeProfissionalRequestDTO;
import com.carlesso.pilatesapi.dto.DisponibilidadeProfissionalResponseDTO;
import com.carlesso.pilatesapi.entity.DisponibilidadeProfissional;
import com.carlesso.pilatesapi.entity.Profissional;
import com.carlesso.pilatesapi.exception.BusinessException;
import com.carlesso.pilatesapi.exception.ConflictException;
import com.carlesso.pilatesapi.exception.ResourceNotFoundException;
import com.carlesso.pilatesapi.repository.DisponibilidadeProfissionalRepository;
import com.carlesso.pilatesapi.repository.ProfissionalRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DisponibilidadeProfissionalServiceTest {

    private static final Long PROFISSIONAL_ID = 7L;
    /** 2026-11-02 é uma segunda-feira. */
    private static final LocalDate SEGUNDA = LocalDate.of(2026, 11, 2);

    @Mock
    DisponibilidadeProfissionalRepository repository;

    @Mock
    ProfissionalRepository profissionalRepository;

    @InjectMocks
    DisponibilidadeProfissionalService service;

    // ---------- criar ----------

    @Test
    void criar_faixaValida_travaOProfissionalAntesDeChecarESalva() {
        Profissional profissional = profissional(true);
        when(profissionalRepository.findByIdForUpdate(PROFISSIONAL_ID)).thenReturn(Optional.of(profissional));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        DisponibilidadeProfissionalResponseDTO response = service.criar(PROFISSIONAL_ID, request(7, 12));

        InOrder ordem = inOrder(profissionalRepository, repository);
        ordem.verify(profissionalRepository).findByIdForUpdate(PROFISSIONAL_ID);
        ordem.verify(repository).existeSobreposicao(PROFISSIONAL_ID, DayOfWeek.MONDAY, hora(7), hora(12), 0L);
        ArgumentCaptor<DisponibilidadeProfissional> salvo = ArgumentCaptor.forClass(DisponibilidadeProfissional.class);
        ordem.verify(repository).save(salvo.capture());
        assertThat(salvo.getValue().getProfissional()).isSameAs(profissional);
        assertThat(response.profissionalId()).isEqualTo(PROFISSIONAL_ID);
        assertThat(response.diaSemana()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(response.horaInicio()).isEqualTo(hora(7));
        assertThat(response.horaFim()).isEqualTo(hora(12));
    }

    @Test
    void criar_faixaSobreposta_lancaConflito() {
        when(profissionalRepository.findByIdForUpdate(PROFISSIONAL_ID)).thenReturn(Optional.of(profissional(true)));
        when(repository.existeSobreposicao(PROFISSIONAL_ID, DayOfWeek.MONDAY, hora(11), hora(13), 0L))
                .thenReturn(true);

        assertThatThrownBy(() -> service.criar(PROFISSIONAL_ID, request(11, 13)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("se sobrepõe");
        verify(repository, never()).save(any());
    }

    @Test
    void criar_horaInicioIgualAHoraFim_lancaBusinessException() {
        when(profissionalRepository.findByIdForUpdate(PROFISSIONAL_ID)).thenReturn(Optional.of(profissional(true)));

        assertThatThrownBy(() -> service.criar(PROFISSIONAL_ID, request(12, 12)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("horaInicio deve ser anterior a horaFim");
        verify(repository, never()).save(any());
    }

    @Test
    void criar_horaInicioPosteriorAHoraFim_lancaBusinessException() {
        when(profissionalRepository.findByIdForUpdate(PROFISSIONAL_ID)).thenReturn(Optional.of(profissional(true)));

        assertThatThrownBy(() -> service.criar(PROFISSIONAL_ID, request(14, 12)))
                .isInstanceOf(BusinessException.class);
        verify(repository, never()).existeSobreposicao(any(), any(), any(), any(), anyLong());
    }

    @Test
    void criar_profissionalInexistente_lancaNotFound() {
        when(profissionalRepository.findByIdForUpdate(PROFISSIONAL_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.criar(PROFISSIONAL_ID, request(7, 12)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Profissional não encontrado");
    }

    @Test
    void criar_profissionalInativo_lancaBusinessException() {
        when(profissionalRepository.findByIdForUpdate(PROFISSIONAL_ID)).thenReturn(Optional.of(profissional(false)));

        assertThatThrownBy(() -> service.criar(PROFISSIONAL_ID, request(7, 12)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("inativo");
        verify(repository, never()).save(any());
    }

    // ---------- atualizar ----------

    @Test
    void atualizar_ignoraAPropriaFaixaNaChecagemDeSobreposicao() {
        DisponibilidadeProfissional existente = faixa(10L, DayOfWeek.MONDAY, 7, 12);
        when(repository.findById(10L)).thenReturn(Optional.of(existente));
        when(profissionalRepository.findByIdForUpdate(PROFISSIONAL_ID)).thenReturn(Optional.of(profissional(true)));
        when(repository.saveAndFlush(existente)).thenReturn(existente);

        var response = service.atualizar(10L, request(DayOfWeek.MONDAY, 8, 12, null));

        verify(repository).existeSobreposicao(PROFISSIONAL_ID, DayOfWeek.MONDAY, hora(8), hora(12), 10L);
        assertThat(response.horaInicio()).isEqualTo(hora(8));
    }

    @Test
    void atualizar_trocandoODia_aplicaTodosOsCampos() {
        DisponibilidadeProfissional existente = faixa(10L, DayOfWeek.MONDAY, 7, 12);
        when(repository.findById(10L)).thenReturn(Optional.of(existente));
        when(profissionalRepository.findByIdForUpdate(PROFISSIONAL_ID)).thenReturn(Optional.of(profissional(true)));
        when(repository.saveAndFlush(existente)).thenReturn(existente);

        var response = service.atualizar(10L, request(DayOfWeek.FRIDAY, 14, 19, null));

        assertThat(response.diaSemana()).isEqualTo(DayOfWeek.FRIDAY);
        assertThat(response.horaInicio()).isEqualTo(hora(14));
        assertThat(response.horaFim()).isEqualTo(hora(19));
    }

    @Test
    void atualizar_sobrepondoOutraFaixa_lancaConflito() {
        DisponibilidadeProfissional existente = faixa(10L, DayOfWeek.MONDAY, 7, 12);
        when(repository.findById(10L)).thenReturn(Optional.of(existente));
        when(profissionalRepository.findByIdForUpdate(PROFISSIONAL_ID)).thenReturn(Optional.of(profissional(true)));
        when(repository.existeSobreposicao(PROFISSIONAL_ID, DayOfWeek.MONDAY, hora(7), hora(15), 10L))
                .thenReturn(true);

        assertThatThrownBy(() -> service.atualizar(10L, request(DayOfWeek.MONDAY, 7, 15, null)))
                .isInstanceOf(ConflictException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void atualizar_versaoDivergente_lancaLockingOtimista() {
        DisponibilidadeProfissional existente = faixa(10L, DayOfWeek.MONDAY, 7, 12);
        ReflectionTestUtils.setField(existente, "version", 3L);
        when(repository.findById(10L)).thenReturn(Optional.of(existente));

        assertThatThrownBy(() -> service.atualizar(10L, request(DayOfWeek.MONDAY, 7, 12, 2L)))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        verify(profissionalRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void atualizar_faixaInexistente_lancaNotFound() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.atualizar(99L, request(7, 12)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Disponibilidade não encontrada: 99");
    }

    @Test
    void atualizar_profissionalInativo_lancaBusinessException() {
        when(repository.findById(10L)).thenReturn(Optional.of(faixa(10L, DayOfWeek.MONDAY, 7, 12)));
        when(profissionalRepository.findByIdForUpdate(PROFISSIONAL_ID)).thenReturn(Optional.of(profissional(false)));

        assertThatThrownBy(() -> service.atualizar(10L, request(7, 12))).isInstanceOf(BusinessException.class);
    }

    // ---------- listar / excluir ----------

    @Test
    void listar_ordenaPorDiaDaSemanaEHoraDeInicio() {
        when(profissionalRepository.existsById(PROFISSIONAL_ID)).thenReturn(true);
        when(repository.findByProfissionalId(PROFISSIONAL_ID))
                .thenReturn(List.of(
                        faixa(1L, DayOfWeek.FRIDAY, 8, 12),
                        faixa(2L, DayOfWeek.MONDAY, 14, 19),
                        faixa(3L, DayOfWeek.SUNDAY, 8, 10),
                        faixa(4L, DayOfWeek.MONDAY, 7, 12)));

        assertThat(service.listar(PROFISSIONAL_ID))
                .extracting(DisponibilidadeProfissionalResponseDTO::id)
                .containsExactly(4L, 2L, 1L, 3L);
    }

    @Test
    void listar_profissionalInexistente_lancaNotFound() {
        when(profissionalRepository.existsById(PROFISSIONAL_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.listar(PROFISSIONAL_ID)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void excluir_removeFisicamente() {
        DisponibilidadeProfissional existente = faixa(10L, DayOfWeek.MONDAY, 7, 12);
        when(repository.findById(10L)).thenReturn(Optional.of(existente));

        service.excluir(10L);

        verify(repository).delete(existente);
    }

    @Test
    void excluir_faixaInexistente_lancaNotFound() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.excluir(99L)).isInstanceOf(ResourceNotFoundException.class);
        verify(repository, never()).delete(any());
    }

    // ---------- verificar ----------

    @Test
    void verificar_profissionalSemFaixas_respondeTrue() {
        when(profissionalRepository.existsById(PROFISSIONAL_ID)).thenReturn(true);
        when(repository.existsByProfissionalId(PROFISSIONAL_ID)).thenReturn(false);

        assertThat(service.verificar(PROFISSIONAL_ID, SEGUNDA, hora(6)).dentroDaDisponibilidade())
                .isTrue();
        verify(repository, never()).findByProfissionalIdAndDiaSemana(any(), any());
    }

    @Test
    void verificar_dentroEForaDasFaixasDoDia() {
        when(profissionalRepository.existsById(PROFISSIONAL_ID)).thenReturn(true);
        when(repository.existsByProfissionalId(PROFISSIONAL_ID)).thenReturn(true);
        when(repository.findByProfissionalIdAndDiaSemana(eq(PROFISSIONAL_ID), eq(DayOfWeek.MONDAY)))
                .thenReturn(List.of(faixa(1L, DayOfWeek.MONDAY, 7, 12), faixa(2L, DayOfWeek.MONDAY, 14, 19)));

        assertThat(service.verificar(PROFISSIONAL_ID, SEGUNDA, hora(13)).dentroDaDisponibilidade())
                .isFalse();
        assertThat(service.verificar(PROFISSIONAL_ID, SEGUNDA, hora(15)).dentroDaDisponibilidade())
                .isTrue();
        // Início inclusivo, fim exclusivo.
        assertThat(service.verificar(PROFISSIONAL_ID, SEGUNDA, hora(7)).dentroDaDisponibilidade())
                .isTrue();
        assertThat(service.verificar(PROFISSIONAL_ID, SEGUNDA, hora(12)).dentroDaDisponibilidade())
                .isFalse();
    }

    @Test
    void verificar_diaSemFaixaDeProfissionalConfigurado_respondeFalse() {
        when(profissionalRepository.existsById(PROFISSIONAL_ID)).thenReturn(true);
        when(repository.existsByProfissionalId(PROFISSIONAL_ID)).thenReturn(true);
        when(repository.findByProfissionalIdAndDiaSemana(PROFISSIONAL_ID, DayOfWeek.SUNDAY))
                .thenReturn(List.of());

        assertThat(service.verificar(PROFISSIONAL_ID, SEGUNDA.minusDays(1), hora(6))
                        .dentroDaDisponibilidade())
                .isFalse();
    }

    @Test
    void verificar_profissionalInexistente_lancaNotFound() {
        when(profissionalRepository.existsById(PROFISSIONAL_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.verificar(PROFISSIONAL_ID, SEGUNDA, hora(8)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------- helpers ----------

    private static LocalTime hora(int hora) {
        return LocalTime.of(hora, 0);
    }

    private static DisponibilidadeProfissionalRequestDTO request(int inicio, int fim) {
        return request(DayOfWeek.MONDAY, inicio, fim, null);
    }

    private static DisponibilidadeProfissionalRequestDTO request(DayOfWeek dia, int inicio, int fim, Long version) {
        return new DisponibilidadeProfissionalRequestDTO(dia, hora(inicio), hora(fim), version);
    }

    private static Profissional profissional(boolean ativo) {
        Profissional profissional = new Profissional();
        profissional.setId(PROFISSIONAL_ID);
        profissional.setNome("Paula");
        profissional.setAtivo(ativo);
        return profissional;
    }

    private static DisponibilidadeProfissional faixa(Long id, DayOfWeek dia, int inicio, int fim) {
        DisponibilidadeProfissional faixa = new DisponibilidadeProfissional();
        faixa.setId(id);
        faixa.setProfissional(profissional(true));
        faixa.setDiaSemana(dia);
        faixa.setHoraInicio(hora(inicio));
        faixa.setHoraFim(hora(fim));
        return faixa;
    }
}
