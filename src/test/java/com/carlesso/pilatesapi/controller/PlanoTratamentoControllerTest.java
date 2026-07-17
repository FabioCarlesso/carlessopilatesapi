package com.carlesso.pilatesapi.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.carlesso.pilatesapi.dto.PlanoTratamentoRequestDTO;
import com.carlesso.pilatesapi.dto.PlanoTratamentoResponseDTO;
import com.carlesso.pilatesapi.dto.PlanoTratamentoUpdateDTO;
import com.carlesso.pilatesapi.exception.ResourceNotFoundException;
import com.carlesso.pilatesapi.service.CustomUserDetailsService;
import com.carlesso.pilatesapi.service.JwtService;
import com.carlesso.pilatesapi.service.PlanoTratamentoService;
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

@WebMvcTest(PlanoTratamentoController.class)
@AutoConfigureMockMvc(addFilters = false)
class PlanoTratamentoControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PlanoTratamentoService service;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @Autowired
    private ObjectMapper mapper;

    private PlanoTratamentoResponseDTO responseDTO() {
        return new PlanoTratamentoResponseDTO(
                1L,
                1L,
                "Maria Souza",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 8, 1),
                "Reduzir dor lombar e melhorar mobilidade",
                "Exercícios de core e alongamentos",
                24,
                "2x por semana",
                "Dr. João",
                "Paciente deve evitar impacto",
                LocalDateTime.of(2026, 5, 1, 10, 0),
                null);
    }

    private PlanoTratamentoRequestDTO requestDTO() {
        return new PlanoTratamentoRequestDTO(
                1L,
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 8, 1),
                "Reduzir dor lombar e melhorar mobilidade",
                "Exercícios de core e alongamentos",
                24,
                "2x por semana",
                "Dr. João",
                "Paciente deve evitar impacto");
    }

    @Test
    void criar_comDadosValidos_deveRetornar201ComHeaderLocation() throws Exception {
        when(service.criar(any())).thenReturn(responseDTO());

        mvc.perform(post("/planos-tratamento")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(requestDTO())))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.pacienteId").value(1))
                .andExpect(jsonPath("$.nomePaciente").value("Maria Souza"))
                .andExpect(jsonPath("$.objetivosTratamento").value("Reduzir dor lombar e melhorar mobilidade"))
                .andExpect(jsonPath("$.numeroSessoesPrevistas").value(24));
    }

    @Test
    void criar_semPacienteId_deveRetornar400() throws Exception {
        var dto = new PlanoTratamentoRequestDTO(
                null, LocalDate.of(2026, 5, 1), null, "Objetivos", null, null, null, null, null);

        mvc.perform(post("/planos-tratamento")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void criar_semDataInicio_deveRetornar400() throws Exception {
        var dto = new PlanoTratamentoRequestDTO(1L, null, null, "Objetivos", null, null, null, null, null);

        mvc.perform(post("/planos-tratamento")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void criar_semObjetivosTratamento_deveRetornar400() throws Exception {
        var dto = new PlanoTratamentoRequestDTO(1L, LocalDate.of(2026, 5, 1), null, null, null, null, null, null, null);

        mvc.perform(post("/planos-tratamento")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void criar_comNumeroSessaoNegativo_deveRetornar400() throws Exception {
        var dto = new PlanoTratamentoRequestDTO(
                1L, LocalDate.of(2026, 5, 1), null, "Objetivos", null, -1, null, null, null);

        mvc.perform(post("/planos-tratamento")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void criar_comDataFimAnteriorADataInicio_deveRetornar400() throws Exception {
        when(service.criar(any()))
                .thenThrow(new IllegalArgumentException("dataFimPrevista não pode ser anterior a dataInicio"));

        var dto = new PlanoTratamentoRequestDTO(
                1L, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 5, 1), "Objetivos", null, null, null, null, null);

        mvc.perform(post("/planos-tratamento")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("dataFimPrevista não pode ser anterior a dataInicio"));
    }

    @Test
    void criar_comPacienteInexistente_deveRetornar404() throws Exception {
        when(service.criar(any())).thenThrow(new ResourceNotFoundException("Paciente não encontrado: 99"));

        mvc.perform(post("/planos-tratamento")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(requestDTO())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.erro").value("Paciente não encontrado: 99"));
    }

    @Test
    void buscarPorId_quandoExistente_deveRetornar200() throws Exception {
        when(service.buscarPorId(1L)).thenReturn(responseDTO());

        mvc.perform(get("/planos-tratamento/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.frequenciaSessoes").value("2x por semana"));
    }

    @Test
    void buscarPorId_quandoNaoExistente_deveRetornar404() throws Exception {
        when(service.buscarPorId(99L))
                .thenThrow(new ResourceNotFoundException("Plano de tratamento não encontrado: 99"));

        mvc.perform(get("/planos-tratamento/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.erro").value("Plano de tratamento não encontrado: 99"));
    }

    @Test
    void listarPorPaciente_deveRetornar200ComPlanos() throws Exception {
        when(service.listarPorPaciente(1L)).thenReturn(List.of(responseDTO()));

        mvc.perform(get("/planos-tratamento/paciente/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].pacienteId").value(1))
                .andExpect(jsonPath("$[0].dataInicio").value("2026-05-01"));
    }

    @Test
    void listarPorPaciente_comPacienteInexistente_deveRetornar404() throws Exception {
        when(service.listarPorPaciente(99L)).thenThrow(new ResourceNotFoundException("Paciente não encontrado: 99"));

        mvc.perform(get("/planos-tratamento/paciente/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.erro").value("Paciente não encontrado: 99"));
    }

    @Test
    void atualizar_deveRetornar200ComDadosAtualizados() throws Exception {
        var updated = new PlanoTratamentoResponseDTO(
                1L,
                1L,
                "Maria Souza",
                LocalDate.of(2026, 5, 10),
                LocalDate.of(2026, 9, 1),
                "Objetivos atualizados",
                "Exercícios de core e alongamentos",
                30,
                "2x por semana",
                "Dr. João",
                "Evoluindo bem",
                LocalDateTime.of(2026, 5, 1, 10, 0),
                LocalDateTime.of(2026, 5, 10, 9, 0));
        when(service.atualizar(eq(1L), any())).thenReturn(updated);

        var dto = new PlanoTratamentoUpdateDTO(
                LocalDate.of(2026, 5, 10),
                LocalDate.of(2026, 9, 1),
                "Objetivos atualizados",
                null,
                30,
                null,
                null,
                "Evoluindo bem");

        mvc.perform(put("/planos-tratamento/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataInicio").value("2026-05-10"))
                .andExpect(jsonPath("$.numeroSessoesPrevistas").value(30))
                .andExpect(jsonPath("$.dataAtualizacao").isNotEmpty());
    }

    @Test
    void atualizar_comObjetivosEmBranco_deveRetornar400() throws Exception {
        var dto = new PlanoTratamentoUpdateDTO(null, null, "   ", null, null, null, null, null);

        mvc.perform(put("/planos-tratamento/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void atualizar_comNumeroSessaoNegativo_deveRetornar400() throws Exception {
        var dto = new PlanoTratamentoUpdateDTO(null, null, null, null, -5, null, null, null);

        mvc.perform(put("/planos-tratamento/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void atualizar_comDataFimAnteriorADataInicio_deveRetornar400() throws Exception {
        when(service.atualizar(eq(1L), any()))
                .thenThrow(new IllegalArgumentException("dataFimPrevista não pode ser anterior a dataInicio"));

        var dto = new PlanoTratamentoUpdateDTO(
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 5, 1), null, null, null, null, null, null);

        mvc.perform(put("/planos-tratamento/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("dataFimPrevista não pode ser anterior a dataInicio"));
    }

    @Test
    void atualizar_comPlanoInexistente_deveRetornar404() throws Exception {
        when(service.atualizar(eq(99L), any()))
                .thenThrow(new ResourceNotFoundException("Plano de tratamento não encontrado: 99"));

        var dto = new PlanoTratamentoUpdateDTO(LocalDate.of(2026, 5, 10), null, null, null, null, null, null, null);

        mvc.perform(put("/planos-tratamento/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(dto)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.erro").value("Plano de tratamento não encontrado: 99"));
    }

    @Test
    void inativar_comPlanoExistente_deveRetornar204() throws Exception {
        mvc.perform(delete("/planos-tratamento/1")).andExpect(status().isNoContent());
    }

    @Test
    void inativar_comPlanoInexistente_deveRetornar404() throws Exception {
        org.mockito.Mockito.doThrow(new ResourceNotFoundException("Plano de tratamento não encontrado: 99"))
                .when(service)
                .inativar(99L);

        mvc.perform(delete("/planos-tratamento/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.erro").value("Plano de tratamento não encontrado: 99"));
    }
}
