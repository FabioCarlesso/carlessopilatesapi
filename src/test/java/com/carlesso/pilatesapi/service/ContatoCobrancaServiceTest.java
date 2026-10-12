package com.carlesso.pilatesapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.carlesso.pilatesapi.dto.ContatoCobrancaRequestDTO;
import com.carlesso.pilatesapi.dto.ContatoCobrancaResponseDTO;
import com.carlesso.pilatesapi.entity.ContatoCobranca;
import com.carlesso.pilatesapi.entity.Pagamento;
import com.carlesso.pilatesapi.entity.enums.StatusPagamento;
import com.carlesso.pilatesapi.exception.BusinessException;
import com.carlesso.pilatesapi.exception.ResourceNotFoundException;
import com.carlesso.pilatesapi.repository.ContatoCobrancaRepository;
import com.carlesso.pilatesapi.repository.PagamentoRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContatoCobrancaServiceTest {

    @Mock
    ContatoCobrancaRepository repository;

    @Mock
    PagamentoRepository pagamentoRepository;

    ContatoCobrancaService service;

    private final ContatoCobrancaRequestDTO dto =
            new ContatoCobrancaRequestDTO(LocalDate.of(2026, 2, 15), "  Ligação; promete pagar sexta  ");

    /** 2026-02-16 01:30 UTC: ainda é dia 15 às 22:30 no estúdio. */
    private final Clock relogio = Clock.fixed(Instant.parse("2026-02-16T01:30:00Z"), ZoneId.of("America/Sao_Paulo"));

    @BeforeEach
    void setUp() {
        service = new ContatoCobrancaService(repository, pagamentoRepository, relogio);
    }

    @Test
    void registrar_preencheAutorDoServidorENormalizaObservacao() {
        Pagamento pagamento = pagamento(StatusPagamento.VENCIDO);
        when(pagamentoRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(pagamento));
        when(repository.save(any(ContatoCobranca.class))).thenAnswer(inv -> inv.getArgument(0));

        ContatoCobrancaResponseDTO response = service.registrar(1L, dto, "admin@email.com");

        ArgumentCaptor<ContatoCobranca> captor = ArgumentCaptor.forClass(ContatoCobranca.class);
        verify(repository).save(captor.capture());
        ContatoCobranca salvo = captor.getValue();
        assertThat(salvo.getPagamento()).isSameAs(pagamento);
        assertThat(salvo.getRegistradoPor()).isEqualTo("admin@email.com");
        assertThat(salvo.getObservacao()).isEqualTo("Ligação; promete pagar sexta");
        assertThat(salvo.getDataContato()).isEqualTo(LocalDate.of(2026, 2, 15));
        assertThat(salvo.getRegistradoEm()).isEqualTo(LocalDateTime.of(2026, 2, 15, 22, 30));
        assertThat(response.registradoPor()).isEqualTo("admin@email.com");
    }

    @Test
    void registrar_dataFuturaNoFusoDoEstudio_lancaIllegalArgumentSemConsultar() {
        // Em UTC já é dia 16, mas no estúdio ainda é 15: o dia 16 é futuro.
        var futuro = new ContatoCobrancaRequestDTO(LocalDate.of(2026, 2, 16), "Ligação");

        assertThatThrownBy(() -> service.registrar(1L, futuro, "admin@email.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("dataContato não pode ser uma data futura");

        verifyNoInteractions(pagamentoRepository, repository);
    }

    @Test
    void registrar_emPagamentoPendente_eAceito() {
        when(pagamentoRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(pagamento(StatusPagamento.PENDENTE)));
        when(repository.save(any(ContatoCobranca.class))).thenAnswer(inv -> inv.getArgument(0));

        service.registrar(1L, dto, "admin@email.com");

        verify(repository).save(any(ContatoCobranca.class));
    }

    @Test
    void registrar_emPagamentoPago_lancaBusinessExceptionSemPersistir() {
        when(pagamentoRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(pagamento(StatusPagamento.PAGO)));

        assertThatThrownBy(() -> service.registrar(1L, dto, "admin@email.com")).isInstanceOf(BusinessException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void registrar_pagamentoInexistente_lancaNotFound() {
        when(pagamentoRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.registrar(99L, dto, "admin@email.com"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void listar_pagamentoInexistente_lancaNotFound() {
        when(pagamentoRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.listar(99L)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void listar_retornaHistoricoNaOrdemDoRepositorio() {
        Pagamento pagamento = pagamento(StatusPagamento.VENCIDO);
        when(pagamentoRepository.existsById(1L)).thenReturn(true);
        when(repository.findByPagamentoIdOrderByDataContatoDescRegistradoEmDescIdDesc(1L))
                .thenReturn(List.of(contato(pagamento, "Recente"), contato(pagamento, "Antigo")));

        assertThat(service.listar(1L))
                .extracting(ContatoCobrancaResponseDTO::observacao)
                .containsExactly("Recente", "Antigo");
    }

    @Test
    void buscarPorId_deOutroPagamento_lancaNotFound() {
        when(repository.findByIdAndPagamentoId(5L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscarPorId(1L, 5L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void buscarPorId_doPagamento_retornaContato() {
        Pagamento pagamento = pagamento(StatusPagamento.VENCIDO);
        when(repository.findByIdAndPagamentoId(5L, 1L)).thenReturn(Optional.of(contato(pagamento, "Ligação")));

        assertThat(service.buscarPorId(1L, 5L).observacao()).isEqualTo("Ligação");
    }

    private Pagamento pagamento(StatusPagamento status) {
        Pagamento pagamento = new Pagamento();
        pagamento.setStatus(status);
        return pagamento;
    }

    private ContatoCobranca contato(Pagamento pagamento, String observacao) {
        ContatoCobranca contato = new ContatoCobranca();
        contato.setPagamento(pagamento);
        contato.setDataContato(LocalDate.of(2026, 2, 15));
        contato.setObservacao(observacao);
        contato.setRegistradoPor("admin@email.com");
        return contato;
    }
}
