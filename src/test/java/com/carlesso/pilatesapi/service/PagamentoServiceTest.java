package com.carlesso.pilatesapi.service;

import com.carlesso.pilatesapi.config.AppProperties;
import com.carlesso.pilatesapi.dto.PagamentoRequestDTO;
import com.carlesso.pilatesapi.dto.PagamentoResponseDTO;
import com.carlesso.pilatesapi.entity.Paciente;
import com.carlesso.pilatesapi.entity.Pagamento;
import com.carlesso.pilatesapi.entity.Plano;
import com.carlesso.pilatesapi.entity.enums.FrequenciaSemanal;
import com.carlesso.pilatesapi.entity.enums.StatusPagamento;
import com.carlesso.pilatesapi.entity.enums.TipoPagamento;
import com.carlesso.pilatesapi.exception.BusinessException;
import com.carlesso.pilatesapi.exception.ConflictException;
import com.carlesso.pilatesapi.exception.ResourceNotFoundException;
import com.carlesso.pilatesapi.repository.PacienteRepository;
import com.carlesso.pilatesapi.repository.PagamentoRepository;
import com.carlesso.pilatesapi.repository.PlanoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PagamentoServiceTest {

    @Mock PagamentoRepository pagamentoRepository;
    @Mock PacienteRepository pacienteRepository;
    @Mock PlanoRepository planoRepository;
    @Mock AulaService aulaService;
    PagamentoService service;

    private Paciente paciente;
    private Plano plano;
    private AppProperties appProperties;

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties(
                new AppProperties.Cobranca("0 0 6 * * *", "0 0 7 * * *", 10));
        service = new PagamentoService(pagamentoRepository, pacienteRepository,
                planoRepository, aulaService, appProperties);

        paciente = new Paciente();
        paciente.setNome("Ana");
        paciente.setEmail("ana@email.com");
        paciente.setCpf("111.222.333-44");

        plano = new Plano();
        plano.setPaciente(paciente);
        plano.setTipo(TipoPagamento.MENSAL);
        plano.setValor(new BigDecimal("200.00"));
        plano.setFrequenciaSemanal(FrequenciaSemanal.DUAS_VEZES);
        plano.setDiasSemana(List.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY));
        plano.setDataInicio(LocalDate.now());
    }

    @Test
    void criarPagamento_comSucesso() {
        var dto = new PagamentoRequestDTO(1L, 1L, new BigDecimal("200.00"),
                LocalDate.now().plusDays(10), LocalDate.now());

        when(pacienteRepository.findById(1L)).thenReturn(Optional.of(paciente));
        when(planoRepository.findById(1L)).thenReturn(Optional.of(plano));
        when(pagamentoRepository.existsByPlanoAndPeriodoInicio(plano, dto.periodoInicio())).thenReturn(false);

        Pagamento salvo = new Pagamento();
        salvo.setPaciente(paciente);
        salvo.setPlano(plano);
        salvo.setValor(dto.valor());
        salvo.setDataVencimento(dto.dataVencimento());
        salvo.setPeriodoInicio(dto.periodoInicio());
        salvo.setPeriodoFim(dto.periodoInicio().plusMonths(1).minusDays(1));
        when(pagamentoRepository.save(any())).thenReturn(salvo);

        PagamentoResponseDTO response = service.criar(dto);

        assertThat(response.status()).isEqualTo(StatusPagamento.PENDENTE);
        assertThat(response.valor()).isEqualByComparingTo(new BigDecimal("200.00"));
    }

    @Test
    void criarPagamento_pacienteInativo_lancaExcecao() {
        paciente.setAtivo(false);
        var dto = new PagamentoRequestDTO(1L, 1L, new BigDecimal("200.00"),
                LocalDate.now().plusDays(10), LocalDate.now());

        when(pacienteRepository.findById(1L)).thenReturn(Optional.of(paciente));

        assertThatThrownBy(() -> service.criar(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("inativo");
    }

    @Test
    void criarPagamento_valorMenorQuePlano_lancaExcecao() {
        var dto = new PagamentoRequestDTO(1L, 1L, new BigDecimal("100.00"),
                LocalDate.now().plusDays(10), LocalDate.now());

        when(pacienteRepository.findById(1L)).thenReturn(Optional.of(paciente));
        when(planoRepository.findById(1L)).thenReturn(Optional.of(plano));

        assertThatThrownBy(() -> service.criar(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("menor que o valor do plano");
    }

    @Test
    void criarPagamento_duplicadoParaMesmoPeriodo_lancaExcecao() {
        var dto = new PagamentoRequestDTO(1L, 1L, new BigDecimal("200.00"),
                LocalDate.now().plusDays(10), LocalDate.now());

        when(pacienteRepository.findById(1L)).thenReturn(Optional.of(paciente));
        when(planoRepository.findById(1L)).thenReturn(Optional.of(plano));
        when(pagamentoRepository.existsByPlanoAndPeriodoInicio(plano, dto.periodoInicio())).thenReturn(true);

        assertThatThrownBy(() -> service.criar(dto))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Já existe um pagamento");
    }

    @Test
    void criarPagamento_pacienteNaoEncontrado_lancaExcecao() {
        var dto = new PagamentoRequestDTO(99L, 1L, new BigDecimal("200.00"),
                LocalDate.now().plusDays(10), LocalDate.now());

        when(pacienteRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.criar(dto))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void pagar_registraPagamentoEGeraAulas() {
        Pagamento pagamento = new Pagamento();
        pagamento.setPaciente(paciente);
        pagamento.setPlano(plano);
        pagamento.setValor(new BigDecimal("200.00"));
        pagamento.setStatus(StatusPagamento.PENDENTE);
        pagamento.setDataVencimento(LocalDate.now().plusDays(5));
        pagamento.setPeriodoInicio(LocalDate.now());
        pagamento.setPeriodoFim(LocalDate.now().plusMonths(1).minusDays(1));

        when(pagamentoRepository.findById(1L)).thenReturn(Optional.of(pagamento));
        when(pagamentoRepository.save(any())).thenReturn(pagamento);

        PagamentoResponseDTO response = service.pagar(1L, LocalDate.now());

        assertThat(response.status()).isEqualTo(StatusPagamento.PAGO);
        verify(aulaService).gerarAulas(pagamento);
    }

    @Test
    void pagar_pagamentoJaConfirmado_lancaExcecao() {
        Pagamento pagamento = new Pagamento();
        pagamento.setPaciente(paciente);
        pagamento.setPlano(plano);
        pagamento.setValor(new BigDecimal("200.00"));
        pagamento.setStatus(StatusPagamento.PAGO);
        pagamento.setDataVencimento(LocalDate.now());
        pagamento.setPeriodoInicio(LocalDate.now());
        pagamento.setPeriodoFim(LocalDate.now().plusMonths(1).minusDays(1));

        when(pagamentoRepository.findById(1L)).thenReturn(Optional.of(pagamento));

        assertThatThrownBy(() -> service.pagar(1L, null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("já foi confirmado");
    }

    @Test
    void gerarCobrancasFuturas_usaVencimentoDiasConfigurado() {
        appProperties = new AppProperties(
                new AppProperties.Cobranca("0 0 6 * * *", "0 0 7 * * *", 15));
        service = new PagamentoService(pagamentoRepository, pacienteRepository,
                planoRepository, aulaService, appProperties);

        when(planoRepository.findByAtivoTrue()).thenReturn(List.of(plano));
        when(pagamentoRepository.findTopByPlanoOrderByPeriodoFimDesc(plano)).thenReturn(Optional.empty());

        ArgumentCaptor<Pagamento> captor = ArgumentCaptor.forClass(Pagamento.class);
        when(pagamentoRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        int total = service.gerarCobrancasFuturas();

        assertThat(total).isEqualTo(1);
        Pagamento gerado = captor.getValue();
        assertThat(gerado.getDataVencimento())
                .isEqualTo(gerado.getPeriodoInicio().plusDays(15));
    }

    @Test
    void atualizarVencidos_marcaPagamentosExpirados() {
        Pagamento p1 = new Pagamento();
        p1.setPaciente(paciente);
        p1.setPlano(plano);
        p1.setValor(new BigDecimal("200.00"));
        p1.setStatus(StatusPagamento.PENDENTE);
        p1.setDataVencimento(LocalDate.now().minusDays(1));
        p1.setPeriodoInicio(LocalDate.now().minusMonths(1));
        p1.setPeriodoFim(LocalDate.now().minusDays(1));

        when(pagamentoRepository.findByStatusAndDataVencimentoBefore(eq(StatusPagamento.PENDENTE), any()))
                .thenReturn(List.of(p1));

        int total = service.atualizarVencidos();

        assertThat(total).isEqualTo(1);
        assertThat(p1.getStatus()).isEqualTo(StatusPagamento.VENCIDO);
    }
}
