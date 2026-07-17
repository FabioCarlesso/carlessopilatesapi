package com.carlesso.pilatesapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.carlesso.pilatesapi.dto.NotaFiscalEmitidaRequestDTO;
import com.carlesso.pilatesapi.entity.NotaFiscalEmitida;
import com.carlesso.pilatesapi.entity.Paciente;
import com.carlesso.pilatesapi.exception.BusinessException;
import com.carlesso.pilatesapi.exception.ResourceNotFoundException;
import com.carlesso.pilatesapi.repository.NotaFiscalEmitidaRepository;
import com.carlesso.pilatesapi.repository.PacienteRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NotaFiscalEmitidaServiceTest {

    @Mock
    NotaFiscalEmitidaRepository repository;

    @Mock
    PacienteRepository pacienteRepository;

    @InjectMocks
    NotaFiscalEmitidaService service;

    @BeforeEach
    void setUp() {
        // Em produção o proxy transacional é injetado via @Lazy; no teste unitário
        // apontamos o "self" para a própria instância para que registrar() delegue ao upsert().
        service.setSelf(service);
    }

    @Test
    void registrar_quandoNaoExiste_deveCriarNota() {
        Paciente paciente = paciente();
        when(pacienteRepository.findByIdAndAtivoTrue(1L)).thenReturn(Optional.of(paciente));
        when(repository.findByPacienteIdAndCompetencia(1L, LocalDate.of(2026, 4, 1)))
                .thenReturn(Optional.empty());
        when(repository.save(any(NotaFiscalEmitida.class))).thenAnswer(inv -> {
            NotaFiscalEmitida n = inv.getArgument(0);
            ReflectionTestUtils.setField(n, "id", 5L);
            return n;
        });

        var dto = new NotaFiscalEmitidaRequestDTO(
                1L, "04/2026", "NF-123", LocalDate.of(2026, 4, 15), new BigDecimal("250.00"), "ok");

        var response = service.registrar(dto);

        assertThat(response.id()).isEqualTo(5L);
        assertThat(response.pacienteId()).isEqualTo(1L);
        assertThat(response.nomePaciente()).isEqualTo("Ana Souza");
        assertThat(response.competencia()).isEqualTo("04/2026");
        assertThat(response.numeroNota()).isEqualTo("NF-123");
        assertThat(response.dataEmissao()).isEqualTo(LocalDate.of(2026, 4, 15));
        assertThat(response.valor()).isEqualByComparingTo("250.00");
        assertThat(response.dataAtualizacao()).isNull();

        ArgumentCaptor<NotaFiscalEmitida> captor = ArgumentCaptor.forClass(NotaFiscalEmitida.class);
        org.mockito.Mockito.verify(repository).save(captor.capture());
        assertThat(captor.getValue().getCompetencia()).isEqualTo(LocalDate.of(2026, 4, 1));
        // dataCriacao agora é preenchida pelo callback @PrePersist da entidade (coberto no teste de repositório).
    }

    @Test
    void registrar_quandoJaExiste_deveAtualizarNota() {
        Paciente paciente = paciente();
        NotaFiscalEmitida existente = new NotaFiscalEmitida();
        ReflectionTestUtils.setField(existente, "id", 9L);
        existente.setPaciente(paciente);
        existente.setCompetencia(LocalDate.of(2026, 4, 1));
        existente.setNumeroNota("NF-ANTIGA");
        existente.setDataEmissao(LocalDate.of(2026, 4, 5));
        existente.setDataCriacao(LocalDateTime.of(2026, 4, 5, 10, 0));

        when(pacienteRepository.findByIdAndAtivoTrue(1L)).thenReturn(Optional.of(paciente));
        when(repository.findByPacienteIdAndCompetencia(1L, LocalDate.of(2026, 4, 1)))
                .thenReturn(Optional.of(existente));
        when(repository.save(any(NotaFiscalEmitida.class))).thenAnswer(inv -> inv.getArgument(0));

        var dto = new NotaFiscalEmitidaRequestDTO(
                1L, "04/2026", "NF-NOVA", LocalDate.of(2026, 4, 20), new BigDecimal("300.00"), null);

        var response = service.registrar(dto);

        assertThat(response.id()).isEqualTo(9L);
        assertThat(response.numeroNota()).isEqualTo("NF-NOVA");
        assertThat(response.dataEmissao()).isEqualTo(LocalDate.of(2026, 4, 20));
        // dataAtualizacao agora é preenchida pelo callback @PreUpdate da entidade.
    }

    @Test
    void registrar_pacienteInexistente_deveLancar404() {
        when(pacienteRepository.findByIdAndAtivoTrue(1L)).thenReturn(Optional.empty());

        var dto = new NotaFiscalEmitidaRequestDTO(1L, "04/2026", "NF-1", LocalDate.of(2026, 4, 15), null, null);

        assertThatThrownBy(() -> service.registrar(dto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Paciente não encontrado");
    }

    @Test
    void registrar_valorNegativo_deveLancarErroDeNegocio() {
        when(pacienteRepository.findByIdAndAtivoTrue(1L)).thenReturn(Optional.of(paciente()));

        var dto = new NotaFiscalEmitidaRequestDTO(
                1L, "04/2026", "NF-1", LocalDate.of(2026, 4, 15), new BigDecimal("-1.00"), null);

        assertThatThrownBy(() -> service.registrar(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("maior que zero");
    }

    @Test
    void registrar_dataEmissaoFutura_deveLancarErroDeNegocio() {
        when(pacienteRepository.findByIdAndAtivoTrue(1L)).thenReturn(Optional.of(paciente()));

        var dto = new NotaFiscalEmitidaRequestDTO(
                1L, "04/2026", "NF-1", LocalDate.now().plusDays(1), new BigDecimal("250.00"), null);

        assertThatThrownBy(() -> service.registrar(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("futura");
    }

    @Test
    void registrar_competenciaInvalida_deveLancarErro() {
        when(pacienteRepository.findByIdAndAtivoTrue(1L)).thenReturn(Optional.of(paciente()));

        var dto = new NotaFiscalEmitidaRequestDTO(1L, "13/2026", "NF-1", LocalDate.of(2026, 4, 15), null, null);

        assertThatThrownBy(() -> service.registrar(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MM/AAAA");
    }

    @Test
    void listarPorPaciente_pacienteInexistente_deveLancar404() {
        when(pacienteRepository.existsByIdAndAtivoTrue(1L)).thenReturn(false);

        assertThatThrownBy(() -> service.listarPorPaciente(1L)).isInstanceOf(ResourceNotFoundException.class);
    }

    private Paciente paciente() {
        Paciente paciente = new Paciente();
        ReflectionTestUtils.setField(paciente, "id", 1L);
        paciente.setNome("Ana Souza");
        paciente.setCpf("11122233344");
        return paciente;
    }
}
