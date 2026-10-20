package com.carlesso.pilatesapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

import com.carlesso.pilatesapi.config.AgendaProperties;
import com.carlesso.pilatesapi.dto.AulaResponseDTO;
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
import com.carlesso.pilatesapi.util.PeriodoGuard;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class AulaServiceTest {

    @Mock
    AulaRepository aulaRepository;

    @Mock
    ProfissionalRepository profissionalRepository;

    AulaService service;

    private Paciente paciente;
    private Plano plano;
    private Pagamento pagamentoPago;

    @BeforeEach
    void setUp() {
        service = new AulaService(aulaRepository, profissionalRepository, new AgendaProperties(60, 3));

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
        assertReadOnly("buscarPorPaciente", Long.class, Pageable.class);
        assertReadOnly("buscarPorPagamento", Long.class);
        assertReadOnly("listarPorPeriodo", LocalDate.class, LocalDate.class, Long.class, Long.class, Boolean.class);
    }

    @Test
    void listarPorPeriodo_repassaFiltrosAoRepositorio() {
        AulaResponseDTO aula =
                new AulaResponseDTO(1L, 1L, "Ana", 7L, "Paula Mendes", 1L, LocalDate.of(2025, 2, 3), null, true);
        when(aulaRepository.findAgendaPorPeriodo(LocalDate.of(2025, 2, 1), LocalDate.of(2025, 2, 28), 7L, 1L, true))
                .thenReturn(List.of(aula));

        var aulas = service.listarPorPeriodo(LocalDate.of(2025, 2, 1), LocalDate.of(2025, 2, 28), 7L, 1L, true);

        assertThat(aulas).containsExactly(aula);
    }

    @Test
    void listarPorPeriodo_periodoInvertido_lancaIllegalArgument() {
        assertThatThrownBy(() ->
                        service.listarPorPeriodo(LocalDate.of(2025, 3, 1), LocalDate.of(2025, 2, 1), null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("não pode ser maior");
        verifyNoInteractions(aulaRepository);
    }

    @Test
    void listarPorPeriodo_acimaDoLimiteDeDias_lancaIllegalArgument() {
        assertThatThrownBy(() ->
                        service.listarPorPeriodo(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 4, 3), null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("92 dias");
        verifyNoInteractions(aulaRepository);
    }

    @Test
    void listarPorPeriodo_acimaDoLimiteDeRegistros_lancaIllegalArgument() {
        AulaResponseDTO aula =
                new AulaResponseDTO(1L, 1L, "Ana", null, null, 1L, LocalDate.of(2025, 2, 3), null, false);
        when(aulaRepository.findAgendaPorPeriodo(any(), any(), any(), any(), any()))
                .thenReturn(Collections.nCopies(PeriodoGuard.LIMITE_REGISTROS_AGENDA + 1, aula));

        assertThatThrownBy(() ->
                        service.listarPorPeriodo(LocalDate.of(2025, 2, 1), LocalDate.of(2025, 2, 28), null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("5000 registros");
    }

    @Test
    void listarPorPeriodo_noLimiteExatoDeRegistros_retornaLista() {
        AulaResponseDTO aula =
                new AulaResponseDTO(1L, 1L, "Ana", null, null, 1L, LocalDate.of(2025, 2, 3), null, false);
        when(aulaRepository.findAgendaPorPeriodo(any(), any(), any(), any(), any()))
                .thenReturn(Collections.nCopies(PeriodoGuard.LIMITE_REGISTROS_AGENDA, aula));

        assertThat(service.listarPorPeriodo(LocalDate.of(2025, 2, 1), LocalDate.of(2025, 2, 28), null, null, null))
                .hasSize(PeriodoGuard.LIMITE_REGISTROS_AGENDA);
    }

    @Test
    void listarPorPeriodo_noLimiteExatoDeDias_consultaRepositorio() {
        when(aulaRepository.findAgendaPorPeriodo(any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        // 2025-01-01 a 2025-04-02 = 92 dias contando as duas pontas
        assertThat(service.listarPorPeriodo(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 4, 2), null, null, null))
                .isEmpty();
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

    @Test
    void realizarAula_semProfissionalId_preservaProfissionalAtribuido() {
        Profissional atribuido = profissionalAtivo(1L, "Paula Mendes");
        Aula aula = aulaPendente();
        aula.setProfissional(atribuido);

        when(aulaRepository.findByIdAndPacienteAtivoTrue(1L)).thenReturn(Optional.of(aula));

        var response = service.realizarAula(1L);

        assertThat(aula.getProfissional()).isEqualTo(atribuido);
        assertThat(response.profissionalId()).isEqualTo(1L);
        assertThat(response.realizada()).isTrue();
        verifyNoInteractions(profissionalRepository);
    }

    @Test
    void realizarAula_comProfissionalId_sobrescreveProfissionalAtribuido() {
        Aula aula = aulaPendente();
        aula.setProfissional(profissionalAtivo(1L, "Paula Mendes"));
        Profissional substituto = profissionalAtivo(2L, "Carla Souza");

        when(aulaRepository.findByIdAndPacienteAtivoTrue(1L)).thenReturn(Optional.of(aula));
        when(profissionalRepository.findById(2L)).thenReturn(Optional.of(substituto));

        var response = service.realizarAula(1L, 2L);

        assertThat(aula.getProfissional()).isEqualTo(substituto);
        assertThat(response.profissionalId()).isEqualTo(2L);
        assertThat(response.realizada()).isTrue();
    }

    @Test
    void atribuirProfissional_vinculaProfissionalSemRealizarAula() {
        Aula aula = aulaPendente();
        Profissional profissional = profissionalAtivo(1L, "Paula Mendes");

        when(aulaRepository.findByIdAndPacienteAtivoTrue(1L)).thenReturn(Optional.of(aula));
        when(profissionalRepository.findById(1L)).thenReturn(Optional.of(profissional));

        var response = service.atribuirProfissional(1L, 1L);

        assertThat(aula.getProfissional()).isEqualTo(profissional);
        assertThat(response.profissionalId()).isEqualTo(1L);
        assertThat(response.profissionalNome()).isEqualTo("Paula Mendes");
        assertThat(response.realizada()).isFalse();
    }

    @Test
    void atribuirProfissional_comIdNulo_desvinculaProfissional() {
        Aula aula = aulaPendente();
        aula.setProfissional(profissionalAtivo(1L, "Paula Mendes"));

        when(aulaRepository.findByIdAndPacienteAtivoTrue(1L)).thenReturn(Optional.of(aula));

        var response = service.atribuirProfissional(1L, null);

        assertThat(aula.getProfissional()).isNull();
        assertThat(response.profissionalId()).isNull();
        assertThat(response.profissionalNome()).isNull();
        verifyNoInteractions(profissionalRepository);
    }

    @Test
    void atribuirProfissional_aulaRealizada_lancaConflictSemAlterarVinculo() {
        Profissional atribuido = profissionalAtivo(1L, "Paula Mendes");
        Aula aula = aulaPendente();
        aula.setProfissional(atribuido);
        aula.setRealizada(true);

        when(aulaRepository.findByIdAndPacienteAtivoTrue(1L)).thenReturn(Optional.of(aula));

        assertThatThrownBy(() -> service.atribuirProfissional(1L, 2L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Aula já realizada");
        assertThat(aula.getProfissional()).isEqualTo(atribuido);
        verifyNoInteractions(profissionalRepository);
    }

    @Test
    void atribuirProfissional_profissionalNaoEncontrado_lancaResourceNotFound() {
        when(aulaRepository.findByIdAndPacienteAtivoTrue(1L)).thenReturn(Optional.of(aulaPendente()));
        when(profissionalRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.atribuirProfissional(1L, 99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Profissional não encontrado: 99");
    }

    @Test
    void atribuirProfissional_profissionalInativo_lancaExcecao() {
        Profissional profissional = profissionalAtivo(2L, "Carla Souza");
        profissional.setAtivo(false);

        when(aulaRepository.findByIdAndPacienteAtivoTrue(1L)).thenReturn(Optional.of(aulaPendente()));
        when(profissionalRepository.findById(2L)).thenReturn(Optional.of(profissional));

        assertThatThrownBy(() -> service.atribuirProfissional(1L, 2L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Profissional inativo");
    }

    @Test
    void atribuirProfissional_pacienteInativo_lancaResourceNotFound() {
        when(aulaRepository.findByIdAndPacienteAtivoTrue(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.atribuirProfissional(1L, 1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Aula não encontrada: 1");
    }

    @Test
    void remarcar_alteraDataPreservandoPacienteEPagamento() {
        Aula aula = aulaPendente();
        LocalDate novaData = LocalDate.of(2025, 2, 20);

        when(aulaRepository.findByIdAndPacienteAtivoTrue(1L)).thenReturn(Optional.of(aula));
        when(aulaRepository.existsByPacienteAndData(paciente, novaData)).thenReturn(false);
        when(aulaRepository.saveAndFlush(aula)).thenReturn(aula);

        var response = service.remarcar(1L, novaData, null);

        assertThat(aula.getData()).isEqualTo(novaData);
        assertThat(response.data()).isEqualTo(novaData);
        assertThat(aula.getPagamento()).isEqualTo(pagamentoPago);
        assertThat(aula.getPaciente()).isEqualTo(paciente);
        assertThat(response.realizada()).isFalse();
    }

    @Test
    void remarcar_paraForaDoPeriodoDoPagamento_eAceitoMantendoOPagamento() {
        Aula aula = aulaPendente();
        // pagamentoPago cobre 01/02 a 28/02: a decisão de negócio da #170 é não confrontar a nova data com o período.
        LocalDate foraDoPeriodo = LocalDate.of(2025, 3, 15);

        when(aulaRepository.findByIdAndPacienteAtivoTrue(1L)).thenReturn(Optional.of(aula));
        when(aulaRepository.existsByPacienteAndData(paciente, foraDoPeriodo)).thenReturn(false);
        when(aulaRepository.saveAndFlush(aula)).thenReturn(aula);

        var response = service.remarcar(1L, foraDoPeriodo, null);

        assertThat(response.data()).isEqualTo(foraDoPeriodo);
        assertThat(aula.getPagamento()).isEqualTo(pagamentoPago);
    }

    @Test
    void remarcar_paraDataPassada_eAceito() {
        Aula aula = aulaPendente();
        LocalDate passado = LocalDate.of(2020, 1, 6);

        when(aulaRepository.findByIdAndPacienteAtivoTrue(1L)).thenReturn(Optional.of(aula));
        when(aulaRepository.existsByPacienteAndData(paciente, passado)).thenReturn(false);
        when(aulaRepository.saveAndFlush(aula)).thenReturn(aula);

        assertThat(service.remarcar(1L, passado, null).data()).isEqualTo(passado);
    }

    @Test
    void remarcar_paraAMesmaData_naoChecaColisaoNemPersiste() {
        Aula aula = aulaPendente();

        when(aulaRepository.findByIdAndPacienteAtivoTrue(1L)).thenReturn(Optional.of(aula));

        var response = service.remarcar(1L, LocalDate.of(2025, 2, 3), null);

        assertThat(response.data()).isEqualTo(LocalDate.of(2025, 2, 3));
        verify(aulaRepository, never()).existsByPacienteAndData(any(), any());
        verify(aulaRepository, never()).saveAndFlush(any());
    }

    @Test
    void remarcar_aulaRealizada_lancaConflictSemAlterarData() {
        Aula aula = aulaPendente();
        aula.setRealizada(true);

        when(aulaRepository.findByIdAndPacienteAtivoTrue(1L)).thenReturn(Optional.of(aula));

        assertThatThrownBy(() -> service.remarcar(1L, LocalDate.of(2025, 2, 20), null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Aula já realizada");
        assertThat(aula.getData()).isEqualTo(LocalDate.of(2025, 2, 3));
        verify(aulaRepository, never()).saveAndFlush(any());
    }

    @Test
    void remarcar_pacienteJaTemAulaNaData_lancaConflictSemAlterarData() {
        Aula aula = aulaPendente();
        LocalDate ocupada = LocalDate.of(2025, 2, 5);

        when(aulaRepository.findByIdAndPacienteAtivoTrue(1L)).thenReturn(Optional.of(aula));
        when(aulaRepository.existsByPacienteAndData(paciente, ocupada)).thenReturn(true);

        assertThatThrownBy(() -> service.remarcar(1L, ocupada, null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Paciente já possui aula na data 2025-02-05");
        assertThat(aula.getData()).isEqualTo(LocalDate.of(2025, 2, 3));
        verify(aulaRepository, never()).saveAndFlush(any());
    }

    @Test
    void remarcar_violacaoDeConstraintNoFlush_viraConflictEmVezDeErroDeBanco() {
        Aula aula = aulaPendente();
        LocalDate novaData = LocalDate.of(2025, 2, 5);

        when(aulaRepository.findByIdAndPacienteAtivoTrue(1L)).thenReturn(Optional.of(aula));
        // Concorrência: outra transação gravou a mesma (paciente, data) entre o exists e o flush.
        when(aulaRepository.existsByPacienteAndData(paciente, novaData)).thenReturn(false);
        when(aulaRepository.saveAndFlush(aula)).thenThrow(new DataIntegrityViolationException("violação unique"));

        assertThatThrownBy(() -> service.remarcar(1L, novaData, null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Paciente já possui aula na data 2025-02-05");
    }

    @Test
    void remarcar_pacienteInativo_lancaResourceNotFound() {
        when(aulaRepository.findByIdAndPacienteAtivoTrue(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.remarcar(1L, LocalDate.of(2025, 2, 20), null))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Aula não encontrada: 1");
    }

    @Test
    void gerarAulas_copiaOHorarioDoPlanoParaCadaAula() {
        plano.setHorario(LocalTime.of(8, 0));
        when(aulaRepository.existsByPacienteAndData(any(), any())).thenReturn(false);
        when(aulaRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        List<Aula> aulas = service.gerarAulas(pagamentoPago);

        assertThat(aulas).hasSize(8).allSatisfy(aula -> assertThat(aula.getHorario())
                .isEqualTo(LocalTime.of(8, 0)));
        // Aula gerada nasce sem profissional: não há agenda de ninguém para conferir.
        verify(aulaRepository, never()).countConcorrentesDoProfissional(any(), any(), any(), any(), any());
    }

    @Test
    void gerarAulas_planoSemHorario_geraAulasSemHorario() {
        when(aulaRepository.existsByPacienteAndData(any(), any())).thenReturn(false);
        when(aulaRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.gerarAulas(pagamentoPago))
                .allSatisfy(aula -> assertThat(aula.getHorario()).isNull());
    }

    @Test
    void atribuirProfissional_comVaga_contaAsAulasDentroDaDuracaoEVincula() {
        Aula aula = aulaPendente();
        aula.setHorario(LocalTime.of(8, 0));
        Profissional profissional = profissionalAtivo(1L, "Paula Mendes");

        when(aulaRepository.findByIdAndPacienteAtivoTrue(1L)).thenReturn(Optional.of(aula));
        when(profissionalRepository.findById(1L)).thenReturn(Optional.of(profissional));
        when(aulaRepository.countConcorrentesDoProfissional(
                        1L, LocalDate.of(2025, 2, 3), LocalTime.of(7, 0), LocalTime.of(9, 0), null))
                .thenReturn(2L);

        service.atribuirProfissional(1L, 1L);

        assertThat(aula.getProfissional()).isEqualTo(profissional);
        verify(profissionalRepository).findByIdForUpdate(1L);
    }

    @Test
    void atribuirProfissional_semVagaNoHorario_lancaConflictSemAlterarVinculo() {
        Aula aula = aulaPendente();
        aula.setHorario(LocalTime.of(8, 0));

        when(aulaRepository.findByIdAndPacienteAtivoTrue(1L)).thenReturn(Optional.of(aula));
        when(profissionalRepository.findById(1L)).thenReturn(Optional.of(profissionalAtivo(1L, "Paula Mendes")));
        when(aulaRepository.countConcorrentesDoProfissional(anyLong(), any(), any(), any(), any()))
                .thenReturn(3L);

        assertThatThrownBy(() -> service.atribuirProfissional(1L, 1L))
                .isInstanceOf(ConflictException.class)
                .hasMessage(
                        "Profissional Paula Mendes já atende 3 aula(s) em 2025-02-03 às 08:00; a capacidade por horário é 3");
        assertThat(aula.getProfissional()).isNull();
    }

    @Test
    void atribuirProfissional_aulaSemHorario_naoConfereCapacidade() {
        when(aulaRepository.findByIdAndPacienteAtivoTrue(1L)).thenReturn(Optional.of(aulaPendente()));
        when(profissionalRepository.findById(1L)).thenReturn(Optional.of(profissionalAtivo(1L, "Paula Mendes")));

        service.atribuirProfissional(1L, 1L);

        verify(aulaRepository, never()).countConcorrentesDoProfissional(any(), any(), any(), any(), any());
        verify(profissionalRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void atribuirProfissional_pertoDaMeiaNoite_abreAJanelaDoLadoQueDariaAVolta() {
        Aula aula = aulaPendente();
        aula.setHorario(LocalTime.of(0, 30));

        when(aulaRepository.findByIdAndPacienteAtivoTrue(1L)).thenReturn(Optional.of(aula));
        when(profissionalRepository.findById(1L)).thenReturn(Optional.of(profissionalAtivo(1L, "Paula Mendes")));

        service.atribuirProfissional(1L, 1L);

        // 00:30 - 60min daria 23:30 do dia anterior; o limite inferior vai nulo em vez de excluir a madrugada.
        verify(aulaRepository).countConcorrentesDoProfissional(eq(1L), any(), isNull(), eq(LocalTime.of(1, 30)), any());
    }

    @Test
    void realizarAula_comProfissional_naoConfereCapacidade() {
        Aula aula = aulaPendente();
        aula.setHorario(LocalTime.of(8, 0));

        when(aulaRepository.findByIdAndPacienteAtivoTrue(1L)).thenReturn(Optional.of(aula));
        when(profissionalRepository.findById(1L)).thenReturn(Optional.of(profissionalAtivo(1L, "Paula Mendes")));

        service.realizarAula(1L, 1L);

        assertThat(aula.isRealizada()).isTrue();
        verify(aulaRepository, never()).countConcorrentesDoProfissional(any(), any(), any(), any(), any());
    }

    @Test
    void remarcar_soOHorarioNoMesmoDia_gravaSemConferirColisaoDoPaciente() {
        Aula aula = aulaPendente();
        aula.setHorario(LocalTime.of(8, 0));

        when(aulaRepository.findByIdAndPacienteAtivoTrue(1L)).thenReturn(Optional.of(aula));
        when(aulaRepository.saveAndFlush(aula)).thenReturn(aula);

        var response = service.remarcar(1L, LocalDate.of(2025, 2, 3), LocalTime.of(18, 0));

        assertThat(response.data()).isEqualTo(LocalDate.of(2025, 2, 3));
        assertThat(response.horario()).isEqualTo(LocalTime.of(18, 0));
        verify(aulaRepository, never()).existsByPacienteAndData(any(), any());
    }

    @Test
    void remarcar_semHorario_preservaOHorarioAtual() {
        Aula aula = aulaPendente();
        aula.setHorario(LocalTime.of(8, 0));
        LocalDate novaData = LocalDate.of(2025, 2, 20);

        when(aulaRepository.findByIdAndPacienteAtivoTrue(1L)).thenReturn(Optional.of(aula));
        when(aulaRepository.existsByPacienteAndData(paciente, novaData)).thenReturn(false);
        when(aulaRepository.saveAndFlush(aula)).thenReturn(aula);

        var response = service.remarcar(1L, novaData, null);

        assertThat(response.data()).isEqualTo(novaData);
        assertThat(response.horario()).isEqualTo(LocalTime.of(8, 0));
    }

    @Test
    void remarcar_paraAMesmaDataEOMesmoHorario_eNoOp() {
        Aula aula = aulaPendente();
        aula.setHorario(LocalTime.of(8, 0));

        when(aulaRepository.findByIdAndPacienteAtivoTrue(1L)).thenReturn(Optional.of(aula));

        service.remarcar(1L, LocalDate.of(2025, 2, 3), LocalTime.of(8, 0));

        verify(aulaRepository, never()).saveAndFlush(any());
        verify(aulaRepository, never()).countConcorrentesDoProfissional(any(), any(), any(), any(), any());
    }

    @Test
    void remarcar_comProfissionalSemVagaNoNovoHorario_lancaConflictSemAlterar() {
        Aula aula = aulaPendente();
        aula.setHorario(LocalTime.of(8, 0));
        aula.setProfissional(profissionalAtivo(1L, "Paula Mendes"));

        when(aulaRepository.findByIdAndPacienteAtivoTrue(1L)).thenReturn(Optional.of(aula));
        when(aulaRepository.countConcorrentesDoProfissional(
                        1L, LocalDate.of(2025, 2, 3), LocalTime.of(17, 0), LocalTime.of(19, 0), null))
                .thenReturn(3L);

        assertThatThrownBy(() -> service.remarcar(1L, LocalDate.of(2025, 2, 3), LocalTime.of(18, 0)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("capacidade por horário é 3");
        assertThat(aula.getHorario()).isEqualTo(LocalTime.of(8, 0));
        verify(aulaRepository, never()).saveAndFlush(any());
    }

    private Aula aulaPendente() {
        Aula aula = new Aula();
        aula.setPaciente(paciente);
        aula.setPagamento(pagamentoPago);
        aula.setData(LocalDate.of(2025, 2, 3));
        return aula;
    }

    private Profissional profissionalAtivo(Long id, String nome) {
        Profissional profissional = new Profissional();
        profissional.setId(id);
        profissional.setNome(nome);
        profissional.setTipoContrato(TipoContrato.PJ);
        profissional.setPercentualPagamentoAula(new BigDecimal("45.00"));
        profissional.setDataInicio(LocalDate.of(2024, 1, 15));
        return profissional;
    }

    private void assertReadOnly(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = AulaService.class.getMethod(methodName, parameterTypes);
        Transactional transactional = method.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isTrue();
    }
}
