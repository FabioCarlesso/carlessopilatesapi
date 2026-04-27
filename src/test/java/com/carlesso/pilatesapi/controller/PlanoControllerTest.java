package com.carlesso.pilatesapi.controller;

import com.carlesso.pilatesapi.dto.PlanoRequestDTO;
import com.carlesso.pilatesapi.dto.PlanoResponseDTO;
import com.carlesso.pilatesapi.entity.enums.FrequenciaSemanal;
import com.carlesso.pilatesapi.entity.enums.TipoPagamento;
import com.carlesso.pilatesapi.exception.BusinessException;
import com.carlesso.pilatesapi.exception.ResourceNotFoundException;
import com.carlesso.pilatesapi.service.CustomUserDetailsService;
import com.carlesso.pilatesapi.service.JwtService;
import com.carlesso.pilatesapi.service.PlanoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PlanoController.class)
@AutoConfigureMockMvc(addFilters = false)
class PlanoControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean PlanoService planoService;
    @MockitoBean JwtService jwtService;
    @MockitoBean CustomUserDetailsService customUserDetailsService;

    private PlanoResponseDTO planoResponse() {
        return new PlanoResponseDTO(1L, 1L, "Ana", TipoPagamento.MENSAL, new BigDecimal("200.00"),
                FrequenciaSemanal.DUAS_VEZES, List.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
                LocalDate.now(), true);
    }

    @Test
    void criar_retorna201() throws Exception {
        var dto = new PlanoRequestDTO(1L, TipoPagamento.MENSAL, new BigDecimal("200.00"),
                FrequenciaSemanal.DUAS_VEZES, List.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), LocalDate.now());

        when(planoService.criar(any())).thenReturn(planoResponse());

        mockMvc.perform(post("/planos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("MENSAL"))
                .andExpect(jsonPath("$.frequenciaSemanal").value("DUAS_VEZES"));
    }

    @Test
    void criar_semPacienteId_retorna400() throws Exception {
        var dto = new PlanoRequestDTO(null, TipoPagamento.MENSAL, new BigDecimal("200.00"),
                FrequenciaSemanal.UMA_VEZ, List.of(DayOfWeek.MONDAY), null);

        mockMvc.perform(post("/planos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void criar_pacienteInativo_retorna422() throws Exception {
        var dto = new PlanoRequestDTO(1L, TipoPagamento.MENSAL, new BigDecimal("200.00"),
                FrequenciaSemanal.UMA_VEZ, List.of(DayOfWeek.MONDAY), null);

        when(planoService.criar(any())).thenThrow(new BusinessException("Paciente inativo"));

        mockMvc.perform(post("/planos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.erro").value("Paciente inativo"));
    }

    @Test
    void criar_frequenciaIncompativel_retorna400() throws Exception {
        var dto = new PlanoRequestDTO(1L, TipoPagamento.MENSAL, new BigDecimal("200.00"),
                FrequenciaSemanal.DUAS_VEZES, List.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), null);

        when(planoService.criar(any())).thenThrow(new IllegalArgumentException("incompatível"));

        mockMvc.perform(post("/planos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("incompatível"));
    }

    @Test
    void buscar_encontrado_retorna200() throws Exception {
        when(planoService.buscarPorId(1L)).thenReturn(planoResponse());

        mockMvc.perform(get("/planos/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void buscar_naoEncontrado_retorna404() throws Exception {
        when(planoService.buscarPorId(99L)).thenThrow(new ResourceNotFoundException("Plano não encontrado: 99"));

        mockMvc.perform(get("/planos/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.erro").value("Plano não encontrado: 99"));
    }

    @Test
    void listarPorPaciente_retorna200() throws Exception {
        when(planoService.listarPorPaciente(1L)).thenReturn(List.of(planoResponse()));

        mockMvc.perform(get("/planos/paciente/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void buscarAtivoPorPaciente_comPlano_retorna200() throws Exception {
        when(planoService.buscarAtivoPorPaciente(1L)).thenReturn(Optional.of(planoResponse()));

        mockMvc.perform(get("/planos/paciente/1/ativo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(true));
    }

    @Test
    void buscarAtivoPorPaciente_semPlano_retorna204() throws Exception {
        when(planoService.buscarAtivoPorPaciente(1L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/planos/paciente/1/ativo"))
                .andExpect(status().isNoContent());
    }

    @Test
    void inativar_comSucesso_retorna204() throws Exception {
        doNothing().when(planoService).inativar(1L);

        mockMvc.perform(delete("/planos/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void inativar_naoEncontrado_retorna404() throws Exception {
        doThrow(new ResourceNotFoundException("Plano não encontrado: 99")).when(planoService).inativar(99L);

        mockMvc.perform(delete("/planos/99"))
                .andExpect(status().isNotFound());
    }
}
