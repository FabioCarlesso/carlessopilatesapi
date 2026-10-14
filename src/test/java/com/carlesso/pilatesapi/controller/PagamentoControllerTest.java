package com.carlesso.pilatesapi.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.carlesso.pilatesapi.dto.ContatoCobrancaResponseDTO;
import com.carlesso.pilatesapi.dto.PagamentoRequestDTO;
import com.carlesso.pilatesapi.dto.PagamentoResponseDTO;
import com.carlesso.pilatesapi.entity.enums.StatusPagamento;
import com.carlesso.pilatesapi.exception.BusinessException;
import com.carlesso.pilatesapi.exception.ConflictException;
import com.carlesso.pilatesapi.exception.ResourceNotFoundException;
import com.carlesso.pilatesapi.service.ContatoCobrancaService;
import com.carlesso.pilatesapi.service.CustomUserDetailsService;
import com.carlesso.pilatesapi.service.JwtService;
import com.carlesso.pilatesapi.service.PagamentoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PagamentoController.class)
@AutoConfigureMockMvc(addFilters = false)
class PagamentoControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    PagamentoService pagamentoService;

    @MockitoBean
    ContatoCobrancaService contatoCobrancaService;

    @MockitoBean
    JwtService jwtService;

    @MockitoBean
    CustomUserDetailsService customUserDetailsService;

    private PagamentoResponseDTO pagamentoPendente() {
        return new PagamentoResponseDTO(
                1L,
                1L,
                "Ana",
                1L,
                new BigDecimal("200.00"),
                StatusPagamento.PENDENTE,
                null,
                LocalDate.now().plusDays(10),
                LocalDate.now(),
                LocalDate.now().plusMonths(1).minusDays(1));
    }

    private PagamentoResponseDTO pagamentoPago() {
        return new PagamentoResponseDTO(
                1L,
                1L,
                "Ana",
                1L,
                new BigDecimal("200.00"),
                StatusPagamento.PAGO,
                LocalDate.now(),
                LocalDate.now().plusDays(10),
                LocalDate.now(),
                LocalDate.now().plusMonths(1).minusDays(1));
    }

    @Test
    void criar_retorna201() throws Exception {
        var dto = new PagamentoRequestDTO(
                1L, 1L, new BigDecimal("200.00"), LocalDate.now().plusDays(10), LocalDate.now());

        when(pagamentoService.criar(any())).thenReturn(pagamentoPendente());

        mockMvc.perform(post("/pagamentos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDENTE"));
    }

    @Test
    void criar_semPacienteId_retorna400() throws Exception {
        var dto = new PagamentoRequestDTO(
                null, 1L, new BigDecimal("200.00"), LocalDate.now().plusDays(10), LocalDate.now());

        mockMvc.perform(post("/pagamentos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void criar_pacienteInativo_retorna422() throws Exception {
        var dto = new PagamentoRequestDTO(
                1L, 1L, new BigDecimal("200.00"), LocalDate.now().plusDays(10), LocalDate.now());

        when(pagamentoService.criar(any()))
                .thenThrow(new BusinessException("Paciente inativo não pode receber novas cobranças"));

        mockMvc.perform(post("/pagamentos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    void criar_pagamentoDuplicado_retorna409() throws Exception {
        var dto = new PagamentoRequestDTO(
                1L, 1L, new BigDecimal("200.00"), LocalDate.now().plusDays(10), LocalDate.now());

        when(pagamentoService.criar(any()))
                .thenThrow(new ConflictException("Já existe um pagamento para este plano no período"));

        mockMvc.perform(post("/pagamentos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    void criar_valorMenorQuePlano_retorna400() throws Exception {
        var dto = new PagamentoRequestDTO(
                1L, 1L, new BigDecimal("100.00"), LocalDate.now().plusDays(10), LocalDate.now());

        when(pagamentoService.criar(any())).thenThrow(new IllegalArgumentException("menor que o valor do plano"));

        mockMvc.perform(post("/pagamentos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    void buscar_encontrado_retorna200() throws Exception {
        when(pagamentoService.buscarPorId(1L)).thenReturn(pagamentoPendente());

        mockMvc.perform(get("/pagamentos/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valor").value(200.00));
    }

    @Test
    void buscar_naoEncontrado_retorna404() throws Exception {
        when(pagamentoService.buscarPorId(99L))
                .thenThrow(new ResourceNotFoundException("Pagamento não encontrado: 99"));

        mockMvc.perform(get("/pagamentos/99")).andExpect(status().isNotFound());
    }

    @Test
    void listarPorPaciente_retorna200() throws Exception {
        when(pagamentoService.listarPorPaciente(1L)).thenReturn(List.of(pagamentoPendente()));

        mockMvc.perform(get("/pagamentos/paciente/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void pagar_retorna200ComStatusPago() throws Exception {
        when(pagamentoService.pagar(eq(1L), isNull())).thenReturn(pagamentoPago());

        mockMvc.perform(patch("/pagamentos/1/pagar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAGO"));

        verify(pagamentoService).pagar(1L, null);
    }

    @Test
    void pagar_comDataPagamentoNoBody_repassaDataParaService() throws Exception {
        LocalDate dataPagamento = LocalDate.of(2025, 2, 10);
        when(pagamentoService.pagar(eq(1L), eq(dataPagamento))).thenReturn(pagamentoPago());

        mockMvc.perform(
                        patch("/pagamentos/1/pagar")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"dataPagamento":"2025-02-10"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAGO"));

        verify(pagamentoService).pagar(1L, dataPagamento);
    }

    @Test
    void pagar_jaConfirmado_retorna409() throws Exception {
        when(pagamentoService.pagar(eq(1L), isNull())).thenThrow(new ConflictException("Pagamento já foi confirmado"));

        mockMvc.perform(patch("/pagamentos/1/pagar"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    void listar_semParametros_usaOrdenacaoPadraoPorVencimento() throws Exception {
        when(pagamentoService.listar(isNull(), isNull(), isNull(), isNull(), any()))
                .thenReturn(new PageImpl<>(List.of(pagamentoPendente()), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/pagamentos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].pacienteNome").value("Ana"))
                .andExpect(jsonPath("$.page.totalElements").value(1));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(pagamentoService).listar(isNull(), isNull(), isNull(), isNull(), captor.capture());
        assertSort(captor.getValue(), Sort.by("dataVencimento"));
    }

    @Test
    void listar_comFiltros_repassaStatusRepetidosEDatas() throws Exception {
        when(pagamentoService.listar(any(), any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/pagamentos")
                        .param("status", "PENDENTE", "VENCIDO")
                        .param("vencimentoDe", "2026-01-01")
                        .param("vencimentoAte", "2026-01-31")
                        .param("pacienteId", "7")
                        .param("page", "1")
                        .param("size", "20")
                        .param("sort", "valor,desc"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(pagamentoService)
                .listar(
                        eq(List.of(StatusPagamento.PENDENTE, StatusPagamento.VENCIDO)),
                        eq(LocalDate.of(2026, 1, 1)),
                        eq(LocalDate.of(2026, 1, 31)),
                        eq(7L),
                        captor.capture());
        assertSort(captor.getValue(), Sort.by(Sort.Direction.DESC, "valor"));
    }

    @Test
    void listar_statusInvalido_retorna400() throws Exception {
        mockMvc.perform(get("/pagamentos").param("status", "ATRASADO"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    void listar_faixaInvertida_retorna400() throws Exception {
        when(pagamentoService.listar(any(), any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("vencimentoDe não pode ser posterior a vencimentoAte"));

        mockMvc.perform(get("/pagamentos").param("vencimentoDe", "2026-02-01").param("vencimentoAte", "2026-01-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("vencimentoDe não pode ser posterior a vencimentoAte"));
    }

    @Test
    void registrarCobranca_retorna201ComLocationEAutorAutenticado() throws Exception {
        when(contatoCobrancaService.registrar(eq(1L), any(), eq("admin@email.com")))
                .thenReturn(contato());

        mockMvc.perform(
                        post("/pagamentos/1/cobrancas")
                                .principal(adminAuth())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"dataContato": "2026-02-15", "observacao": "Ligação",
                                 "registradoPor": "forjado@email.com"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/pagamentos/1/cobrancas/5"))
                .andExpect(jsonPath("$.registradoPor").value("admin@email.com"));

        verify(contatoCobrancaService).registrar(eq(1L), any(), eq("admin@email.com"));
    }

    @Test
    void registrarCobranca_dataFutura_retorna400() throws Exception {
        mockMvc.perform(post("/pagamentos/1/cobrancas")
                        .principal(adminAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"dataContato": "%s", "observacao": "Ligação"}
                                """
                                        .formatted(LocalDate.now().plusDays(1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[?(@.campo == 'dataContato')].mensagem")
                        .value("dataContato não pode ser uma data futura"));
    }

    @Test
    void registrarCobranca_observacaoVaziaOuLonga_retorna400() throws Exception {
        mockMvc.perform(
                        post("/pagamentos/1/cobrancas")
                                .principal(adminAuth())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"dataContato": "2026-02-15", "observacao": "   "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[?(@.campo == 'observacao')].mensagem")
                        .exists());

        mockMvc.perform(post("/pagamentos/1/cobrancas")
                        .principal(adminAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"dataContato": "2026-02-15", "observacao": "%s"}
                                """
                                        .formatted("x".repeat(501))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[?(@.campo == 'observacao')].mensagem")
                        .value("observacao deve ter no máximo 500 caracteres"));
    }

    @Test
    void registrarCobranca_pagamentoPago_retorna422() throws Exception {
        when(contatoCobrancaService.registrar(eq(1L), any(), any()))
                .thenThrow(new BusinessException("Pagamento já confirmado não aceita novos contatos de cobrança"));

        mockMvc.perform(
                        post("/pagamentos/1/cobrancas")
                                .principal(adminAuth())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"dataContato": "2026-02-15", "observacao": "Ligação"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    void listarCobrancas_retorna200() throws Exception {
        when(contatoCobrancaService.listar(1L)).thenReturn(List.of(contato()));

        mockMvc.perform(get("/pagamentos/1/cobrancas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(5))
                .andExpect(jsonPath("$[0].dataContato").value("2026-02-15"));
    }

    @Test
    void listarCobrancas_pagamentoInexistente_retorna404() throws Exception {
        when(contatoCobrancaService.listar(99L))
                .thenThrow(new ResourceNotFoundException("Pagamento não encontrado: 99"));

        mockMvc.perform(get("/pagamentos/99/cobrancas")).andExpect(status().isNotFound());
    }

    @Test
    void buscarCobranca_retorna200() throws Exception {
        when(contatoCobrancaService.buscarPorId(1L, 5L)).thenReturn(contato());

        mockMvc.perform(get("/pagamentos/1/cobrancas/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagamentoId").value(1));
    }

    private ContatoCobrancaResponseDTO contato() {
        return new ContatoCobrancaResponseDTO(
                5L, 1L, LocalDate.of(2026, 2, 15), "Ligação", "admin@email.com", LocalDateTime.of(2026, 2, 15, 10, 0));
    }

    private UsernamePasswordAuthenticationToken adminAuth() {
        return new UsernamePasswordAuthenticationToken(
                "admin@email.com", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private void assertSort(Pageable pageable, Sort esperado) {
        assertThat(pageable.getSort()).isEqualTo(esperado);
    }
}
