package com.carlesso.pilatesapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.carlesso.pilatesapi.dto.BloqueioAgendaRequestDTO;
import com.carlesso.pilatesapi.entity.BloqueioAgenda;
import com.carlesso.pilatesapi.exception.ResourceNotFoundException;
import com.carlesso.pilatesapi.repository.BloqueioAgendaRepository;
import com.carlesso.pilatesapi.util.PeriodoGuard;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BloqueioAgendaServiceTest {

    private static final LocalDate FERIADO = LocalDate.of(2026, 9, 7);

    @Mock
    BloqueioAgendaRepository repository;

    @InjectMocks
    BloqueioAgendaService service;

    @Test
    void criar_diaInteiro_persisteBloqueioEMarcaComoDiaInteiro() {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.criar(request(FERIADO, FERIADO, null, null, "Feriado da Independência"));

        ArgumentCaptor<BloqueioAgenda> salvo = ArgumentCaptor.forClass(BloqueioAgenda.class);
        verify(repository).save(salvo.capture());
        assertThat(salvo.getValue().getDataInicio()).isEqualTo(FERIADO);
        assertThat(salvo.getValue().getDataFim()).isEqualTo(FERIADO);
        assertThat(salvo.getValue().getHoraInicio()).isNull();
        assertThat(salvo.getValue().getHoraFim()).isNull();
        assertThat(response.diaInteiro()).isTrue();
        assertThat(response.motivo()).isEqualTo("Feriado da Independência");
    }

    @Test
    void criar_comFaixaDeHorario_naoEhDiaInteiro() {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.criar(
                request(FERIADO, FERIADO, LocalTime.of(8, 0), LocalTime.of(12, 0), "Manutenção dos aparelhos"));

        assertThat(response.diaInteiro()).isFalse();
        assertThat(response.horaInicio()).isEqualTo(LocalTime.of(8, 0));
        assertThat(response.horaFim()).isEqualTo(LocalTime.of(12, 0));
    }

    @Test
    void criar_motivoComEspacosAoRedor_persisteMotivoAparado() {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.criar(request(FERIADO, FERIADO, null, null, "  Feriado  "));

        assertThat(response.motivo()).isEqualTo("Feriado");
    }

    @Test
    void criar_dataFimAnteriorADataInicio_lancaIllegalArgument() {
        var dto = request(FERIADO, FERIADO.minusDays(1), null, null, "Feriado");

        assertThatThrownBy(() -> service.criar(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("dataFim não pode ser anterior a dataInicio");
        verify(repository, never()).save(any());
    }

    @Test
    void criar_dataFimIgualADataInicio_ehValido() {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.criar(request(FERIADO, FERIADO, null, null, "Feriado"));

        assertThat(response.dataInicio()).isEqualTo(response.dataFim());
    }

    @Test
    void criar_apenasHoraInicio_lancaIllegalArgument() {
        var dto = request(FERIADO, FERIADO, LocalTime.of(8, 0), null, "Manutenção");

        assertThatThrownBy(() -> service.criar(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("horaInicio e horaFim devem ser informadas juntas");
        verify(repository, never()).save(any());
    }

    @Test
    void criar_apenasHoraFim_lancaIllegalArgument() {
        var dto = request(FERIADO, FERIADO, null, LocalTime.of(12, 0), "Manutenção");

        assertThatThrownBy(() -> service.criar(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("horaInicio e horaFim devem ser informadas juntas");
    }

    @Test
    void criar_horaFimAnteriorAHoraInicio_lancaIllegalArgument() {
        var dto = request(FERIADO, FERIADO, LocalTime.of(12, 0), LocalTime.of(8, 0), "Manutenção");

        assertThatThrownBy(() -> service.criar(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("horaFim deve ser posterior a horaInicio");
    }

    @Test
    void criar_horaFimIgualAHoraInicio_lancaIllegalArgument() {
        var dto = request(FERIADO, FERIADO, LocalTime.of(8, 0), LocalTime.of(8, 0), "Manutenção");

        assertThatThrownBy(() -> service.criar(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("horaFim deve ser posterior a horaInicio");
    }

    @Test
    void listarPorPeriodo_delegaAConsultaDeIntersecao() {
        var bloqueio = bloqueio(FERIADO, FERIADO, "Feriado");
        when(repository.findQueIntersectamPeriodo(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
                .thenReturn(List.of(bloqueio));

        var bloqueios = service.listarPorPeriodo(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        assertThat(bloqueios).singleElement().satisfies(item -> {
            assertThat(item.dataInicio()).isEqualTo(FERIADO);
            assertThat(item.motivo()).isEqualTo("Feriado");
        });
    }

    @Test
    void listarPorPeriodo_semBloqueios_retornaListaVazia() {
        when(repository.findQueIntersectamPeriodo(any(), any())).thenReturn(List.of());

        assertThat(service.listarPorPeriodo(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
                .isEmpty();
    }

    @Test
    void listarPorPeriodo_periodoInvertido_lancaIllegalArgument() {
        assertThatThrownBy(() -> service.listarPorPeriodo(LocalDate.of(2026, 9, 30), LocalDate.of(2026, 9, 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Período inicial não pode ser maior que o período final");
        verifyNoInteractions(repository);
    }

    @Test
    void listarPorPeriodo_periodoAusente_lancaIllegalArgument() {
        assertThatThrownBy(() -> service.listarPorPeriodo(null, LocalDate.of(2026, 9, 30)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Período inicial e final são obrigatórios");
    }

    @Test
    void listarPorPeriodo_acimaDoLimiteDeDias_lancaIllegalArgument() {
        LocalDate inicio = LocalDate.of(2026, 1, 1);
        LocalDate fim = inicio.plusDays(PeriodoGuard.LIMITE_DIAS_AGENDA);

        assertThatThrownBy(() -> service.listarPorPeriodo(inicio, fim))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limitada a " + PeriodoGuard.LIMITE_DIAS_AGENDA + " dias");
    }

    @Test
    void buscarPorId_inexistente_lancaResourceNotFound() {
        when(repository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscarPorId(9L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Bloqueio de agenda não encontrado: 9");
    }

    @Test
    void atualizar_substituiTodosOsCampos() {
        var existente = bloqueio(FERIADO, FERIADO, "Feriado");
        existente.setHoraInicio(LocalTime.of(8, 0));
        existente.setHoraFim(LocalTime.of(12, 0));
        when(repository.findById(1L)).thenReturn(Optional.of(existente));

        var response = service.atualizar(1L, request(FERIADO, FERIADO.plusDays(2), null, null, "Férias coletivas"));

        assertThat(response.dataFim()).isEqualTo(FERIADO.plusDays(2));
        assertThat(response.motivo()).isEqualTo("Férias coletivas");
        // PUT é substituição completa: a faixa de horário some quando o corpo novo não a traz.
        assertThat(response.diaInteiro()).isTrue();
        assertThat(existente.getHoraInicio()).isNull();
        assertThat(existente.getHoraFim()).isNull();
    }

    @Test
    void atualizar_inexistente_lancaResourceNotFound() {
        when(repository.findById(9L)).thenReturn(Optional.empty());
        var dto = request(FERIADO, FERIADO, null, null, "Feriado");

        assertThatThrownBy(() -> service.atualizar(9L, dto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Bloqueio de agenda não encontrado: 9");
    }

    @Test
    void atualizar_periodoInvalido_naoBuscaOBloqueio() {
        var dto = request(FERIADO, FERIADO.minusDays(1), null, null, "Feriado");

        assertThatThrownBy(() -> service.atualizar(1L, dto)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void excluir_removeOBloqueio() {
        var existente = bloqueio(FERIADO, FERIADO, "Feriado");
        when(repository.findById(1L)).thenReturn(Optional.of(existente));

        service.excluir(1L);

        verify(repository).delete(existente);
    }

    @Test
    void excluir_inexistente_lancaResourceNotFound() {
        when(repository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.excluir(9L)).isInstanceOf(ResourceNotFoundException.class);
        verify(repository, never()).delete(any());
    }

    private BloqueioAgendaRequestDTO request(
            LocalDate dataInicio, LocalDate dataFim, LocalTime horaInicio, LocalTime horaFim, String motivo) {
        return new BloqueioAgendaRequestDTO(dataInicio, dataFim, horaInicio, horaFim, motivo);
    }

    private BloqueioAgenda bloqueio(LocalDate dataInicio, LocalDate dataFim, String motivo) {
        BloqueioAgenda bloqueio = new BloqueioAgenda();
        bloqueio.setDataInicio(dataInicio);
        bloqueio.setDataFim(dataFim);
        bloqueio.setMotivo(motivo);
        return bloqueio;
    }
}
