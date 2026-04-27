package com.carlesso.pilatesapi.controller;

import com.carlesso.pilatesapi.dto.AulaResponseDTO;
import com.carlesso.pilatesapi.exception.BusinessException;
import com.carlesso.pilatesapi.exception.ConflictException;
import com.carlesso.pilatesapi.exception.ResourceNotFoundException;
import com.carlesso.pilatesapi.service.AulaService;
import com.carlesso.pilatesapi.service.CustomUserDetailsService;
import com.carlesso.pilatesapi.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AulaController.class)
@AutoConfigureMockMvc(addFilters = false)
class AulaControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean AulaService aulaService;
    @MockitoBean JwtService jwtService;
    @MockitoBean CustomUserDetailsService customUserDetailsService;

    private AulaResponseDTO aulaResponse(boolean realizada) {
        return new AulaResponseDTO(1L, 1L, "Ana", 1L, LocalDate.of(2025, 2, 3), realizada);
    }

    @Test
    void buscar_encontrada_retorna200() throws Exception {
        when(aulaService.buscarPorId(1L)).thenReturn(aulaResponse(false));

        mockMvc.perform(get("/aulas/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.realizada").value(false))
                .andExpect(jsonPath("$.data").value("2025-02-03"));
    }

    @Test
    void buscar_naoEncontrada_retorna404() throws Exception {
        when(aulaService.buscarPorId(99L))
                .thenThrow(new ResourceNotFoundException("Aula não encontrada: 99"));

        mockMvc.perform(get("/aulas/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.erro").value("Aula não encontrada: 99"));
    }

    @Test
    void listarPorPaciente_retorna200() throws Exception {
        when(aulaService.buscarPorPaciente(1L)).thenReturn(List.of(aulaResponse(false), aulaResponse(true)));

        mockMvc.perform(get("/aulas/paciente/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void listarPorPagamento_retorna200() throws Exception {
        when(aulaService.buscarPorPagamento(1L)).thenReturn(List.of(aulaResponse(false)));

        mockMvc.perform(get("/aulas/pagamento/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void realizar_retorna200ComRealizadaTrue() throws Exception {
        when(aulaService.realizarAula(eq(1L), isNull())).thenReturn(aulaResponse(true));

        mockMvc.perform(patch("/aulas/1/realizar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.realizada").value(true));
    }

    @Test
    void realizar_comProfissional_retorna200ComRealizadaTrue() throws Exception {
        when(aulaService.realizarAula(1L, 2L)).thenReturn(aulaResponse(true));

        mockMvc.perform(patch("/aulas/1/realizar").param("profissionalId", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.realizada").value(true));
    }

    @Test
    void realizar_comProfissionalInexistente_retorna404() throws Exception {
        when(aulaService.realizarAula(eq(1L), eq(99L)))
                .thenThrow(new ResourceNotFoundException("Profissional não encontrado: 99"));

        mockMvc.perform(patch("/aulas/1/realizar").param("profissionalId", "99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void realizar_aaulaJaRealizada_retorna409() throws Exception {
        when(aulaService.realizarAula(eq(1L), isNull()))
                .thenThrow(new ConflictException("Aula já foi marcada como realizada"));

        mockMvc.perform(patch("/aulas/1/realizar"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erro").exists());
    }

    @Test
    void realizar_profissionalInativo_retorna422() throws Exception {
        when(aulaService.realizarAula(eq(1L), eq(2L)))
                .thenThrow(new BusinessException("Profissional inativo não pode ser vinculado à aula"));

        mockMvc.perform(patch("/aulas/1/realizar").param("profissionalId", "2"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.erro").exists());
    }

    @Test
    void realizar_naoEncontrada_retorna404() throws Exception {
        when(aulaService.realizarAula(eq(99L), isNull()))
                .thenThrow(new ResourceNotFoundException("Aula não encontrada: 99"));

        mockMvc.perform(patch("/aulas/99/realizar"))
                .andExpect(status().isNotFound());
    }
}
