package com.carlesso.pilatesapi.controller;

import com.carlesso.pilatesapi.dto.AnamneseRequestDTO;
import com.carlesso.pilatesapi.dto.AnamneseResponseDTO;
import com.carlesso.pilatesapi.dto.AnamneseUpdateDTO;
import com.carlesso.pilatesapi.exception.ConflictException;
import com.carlesso.pilatesapi.exception.ResourceNotFoundException;
import com.carlesso.pilatesapi.service.AnamneseService;
import com.carlesso.pilatesapi.service.CustomUserDetailsService;
import com.carlesso.pilatesapi.service.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AnamneseController.class)
@AutoConfigureMockMvc(addFilters = false)
class AnamneseControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AnamneseService service;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @Autowired
    private ObjectMapper mapper;

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private AnamneseResponseDTO responseDTO() {
        return new AnamneseResponseDTO(
                1L, 1L, "Maria Souza",
                "Dor lombar", "Hipertensão", "Nenhuma", "Entorse tornozelo",
                "Losartana", "Nenhuma", "Sedentário", "Evitar impacto",
                "Melhorar postura e reduzir dores", "Paciente relata estresse",
                LocalDateTime.of(2026, 1, 15, 10, 0), null
        );
    }

    private AnamneseRequestDTO requestDTO() {
        return new AnamneseRequestDTO(
                1L, "Dor lombar", "Hipertensão", "Nenhuma", "Entorse tornozelo",
                "Losartana", "Nenhuma", "Sedentário", "Evitar impacto",
                "Melhorar postura e reduzir dores", "Paciente relata estresse"
        );
    }

    // -------------------------------------------------------------------------
    // POST /anamneses
    // -------------------------------------------------------------------------

    @Test
    void criar_comDadosValidos_deveRetornar201ComHeaderLocation() throws Exception {
        when(service.criar(any())).thenReturn(responseDTO());

        mvc.perform(post("/anamneses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(requestDTO())))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.pacienteId").value(1))
                .andExpect(jsonPath("$.nomePaciente").value("Maria Souza"))
                .andExpect(jsonPath("$.queixaPrincipal").value("Dor lombar"));
    }

    @Test
    void criar_semQueixaPrincipal_deveRetornar400() throws Exception {
        var dto = new AnamneseRequestDTO(1L, null, null, null, null, null, null, null, null, "Objetivo", null);

        mvc.perform(post("/anamneses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void criar_semObjetivos_deveRetornar400() throws Exception {
        var dto = new AnamneseRequestDTO(1L, "Dor", null, null, null, null, null, null, null, null, null);

        mvc.perform(post("/anamneses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void criar_semPacienteId_deveRetornar400() throws Exception {
        var dto = new AnamneseRequestDTO(null, "Dor", null, null, null, null, null, null, null, "Objetivo", null);

        mvc.perform(post("/anamneses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void criar_comPacienteInexistente_deveRetornar404() throws Exception {
        when(service.criar(any())).thenThrow(new ResourceNotFoundException("Paciente não encontrado: 99"));

        mvc.perform(post("/anamneses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(requestDTO())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.erro").value("Paciente não encontrado: 99"));
    }

    @Test
    void criar_quandoPacienteJaPossuiAnamnese_deveRetornar409() throws Exception {
        when(service.criar(any())).thenThrow(new ConflictException("Paciente já possui anamnese cadastrada: 1"));

        mvc.perform(post("/anamneses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(requestDTO())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erro").value("Paciente já possui anamnese cadastrada: 1"));
    }

    // -------------------------------------------------------------------------
    // GET /anamneses/{id}
    // -------------------------------------------------------------------------

    @Test
    void buscarPorId_quandoExistente_deveRetornar200() throws Exception {
        when(service.buscarPorId(1L)).thenReturn(responseDTO());

        mvc.perform(get("/anamneses/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.queixaPrincipal").value("Dor lombar"))
                .andExpect(jsonPath("$.objetivos").value("Melhorar postura e reduzir dores"));
    }

    @Test
    void buscarPorId_quandoNaoExistente_deveRetornar404() throws Exception {
        when(service.buscarPorId(99L)).thenThrow(new ResourceNotFoundException("Anamnese não encontrada: 99"));

        mvc.perform(get("/anamneses/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.erro").value("Anamnese não encontrada: 99"));
    }

    // -------------------------------------------------------------------------
    // GET /anamneses/paciente/{pacienteId}
    // -------------------------------------------------------------------------

    @Test
    void buscarPorPaciente_quandoExistente_deveRetornar200() throws Exception {
        when(service.buscarPorPaciente(1L)).thenReturn(responseDTO());

        mvc.perform(get("/anamneses/paciente/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pacienteId").value(1))
                .andExpect(jsonPath("$.nomePaciente").value("Maria Souza"));
    }

    @Test
    void buscarPorPaciente_quandoNaoExistente_deveRetornar404() throws Exception {
        when(service.buscarPorPaciente(99L)).thenThrow(new ResourceNotFoundException("Paciente não encontrado: 99"));

        mvc.perform(get("/anamneses/paciente/99"))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------------------
    // PUT /anamneses/{id}
    // -------------------------------------------------------------------------

    @Test
    void atualizar_deveRetornar200ComDadosAtualizados() throws Exception {
        var updated = new AnamneseResponseDTO(
                1L, 1L, "Maria Souza",
                "Nova queixa", "Hipertensão", "Nenhuma", "Entorse tornozelo",
                "Losartana", "Nenhuma", "Sedentário", "Evitar impacto",
                "Melhorar postura e reduzir dores", "Paciente relata estresse",
                LocalDateTime.of(2026, 1, 15, 10, 0), LocalDateTime.of(2026, 3, 10, 9, 0)
        );
        when(service.atualizar(eq(1L), any())).thenReturn(updated);

        var dto = new AnamneseUpdateDTO("Nova queixa", null, null, null, null, null, null, null, null, null);

        mvc.perform(put("/anamneses/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queixaPrincipal").value("Nova queixa"))
                .andExpect(jsonPath("$.dataAtualizacao").isNotEmpty());
    }

    @Test
    void atualizar_quandoNaoExistente_deveRetornar404() throws Exception {
        when(service.atualizar(eq(99L), any()))
                .thenThrow(new ResourceNotFoundException("Anamnese não encontrada: 99"));

        var dto = new AnamneseUpdateDTO("Queixa", null, null, null, null, null, null, null, null, null);

        mvc.perform(put("/anamneses/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(dto)))
                .andExpect(status().isNotFound());
    }
}
