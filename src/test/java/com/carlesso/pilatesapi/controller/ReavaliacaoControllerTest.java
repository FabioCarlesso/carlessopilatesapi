package com.carlesso.pilatesapi.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.carlesso.pilatesapi.dto.ReavaliacaoRequestDTO;
import com.carlesso.pilatesapi.dto.ReavaliacaoResponseDTO;
import com.carlesso.pilatesapi.dto.ReavaliacaoUpdateDTO;
import com.carlesso.pilatesapi.exception.ResourceNotFoundException;
import com.carlesso.pilatesapi.service.CustomUserDetailsService;
import com.carlesso.pilatesapi.service.JwtService;
import com.carlesso.pilatesapi.service.ReavaliacaoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ReavaliacaoController.class)
@AutoConfigureMockMvc(addFilters = false)
class ReavaliacaoControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ReavaliacaoService service;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @Autowired
    private ObjectMapper mapper;

    private ReavaliacaoResponseDTO responseDTO() {
        return new ReavaliacaoResponseDTO(
                1L,
                1L,
                "Carlos Silva",
                null,
                null,
                LocalDate.of(2026, 5, 1),
                "Melhora geral observada",
                "Dor reduziu de 7 para 4",
                "Ganho de força nos extensores",
                "Amplitude de quadril aumentou 15°",
                "Consegue subir escadas sem dor",
                "Retorno às atividades diárias",
                "Ainda apresenta dor ao agachar",
                "Aumentar carga nos exercícios de glúteo",
                "Paciente motivado com a evolução",
                LocalDateTime.of(2026, 5, 1, 10, 0),
                null);
    }

    private ReavaliacaoRequestDTO requestDTO() {
        return new ReavaliacaoRequestDTO(
                1L,
                null,
                null,
                LocalDate.of(2026, 5, 1),
                "Melhora geral observada",
                "Dor reduziu de 7 para 4",
                "Ganho de força nos extensores",
                "Amplitude de quadril aumentou 15°",
                "Consegue subir escadas sem dor",
                "Retorno às atividades diárias",
                "Ainda apresenta dor ao agachar",
                "Aumentar carga nos exercícios de glúteo",
                "Paciente motivado com a evolução");
    }

    @Test
    void criar_comDadosValidos_deveRetornar201ComHeaderLocation() throws Exception {
        when(service.criar(any())).thenReturn(responseDTO());

        mvc.perform(post("/reavaliacoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(requestDTO())))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.pacienteId").value(1))
                .andExpect(jsonPath("$.nomePaciente").value("Carlos Silva"))
                .andExpect(jsonPath("$.dataReavaliacao").value("2026-05-01"));
    }

    @Test
    void criar_semPacienteId_deveRetornar400() throws Exception {
        var dto = new ReavaliacaoRequestDTO(
                null, null, null, LocalDate.of(2026, 5, 1), null, null, null, null, null, null, null, null, null);

        mvc.perform(post("/reavaliacoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void criar_semDataReavaliacao_deveRetornar400() throws Exception {
        var dto = new ReavaliacaoRequestDTO(1L, null, null, null, null, null, null, null, null, null, null, null, null);

        mvc.perform(post("/reavaliacoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void criar_comPacienteInexistente_deveRetornar404() throws Exception {
        when(service.criar(any())).thenThrow(new ResourceNotFoundException("Paciente não encontrado: 99"));

        mvc.perform(post("/reavaliacoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(requestDTO())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.erro").value("Paciente não encontrado: 99"));
    }

    @Test
    void buscarPorId_quandoExistente_deveRetornar200() throws Exception {
        when(service.buscarPorId(1L)).thenReturn(responseDTO());

        mvc.perform(get("/reavaliacoes/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.evolucaoDor").value("Dor reduziu de 7 para 4"));
    }

    @Test
    void buscarPorId_quandoNaoExistente_deveRetornar404() throws Exception {
        when(service.buscarPorId(99L)).thenThrow(new ResourceNotFoundException("Reavaliação não encontrada: 99"));

        mvc.perform(get("/reavaliacoes/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.erro").value("Reavaliação não encontrada: 99"));
    }

    @Test
    void listarPorPaciente_deveRetornar200ComReavaliacoes() throws Exception {
        when(service.listarPorPaciente(1L)).thenReturn(List.of(responseDTO()));

        mvc.perform(get("/reavaliacoes/paciente/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].pacienteId").value(1))
                .andExpect(jsonPath("$[0].dataReavaliacao").value("2026-05-01"));
    }

    @Test
    void atualizar_deveRetornar200ComDadosAtualizados() throws Exception {
        var updated = new ReavaliacaoResponseDTO(
                1L,
                1L,
                "Carlos Silva",
                null,
                null,
                LocalDate.of(2026, 5, 10),
                "Evolução excelente",
                "Sem dor",
                "Ganho de força nos extensores",
                "Amplitude de quadril aumentou 15°",
                "Consegue subir escadas sem dor",
                "Retorno às atividades diárias",
                "Ainda apresenta dor ao agachar",
                "Aumentar carga nos exercícios de glúteo",
                "Paciente motivado com a evolução",
                LocalDateTime.of(2026, 5, 1, 10, 0),
                LocalDateTime.of(2026, 5, 10, 9, 0));
        when(service.atualizar(eq(1L), any())).thenReturn(updated);

        var dto = new ReavaliacaoUpdateDTO(
                LocalDate.of(2026, 5, 10), "Evolução excelente", "Sem dor", null, null, null, null, null, null, null);

        mvc.perform(put("/reavaliacoes/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataReavaliacao").value("2026-05-10"))
                .andExpect(jsonPath("$.evolucaoDor").value("Sem dor"))
                .andExpect(jsonPath("$.dataAtualizacao").isNotEmpty());
    }

    @Test
    void atualizar_comReavaliacaoInexistente_deveRetornar404() throws Exception {
        when(service.atualizar(eq(99L), any()))
                .thenThrow(new ResourceNotFoundException("Reavaliação não encontrada: 99"));

        var dto = new ReavaliacaoUpdateDTO(
                LocalDate.of(2026, 5, 10), null, null, null, null, null, null, null, null, null);

        mvc.perform(put("/reavaliacoes/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(dto)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.erro").value("Reavaliação não encontrada: 99"));
    }
}
