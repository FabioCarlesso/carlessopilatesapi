package com.carlesso.pilatesapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.carlesso.pilatesapi.dto.ListaEsperaRequestDTO;
import com.carlesso.pilatesapi.entity.ListaEspera;
import com.carlesso.pilatesapi.entity.Paciente;
import com.carlesso.pilatesapi.exception.BusinessException;
import com.carlesso.pilatesapi.exception.ConflictException;
import com.carlesso.pilatesapi.exception.ResourceNotFoundException;
import com.carlesso.pilatesapi.repository.ListaEsperaRepository;
import com.carlesso.pilatesapi.repository.PacienteRepository;
import com.carlesso.pilatesapi.util.PeriodoGuard;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
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
class ListaEsperaServiceTest {

    private static final DayOfWeek SEGUNDA = DayOfWeek.MONDAY;
    private static final LocalTime INICIO = LocalTime.of(8, 0);
    private static final LocalTime FIM = LocalTime.of(9, 0);

    @Mock
    ListaEsperaRepository repository;

    @Mock
    PacienteRepository pacienteRepository;

    @InjectMocks
    ListaEsperaService service;

    @Test
    void criar_persisteEntradaComOsDadosDoPedido() {
        when(pacienteRepository.findById(1L)).thenReturn(Optional.of(paciente(1L, true)));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.criar(request(1L, SEGUNDA, INICIO, FIM, "Prefere aparelho"));

        ArgumentCaptor<ListaEspera> salva = ArgumentCaptor.forClass(ListaEspera.class);
        verify(repository).saveAndFlush(salva.capture());
        assertThat(salva.getValue().getPaciente().getId()).isEqualTo(1L);
        assertThat(salva.getValue().getDiaSemana()).isEqualTo(SEGUNDA);
        assertThat(salva.getValue().getHoraInicio()).isEqualTo(INICIO);
        assertThat(salva.getValue().getHoraFim()).isEqualTo(FIM);
        assertThat(salva.getValue().isAtivo()).isTrue();
        assertThat(response.pacienteNome()).isEqualTo("Ana");
        assertThat(response.observacao()).isEqualTo("Prefere aparelho");
    }

    /** A ordem de chegada é do servidor: o service nunca escreve dataEntrada, quem a define é o {@code @PrePersist}. */
    @Test
    void criar_naoDefineDataEntradaNoService() {
        when(pacienteRepository.findById(1L)).thenReturn(Optional.of(paciente(1L, true)));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.criar(request(1L, SEGUNDA, INICIO, FIM, null));

        ArgumentCaptor<ListaEspera> salva = ArgumentCaptor.forClass(ListaEspera.class);
        verify(repository).saveAndFlush(salva.capture());
        assertThat(salva.getValue().getDataEntrada()).isNull();
    }

    @Test
    void criar_observacaoEmBranco_persisteNulo() {
        when(pacienteRepository.findById(1L)).thenReturn(Optional.of(paciente(1L, true)));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.criar(request(1L, SEGUNDA, INICIO, FIM, "   "));

        assertThat(response.observacao()).isNull();
    }

    @Test
    void criar_observacaoComEspacosAoRedor_persisteAparada() {
        when(pacienteRepository.findById(1L)).thenReturn(Optional.of(paciente(1L, true)));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.criar(request(1L, SEGUNDA, INICIO, FIM, "  Só à tarde  "));

        assertThat(response.observacao()).isEqualTo("Só à tarde");
    }

    @Test
    void criar_pacienteInexistente_lancaResourceNotFound() {
        when(pacienteRepository.findById(9L)).thenReturn(Optional.empty());
        var dto = request(9L, SEGUNDA, INICIO, FIM, null);

        assertThatThrownBy(() -> service.criar(dto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Paciente não encontrado: 9");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void criar_pacienteInativo_lancaBusinessException() {
        when(pacienteRepository.findById(1L)).thenReturn(Optional.of(paciente(1L, false)));
        var dto = request(1L, SEGUNDA, INICIO, FIM, null);

        assertThatThrownBy(() -> service.criar(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Paciente inativo");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void criar_entradaDuplicada_lancaConflict() {
        when(pacienteRepository.findById(1L)).thenReturn(Optional.of(paciente(1L, true)));
        when(repository.existeAtivaParaPacienteNaFaixa(1L, SEGUNDA, INICIO, FIM))
                .thenReturn(true);
        var dto = request(1L, SEGUNDA, INICIO, FIM, null);

        assertThatThrownBy(() -> service.criar(dto))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Paciente já está na lista de espera para esse dia e faixa de horário");
        verify(repository, never()).saveAndFlush(any());
    }

    /**
     * A corrida que o `exists` não pega: duas requisições simultâneas passam as duas pela checagem prévia, e quem
     * perde esbarra no índice parcial da V37. O 409 documentado tem que sair daí também, não um 500.
     */
    @Test
    void criar_violacaoDoIndiceUnico_lancaConflict() {
        when(pacienteRepository.findById(1L)).thenReturn(Optional.of(paciente(1L, true)));
        when(repository.existeAtivaParaPacienteNaFaixa(1L, SEGUNDA, INICIO, FIM))
                .thenReturn(false);
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("lista_espera_ativa_uidx"));
        var dto = request(1L, SEGUNDA, INICIO, FIM, null);

        assertThatThrownBy(() -> service.criar(dto))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Paciente já está na lista de espera para esse dia e faixa de horário");
    }

    @Test
    void criar_horaFimAnteriorAHoraInicio_lancaIllegalArgument() {
        var dto = request(1L, SEGUNDA, FIM, INICIO, null);

        assertThatThrownBy(() -> service.criar(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("horaFim deve ser posterior a horaInicio");
        verifyNoInteractions(pacienteRepository, repository);
    }

    @Test
    void criar_horaFimIgualAHoraInicio_lancaIllegalArgument() {
        var dto = request(1L, SEGUNDA, INICIO, INICIO, null);

        assertThatThrownBy(() -> service.criar(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("horaFim deve ser posterior a horaInicio");
    }

    @Test
    void listar_delegaOsFiltrosERetornaAFilaNaOrdemDoRepositorio() {
        when(repository.buscarAtivas(SEGUNDA, INICIO, FIM, null))
                .thenReturn(List.of(
                        entrada(1L, "Primeiro", LocalDateTime.of(2026, 8, 1, 9, 0)),
                        entrada(2L, "Segundo", LocalDateTime.of(2026, 8, 2, 9, 0))));

        var fila = service.listar(SEGUNDA, INICIO, FIM, null);

        assertThat(fila).extracting("observacao").containsExactly("Primeiro", "Segundo");
        assertThat(fila.getFirst().dataEntrada()).isEqualTo(LocalDateTime.of(2026, 8, 1, 9, 0));
    }

    @Test
    void listar_semFiltros_consultaAFilaInteira() {
        when(repository.buscarAtivas(null, null, null, null)).thenReturn(List.of());

        assertThat(service.listar(null, null, null, null)).isEmpty();
    }

    @Test
    void listar_apenasHoraInicio_lancaIllegalArgument() {
        assertThatThrownBy(() -> service.listar(SEGUNDA, INICIO, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("horaInicio e horaFim devem ser informadas juntas");
        verifyNoInteractions(repository);
    }

    @Test
    void listar_apenasHoraFim_lancaIllegalArgument() {
        assertThatThrownBy(() -> service.listar(SEGUNDA, null, FIM, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("horaInicio e horaFim devem ser informadas juntas");
    }

    @Test
    void listar_faixaInvertida_lancaIllegalArgument() {
        assertThatThrownBy(() -> service.listar(SEGUNDA, FIM, INICIO, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("horaFim deve ser posterior a horaInicio");
        verifyNoInteractions(repository);
    }

    @Test
    void listar_acimaDoLimiteDeRegistros_lancaIllegalArgument() {
        var entrada = entrada(1L, "Na fila", LocalDateTime.of(2026, 8, 1, 9, 0));
        when(repository.buscarAtivas(null, null, null, null))
                .thenReturn(Collections.nCopies(PeriodoGuard.LIMITE_REGISTROS_AGENDA + 1, entrada));

        assertThatThrownBy(() -> service.listar(null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limitada a " + PeriodoGuard.LIMITE_REGISTROS_AGENDA + " registros");
    }

    @Test
    void listar_exatamenteNoLimiteDeRegistros_ehPermitido() {
        var entrada = entrada(1L, "Na fila", LocalDateTime.of(2026, 8, 1, 9, 0));
        when(repository.buscarAtivas(null, null, null, null))
                .thenReturn(Collections.nCopies(PeriodoGuard.LIMITE_REGISTROS_AGENDA, entrada));

        assertThat(service.listar(null, null, null, null)).hasSize(PeriodoGuard.LIMITE_REGISTROS_AGENDA);
    }

    @Test
    void buscarPorId_retornaAEntradaAtiva() {
        when(repository.findAtivaById(1L))
                .thenReturn(Optional.of(entrada(1L, "Na fila", LocalDateTime.of(2026, 8, 1, 9, 0))));

        var response = service.buscarPorId(1L);

        assertThat(response.observacao()).isEqualTo("Na fila");
        assertThat(response.dataEntrada()).isEqualTo(LocalDateTime.of(2026, 8, 1, 9, 0));
    }

    @Test
    void buscarPorId_removidaOuInexistente_lancaResourceNotFound() {
        when(repository.findAtivaById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscarPorId(9L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Entrada da lista de espera não encontrada: 9");
    }

    @Test
    void remover_desativaAEntradaEmVezDeApagar() {
        var entrada = entrada(1L, "Na fila", LocalDateTime.of(2026, 8, 1, 9, 0));
        when(repository.findAtivaById(1L)).thenReturn(Optional.of(entrada));

        service.remover(1L);

        assertThat(entrada.isAtivo()).isFalse();
        verify(repository, never()).delete(any());
    }

    @Test
    void remover_inexistente_lancaResourceNotFound() {
        when(repository.findAtivaById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.remover(9L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Entrada da lista de espera não encontrada: 9");
    }

    private ListaEsperaRequestDTO request(
            Long pacienteId, DayOfWeek diaSemana, LocalTime horaInicio, LocalTime horaFim, String observacao) {
        return new ListaEsperaRequestDTO(pacienteId, diaSemana, horaInicio, horaFim, observacao);
    }

    private Paciente paciente(Long id, boolean ativo) {
        Paciente paciente = new Paciente();
        paciente.setNome("Ana");
        paciente.setAtivo(ativo);
        setId(paciente, id);
        return paciente;
    }

    private void setId(Paciente paciente, Long id) {
        try {
            var field = Paciente.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(paciente, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private ListaEspera entrada(Long pacienteId, String observacao, LocalDateTime dataEntrada) {
        ListaEspera entrada = new ListaEspera();
        entrada.setPaciente(paciente(pacienteId, true));
        entrada.setDiaSemana(SEGUNDA);
        entrada.setHoraInicio(INICIO);
        entrada.setHoraFim(FIM);
        entrada.setDataEntrada(dataEntrada);
        entrada.setObservacao(observacao);
        return entrada;
    }
}
