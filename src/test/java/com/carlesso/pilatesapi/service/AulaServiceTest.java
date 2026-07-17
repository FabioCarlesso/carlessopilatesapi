package com.carlesso.pilatesapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

import com.carlesso.pilatesapi.entity.Aula;
import com.carlesso.pilatesapi.entity.Paciente;
import com.carlesso.pilatesapi.entity.Pagamento;
import com.carlesso.pilatesapi.entity.Plano;
import com.carlesso.pilatesapi.entity.Profissional;
import com.carlesso.pilatesapi.entity.enums.FrequenciaSemanal;
import com.carlesso.pilatesapi.entity.enums.StatusPagamento;
import com.carlesso.pilatesapi.entity.enums.TipoContrato;
import com.carlesso.pilatesapi.entity.enums.TipoPagamento;
import com.carlesso.pilatesapi.exception.BusinessException;
import com.carlesso.pilatesapi.exception.ConflictException;
import com.carlesso.pilatesapi.exception.ResourceNotFoundException;
import com.carlesso.pilatesapi.repository.AulaRepository;
import com.carlesso.pilatesapi.repository.ProfissionalRepository;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class AulaServiceTest {

    @Mock
    AulaRepository aulaRepository;

    @Mock
    ProfissionalRepository profissionalRepository;

    @InjectMocks
    AulaService service;

    private Paciente paciente;
    private Plano plano;
    private Pagamento pagamentoPago;

    @BeforeEach
    void setUp() {
        paciente = new Paciente();
        paciente.setNome("Ana");
        paciente.setEmail("ana@email.com");
        paciente.setCpf("111.222.333-44");

        // Fevereiro 2025: MONDAY (3,10,17,24) e WEDNESDAY (5,12,19,26) → 8 aulas exatas
        plano = new Plano();
        plano.setPaciente(paciente);
        plano.setTipo(TipoPagamento.MENSAL);
        plano.setValor(new BigDecimal("200.00"));
        plano.setFrequenciaSemanal(FrequenciaSemanal.DUAS_VEZES);
        plano.setDiasSemana(List.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY));
        plano.setDataInicio(LocalDate.of(2025, 2, 1));

        pagamentoPago = new Pagamento();
        pagamentoPago.setPaciente(paciente);
        pagamentoPago.setPlano(plano);
        pagamentoPago.setValor(new BigDecimal("200.00"));
        pagamentoPago.setStatus(StatusPagamento.PAGO);
        pagamentoPago.setPeriodoInicio(LocalDate.of(2025, 2, 1));
        pagamentoPago.setPeriodoFim(LocalDate.of(2025, 2, 28));
        pagamentoPago.setDataVencimento(LocalDate.of(2025, 2, 10));
    }

    @Test
    void metodosDeLeitura_saoTransacionaisReadOnly() throws Exception {
        assertReadOnly("buscarPorId", Long.class);
        assertReadOnly("buscarPorPaciente", Long.class);
        assertReadOnly("buscarPorPagamento", Long.class);
    }

    @Test
    void gerarAulas_calculaCorretamenteQuantidadePorFrequencia() {
        when(aulaRepository.existsByPacienteAndData(any(), any())).thenReturn(false);
        when(aulaRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        List<Aula> aulas = service.gerarAulas(pagamentoPago);

        // Fevereiro 2025: 4 segundas + 4 quartas = 8 aulas
        assertThat(aulas).hasSize(8);
    }

    @Test
    void gerarAulas_geraDatasCorrentes() {
        when(aulaRepository.existsByPacienteAndData(any(), any())).thenReturn(false);
        when(aulaRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        List<Aula> aulas = service.gerarAulas(pagamentoPago);

        List<LocalDate> datas = aulas.stream().map(Aula::getData).toList();
        assertThat(datas)
                .containsExactlyInAnyOrder(
                        LocalDate.of(2025, 2, 3), // seg
                        LocalDate.of(2025, 2, 5), // qua
                        LocalDate.of(2025, 2, 10), // seg
                        LocalDate.of(2025, 2, 12), // qua
                        LocalDate.of(2025, 2, 17), // seg
                        LocalDate.of(2025, 2, 19), // qua
                        LocalDate.of(2025, 2, 24), // seg
                        LocalDate.of(2025, 2, 26) // qua
                        );
    }

    @Test
    void gerarAulas_naoDuplicaAulasExistentes() {
        // Simula que segunda dia 3 já existe
        when(aulaRepository.existsByPacienteAndData(paciente, LocalDate.of(2025, 2, 3)))
                .thenReturn(true);
        when(aulaRepository.existsByPacienteAndData(any(), argThat(d -> !d.equals(LocalDate.of(2025, 2, 3)))))
                .thenReturn(false);
        when(aulaRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        List<Aula> aulas = service.gerarAulas(pagamentoPago);

        assertThat(aulas).hasSize(7); // 8 - 1 existente
    }

    @Test
    void gerarAulas_pagamentoPendente_lancaExcecao() {
        pagamentoPago.setStatus(StatusPagamento.PENDENTE);

        assertThatThrownBy(() -> service.gerarAulas(pagamentoPago))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("PAGO");
    }

    @Test
    void gerarAulas_pacienteInativo_lancaExcecao() {
        paciente.setAtivo(false);

        assertThatThrownBy(() -> service.gerarAulas(pagamentoPago))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("inativo");
    }

    @Test
    void realizarAula_marcaComoRealizada() {
        Aula aula = new Aula();
        aula.setPaciente(paciente);
        aula.setPagamento(pagamentoPago);
        aula.setData(LocalDate.of(2025, 2, 3));

        when(aulaRepository.findByIdAndPacienteAtivoTrue(1L)).thenReturn(Optional.of(aula));

        var response = service.realizarAula(1L);

        assertThat(response.realizada()).isTrue();
    }

    @Test
    void realizarAula_comProfissional_vinculaProfissional() {
        Aula aula = new Aula();
        aula.setPaciente(paciente);
        aula.setPagamento(pagamentoPago);
        aula.setData(LocalDate.of(2025, 2, 3));

        Profissional profissional = new Profissional();
        profissional.setId(1L);
        profissional.setNome("Paula Mendes");
        profissional.setEmail("paula@email.com");
        profissional.setCpf("12345678900");
        profissional.setTipoContrato(TipoContrato.PJ);
        profissional.setPercentualPagamentoAula(new BigDecimal("45.00"));
        profissional.setDataInicio(LocalDate.of(2024, 1, 15));

        when(aulaRepository.findByIdAndPacienteAtivoTrue(1L)).thenReturn(Optional.of(aula));
        when(profissionalRepository.findById(1L)).thenReturn(Optional.of(profissional));

        service.realizarAula(1L, 1L);

        assertThat(aula.getProfissional()).isEqualTo(profissional);
        assertThat(aula.isRealizada()).isTrue();
    }

    @Test
    void realizarAula_profissionalInativo_lancaExcecao() {
        Aula aula = new Aula();
        aula.setPaciente(paciente);
        aula.setPagamento(pagamentoPago);
        aula.setData(LocalDate.of(2025, 2, 3));

        Profissional profissional = new Profissional();
        profissional.setAtivo(false);

        when(aulaRepository.findByIdAndPacienteAtivoTrue(1L)).thenReturn(Optional.of(aula));
        when(profissionalRepository.findById(1L)).thenReturn(Optional.of(profissional));

        assertThatThrownBy(() -> service.realizarAula(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Profissional inativo");
    }

    @Test
    void realizarAula_jaRealizada_lancaExcecao() {
        Aula aula = new Aula();
        aula.setPaciente(paciente);
        aula.setPagamento(pagamentoPago);
        aula.setData(LocalDate.of(2025, 2, 3));
        aula.setRealizada(true);

        when(aulaRepository.findByIdAndPacienteAtivoTrue(1L)).thenReturn(Optional.of(aula));

        assertThatThrownBy(() -> service.realizarAula(1L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("já foi marcada");
    }

    @Test
    void realizarAula_pacienteInativo_lancaResourceNotFound() {
        when(aulaRepository.findByIdAndPacienteAtivoTrue(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.realizarAula(1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Aula não encontrada: 1");
    }

    @Test
    void buscarPorId_naoEncontrado_lancaExcecao() {
        when(aulaRepository.findByIdAndPacienteAtivoTrue(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscarPorId(99L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void buscarPorId_pacienteInativo_lancaResourceNotFound() {
        when(aulaRepository.findByIdAndPacienteAtivoTrue(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscarPorId(1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Aula não encontrada: 1");
    }

    @Test
    void realizarAula_profissionalNaoEncontrado_lancaResourceNotFound() {
        Aula aula = new Aula();
        aula.setPaciente(paciente);
        aula.setPagamento(pagamentoPago);
        aula.setData(LocalDate.of(2025, 2, 3));

        when(aulaRepository.findByIdAndPacienteAtivoTrue(1L)).thenReturn(Optional.of(aula));
        when(profissionalRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.realizarAula(1L, 99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Profissional não encontrado: 99");
    }

    private void assertReadOnly(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = AulaService.class.getMethod(methodName, parameterTypes);
        Transactional transactional = method.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isTrue();
    }
}
