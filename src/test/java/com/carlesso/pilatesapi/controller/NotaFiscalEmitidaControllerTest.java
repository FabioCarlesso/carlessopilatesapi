package com.carlesso.pilatesapi.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carlesso.pilatesapi.dto.NotaFiscalEmitidaResponseDTO;
import com.carlesso.pilatesapi.service.CustomUserDetailsService;
import com.carlesso.pilatesapi.service.JwtService;
import com.carlesso.pilatesapi.service.NotaFiscalEmitidaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(NotaFiscalEmitidaController.class)
@AutoConfigureMockMvc(addFilters = false)
class NotaFiscalEmitidaControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    NotaFiscalEmitidaService service;

    @MockitoBean
    JwtService jwtService;

    @MockitoBean
    CustomUserDetailsService customUserDetailsService;

    @Test
    void registrar_deveRetornarNota() throws Exception {
        when(service.registrar(any())).thenReturn(item());

        var body = Map.of(
                "pacienteId", 1,
                "competencia", "04/2026",
                "numeroNota", "NF-123",
                "dataEmissao", "2026-04-15",
                "valor", 250.00);

        mockMvc.perform(post("/api/nfse-emitidas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.pacienteId").value(1))
                .andExpect(jsonPath("$.competencia").value("04/2026"))
                .andExpect(jsonPath("$.numeroNota").value("NF-123"));
    }

    @Test
    void registrar_competenciaInvalida_deveRetornar400() throws Exception {
        var body = Map.of(
                "pacienteId", 1,
                "competencia", "13/2026",
                "dataEmissao", "2026-04-15");

        mockMvc.perform(post("/api/nfse-emitidas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registrar_semPacienteId_deveRetornar400() throws Exception {
        var body = Map.of(
                "competencia", "04/2026",
                "dataEmissao", "2026-04-15");

        mockMvc.perform(post("/api/nfse-emitidas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listarPorPaciente_deveRetornarLista() throws Exception {
        when(service.listarPorPaciente(1L)).thenReturn(List.of(item()));

        mockMvc.perform(get("/api/nfse-emitidas/paciente/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(5))
                .andExpect(jsonPath("$[0].competencia").value("04/2026"));
    }

    private NotaFiscalEmitidaResponseDTO item() {
        return new NotaFiscalEmitidaResponseDTO(
                5L,
                1L,
                "Ana Souza",
                "04/2026",
                "NF-123",
                LocalDate.of(2026, 4, 15),
                new BigDecimal("250.00"),
                "ok",
                LocalDateTime.of(2026, 4, 15, 10, 0),
                null);
    }
}
