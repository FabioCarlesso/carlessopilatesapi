package com.carlesso.pilatesapi.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.carlesso.pilatesapi.dto.PagamentoRequestDTO;
import com.carlesso.pilatesapi.dto.PagamentoResponseDTO;
import com.carlesso.pilatesapi.entity.enums.StatusPagamento;
import com.carlesso.pilatesapi.exception.BusinessException;
import com.carlesso.pilatesapi.exception.ConflictException;
import com.carlesso.pilatesapi.exception.ResourceNotFoundException;
import com.carlesso.pilatesapi.service.CustomUserDetailsService;
import com.carlesso.pilatesapi.service.JwtService;
import com.carlesso.pilatesapi.service.PagamentoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
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
                .andExpect(jsonPath("$.erro").exists());
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
                .andExpect(jsonPath("$.erro").exists());
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
                .andExpect(jsonPath("$.erro").exists());
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
                .andExpect(jsonPath("$.erro").exists());
    }
}
