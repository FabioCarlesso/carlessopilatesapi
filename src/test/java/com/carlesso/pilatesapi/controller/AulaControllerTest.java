package com.carlesso.pilatesapi.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.carlesso.pilatesapi.dto.AulaProfissionalRequestDTO;
import com.carlesso.pilatesapi.dto.AulaResponseDTO;
import com.carlesso.pilatesapi.exception.BusinessException;
import com.carlesso.pilatesapi.exception.ConflictException;
import com.carlesso.pilatesapi.exception.ResourceNotFoundException;
import com.carlesso.pilatesapi.service.AulaService;
import com.carlesso.pilatesapi.service.CustomUserDetailsService;
import com.carlesso.pilatesapi.service.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(AulaController.class)
@AutoConfigureMockMvc(addFilters = false)
class AulaControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    AulaService aulaService;

    @MockitoBean
    JwtService jwtService;

    @MockitoBean
    CustomUserDetailsService customUserDetailsService;

    private AulaResponseDTO aulaResponse(boolean realizada) {
        return new AulaResponseDTO(1L, 1L, "Ana", 7L, "Paula", 1L, LocalDate.of(2025, 2, 3), realizada);
    }

    @Test
    void listarPorPeriodo_retorna200ComProfissionalNoPayload() throws Exception {
        when(aulaService.listarPorPeriodo(LocalDate.of(2025, 2, 1), LocalDate.of(2025, 2, 28), null, null, null))
                .thenReturn(List.of(aulaResponse(true)));

        mockMvc.perform(get("/aulas").param("inicio", "2025-02-01").param("fim", "2025-02-28"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].profissionalId").value(7))
                .andExpect(jsonPath("$[0].profissionalNome").value("Paula"));
    }

    @Test
    void listarPorPeriodo_comFiltrosOpcionais_repassaTodosAoService() throws Exception {
        when(aulaService.listarPorPeriodo(LocalDate.of(2025, 2, 1), LocalDate.of(2025, 2, 28), 7L, 1L, true))
                .thenReturn(List.of(aulaResponse(true)));

        mockMvc.perform(get("/aulas")
                        .param("inicio", "2025-02-01")
                        .param("fim", "2025-02-28")
                        .param("profissionalId", "7")
                        .param("pacienteId", "1")
                        .param("realizada", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void listarPorPeriodo_semRegistros_retorna200ComListaVazia() throws Exception {
        when(aulaService.listarPorPeriodo(LocalDate.of(2025, 2, 1), LocalDate.of(2025, 2, 28), null, null, null))
                .thenReturn(List.of());

        mockMvc.perform(get("/aulas").param("inicio", "2025-02-01").param("fim", "2025-02-28"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void listarPorPeriodo_semPeriodo_retorna400() throws Exception {
        mockMvc.perform(get("/aulas").param("inicio", "2025-02-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").exists());
    }

    @Test
    void listarPorPeriodo_inicioPosteriorAoFim_retorna400() throws Exception {
        when(aulaService.listarPorPeriodo(LocalDate.of(2025, 3, 1), LocalDate.of(2025, 2, 1), null, null, null))
                .thenThrow(new IllegalArgumentException("Período inicial não pode ser maior que o período final"));

        mockMvc.perform(get("/aulas").param("inicio", "2025-03-01").param("fim", "2025-02-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Período inicial não pode ser maior que o período final"));
    }

    @Test
    void listarPorPeriodo_acimaDoLimiteDeDias_retorna400() throws Exception {
        when(aulaService.listarPorPeriodo(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31), null, null, null))
                .thenThrow(new IllegalArgumentException("Consulta por período limitada a 92 dias"));

        mockMvc.perform(get("/aulas").param("inicio", "2025-01-01").param("fim", "2025-12-31"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Consulta por período limitada a 92 dias"));
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
        when(aulaService.buscarPorId(99L)).thenThrow(new ResourceNotFoundException("Aula não encontrada: 99"));

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

        mockMvc.perform(patch("/aulas/99/realizar")).andExpect(status().isNotFound());
    }

    @Test
    void atribuirProfissional_retorna200ComVinculo() throws Exception {
        when(aulaService.atribuirProfissional(1L, 7L)).thenReturn(aulaResponse(false));

        mockMvc.perform(patchProfissional("/aulas/1/profissional", 7L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profissionalId").value(7))
                .andExpect(jsonPath("$.profissionalNome").value("Paula"))
                .andExpect(jsonPath("$.realizada").value(false));
    }

    @Test
    void atribuirProfissional_comProfissionalIdNulo_retorna200SemVinculo() throws Exception {
        when(aulaService.atribuirProfissional(eq(1L), isNull()))
                .thenReturn(new AulaResponseDTO(1L, 1L, "Ana", null, null, 1L, LocalDate.of(2025, 2, 3), false));

        mockMvc.perform(patchProfissional("/aulas/1/profissional", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profissionalId").doesNotExist())
                .andExpect(jsonPath("$.profissionalNome").doesNotExist());
    }

    @Test
    void atribuirProfissional_aulaNaoEncontrada_retorna404() throws Exception {
        when(aulaService.atribuirProfissional(99L, 7L))
                .thenThrow(new ResourceNotFoundException("Aula não encontrada: 99"));

        mockMvc.perform(patchProfissional("/aulas/99/profissional", 7L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.erro").value("Aula não encontrada: 99"));
    }

    @Test
    void atribuirProfissional_profissionalInexistente_retorna404() throws Exception {
        when(aulaService.atribuirProfissional(1L, 99L))
                .thenThrow(new ResourceNotFoundException("Profissional não encontrado: 99"));

        mockMvc.perform(patchProfissional("/aulas/1/profissional", 99L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.erro").value("Profissional não encontrado: 99"));
    }

    @Test
    void atribuirProfissional_aulaJaRealizada_retorna409() throws Exception {
        when(aulaService.atribuirProfissional(1L, 7L))
                .thenThrow(new ConflictException("Aula já realizada não pode ter o profissional alterado"));

        mockMvc.perform(patchProfissional("/aulas/1/profissional", 7L))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erro").exists());
    }

    @Test
    void atribuirProfissional_profissionalInativo_retorna422() throws Exception {
        when(aulaService.atribuirProfissional(1L, 7L))
                .thenThrow(new BusinessException("Profissional inativo não pode ser vinculado à aula"));

        mockMvc.perform(patchProfissional("/aulas/1/profissional", 7L))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.erro").exists());
    }

    private MockHttpServletRequestBuilder patchProfissional(String url, Long profissionalId) throws Exception {
        return patch(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AulaProfissionalRequestDTO(profissionalId)));
    }
}
