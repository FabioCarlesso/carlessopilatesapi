package com.carlesso.pilatesapi.controller;

import com.carlesso.pilatesapi.dto.SessaoPilatesRequestDTO;
import com.carlesso.pilatesapi.dto.SessaoPilatesResponseDTO;
import com.carlesso.pilatesapi.dto.SessaoPilatesUpdateDTO;
import com.carlesso.pilatesapi.entity.enums.StatusSessao;
import com.carlesso.pilatesapi.entity.enums.TipoSessao;
import com.carlesso.pilatesapi.exception.BusinessException;
import com.carlesso.pilatesapi.exception.ResourceNotFoundException;
import com.carlesso.pilatesapi.service.CustomUserDetailsService;
import com.carlesso.pilatesapi.service.JwtService;
import com.carlesso.pilatesapi.service.SessaoPilatesService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SessaoPilatesController.class)
@AutoConfigureMockMvc(addFilters = false)
class SessaoPilatesControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SessaoPilatesService service;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @Autowired
    private ObjectMapper mapper;

    private SessaoPilatesResponseDTO responseDTO() {
        return new SessaoPilatesResponseDTO(
                1L, 1L, "Ana Oliveira",
                null, null, null,
                TipoSessao.PILATES,
                StatusSessao.AGENDADA,
                LocalDate.of(2026, 5, 10),
                LocalTime.of(9, 0),
                "Sala 1",
                50,
                "Observação teste",
                LocalDateTime.of(2026, 5, 4, 10, 0),
                null
        );
    }

    private SessaoPilatesRequestDTO requestDTO() {
        return new SessaoPilatesRequestDTO(
                1L, null, null,
                TipoSessao.PILATES,
                LocalDate.of(2026, 5, 10),
                LocalTime.of(9, 0),
                "Sala 1",
                50,
                "Observação teste"
        );
    }

    @Test
    void criar_comDadosValidos_deveRetornar201ComHeaderLocation() throws Exception {
        when(service.criar(any())).thenReturn(responseDTO());

        mvc.perform(post("/sessoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(requestDTO())))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.pacienteId").value(1))
                .andExpect(jsonPath("$.nomePaciente").value("Ana Oliveira"))
                .andExpect(jsonPath("$.tipo").value("PILATES"))
                .andExpect(jsonPath("$.status").value("AGENDADA"))
                .andExpect(jsonPath("$.duracaoMinutos").value(50));
    }

    @Test
    void criar_semPacienteId_deveRetornar400() throws Exception {
        var dto = new SessaoPilatesRequestDTO(
                null, null, null, TipoSessao.PILATES,
                LocalDate.of(2026, 5, 10), null, null, null, null
        );

        mvc.perform(post("/sessoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void criar_semTipo_deveRetornar400() throws Exception {
        var dto = new SessaoPilatesRequestDTO(
                1L, null, null, null,
                LocalDate.of(2026, 5, 10), null, null, null, null
        );

        mvc.perform(post("/sessoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void criar_semData_deveRetornar400() throws Exception {
        var dto = new SessaoPilatesRequestDTO(
                1L, null, null, TipoSessao.PILATES,
                null, null, null, null, null
        );

        mvc.perform(post("/sessoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void criar_comDuracaoNegativa_deveRetornar400() throws Exception {
        var dto = new SessaoPilatesRequestDTO(
                1L, null, null, TipoSessao.PILATES,
                LocalDate.of(2026, 5, 10), null, null, -10, null
        );

        mvc.perform(post("/sessoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void criar_comPacienteInexistente_deveRetornar404() throws Exception {
        when(service.criar(any())).thenThrow(new ResourceNotFoundException("Paciente não encontrado: 99"));

        mvc.perform(post("/sessoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(requestDTO())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.erro").value("Paciente não encontrado: 99"));
    }

    @Test
    void buscarPorId_quandoExistente_deveRetornar200() throws Exception {
        when(service.buscarPorId(1L)).thenReturn(responseDTO());

        mvc.perform(get("/sessoes/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.local").value("Sala 1"));
    }

    @Test
    void buscarPorId_quandoNaoExistente_deveRetornar404() throws Exception {
        when(service.buscarPorId(99L))
                .thenThrow(new ResourceNotFoundException("Sessão não encontrada: 99"));

        mvc.perform(get("/sessoes/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.erro").value("Sessão não encontrada: 99"));
    }

    @Test
    void listarPorPaciente_deveRetornar200ComSessoes() throws Exception {
        when(service.listarPorPaciente(1L)).thenReturn(List.of(responseDTO()));

        mvc.perform(get("/sessoes/paciente/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].pacienteId").value(1))
                .andExpect(jsonPath("$[0].data").value("2026-05-10"))
                .andExpect(jsonPath("$[0].tipo").value("PILATES"));
    }

    @Test
    void listarPorPaciente_comPacienteInexistente_deveRetornar404() throws Exception {
        when(service.listarPorPaciente(99L))
                .thenThrow(new ResourceNotFoundException("Paciente não encontrado: 99"));

        mvc.perform(get("/sessoes/paciente/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.erro").value("Paciente não encontrado: 99"));
    }

    @Test
    void atualizar_deveRetornar200ComDadosAtualizados() throws Exception {
        var updated = new SessaoPilatesResponseDTO(
                1L, 1L, "Ana Oliveira",
                null, null, null,
                TipoSessao.PILATES,
                StatusSessao.REALIZADA,
                LocalDate.of(2026, 5, 15),
                LocalTime.of(10, 0),
                "Sala 1",
                60,
                "Observação teste",
                LocalDateTime.of(2026, 5, 4, 10, 0),
                LocalDateTime.of(2026, 5, 15, 10, 0)
        );
        when(service.atualizar(eq(1L), any())).thenReturn(updated);

        var dto = new SessaoPilatesUpdateDTO(
                LocalDate.of(2026, 5, 15),
                LocalTime.of(10, 0),
                null, 60,
                StatusSessao.REALIZADA,
                null
        );

        mvc.perform(put("/sessoes/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("2026-05-15"))
                .andExpect(jsonPath("$.status").value("REALIZADA"))
                .andExpect(jsonPath("$.dataAtualizacao").isNotEmpty());
    }

    @Test
    void atualizar_comDuracaoNegativa_deveRetornar400() throws Exception {
        var dto = new SessaoPilatesUpdateDTO(null, null, null, -5, null, null);

        mvc.perform(put("/sessoes/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void atualizar_comSessaoInexistente_deveRetornar404() throws Exception {
        when(service.atualizar(eq(99L), any()))
                .thenThrow(new ResourceNotFoundException("Sessão não encontrada: 99"));

        var dto = new SessaoPilatesUpdateDTO(LocalDate.of(2026, 5, 20), null, null, null, null, null);

        mvc.perform(put("/sessoes/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(dto)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.erro").value("Sessão não encontrada: 99"));
    }

    @Test
    void realizar_comSessaoAgendada_deveRetornar200ComStatusRealizada() throws Exception {
        var realizada = new SessaoPilatesResponseDTO(
                1L, 1L, "Ana Oliveira",
                null, null, null,
                TipoSessao.PILATES,
                StatusSessao.REALIZADA,
                LocalDate.of(2026, 5, 10),
                LocalTime.of(9, 0),
                "Sala 1",
                50,
                "Observação teste",
                LocalDateTime.of(2026, 5, 4, 10, 0),
                LocalDateTime.of(2026, 5, 8, 12, 0)
        );
        when(service.realizar(1L)).thenReturn(realizada);

        mvc.perform(patch("/sessoes/1/realizar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("REALIZADA"));
    }

    @Test
    void realizar_comSessaoInexistente_deveRetornar404() throws Exception {
        when(service.realizar(99L))
                .thenThrow(new ResourceNotFoundException("Sessão não encontrada: 99"));

        mvc.perform(patch("/sessoes/99/realizar"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.erro").value("Sessão não encontrada: 99"));
    }

    @Test
    void realizar_comTransicaoInvalida_deveRetornar422() throws Exception {
        when(service.realizar(1L))
                .thenThrow(new BusinessException("Transição inválida: sessão CANCELADA não pode ser alterada para REALIZADA"));

        mvc.perform(patch("/sessoes/1/realizar"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.erro").value("Transição inválida: sessão CANCELADA não pode ser alterada para REALIZADA"));
    }

    @Test
    void cancelar_comSessaoAgendada_deveRetornar200ComStatusCancelada() throws Exception {
        var cancelada = new SessaoPilatesResponseDTO(
                1L, 1L, "Ana Oliveira",
                null, null, null,
                TipoSessao.PILATES,
                StatusSessao.CANCELADA,
                LocalDate.of(2026, 5, 10),
                LocalTime.of(9, 0),
                "Sala 1",
                50,
                "Observação teste",
                LocalDateTime.of(2026, 5, 4, 10, 0),
                LocalDateTime.of(2026, 5, 8, 12, 0)
        );
        when(service.cancelar(1L)).thenReturn(cancelada);

        mvc.perform(patch("/sessoes/1/cancelar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("CANCELADA"));
    }

    @Test
    void cancelar_comSessaoInexistente_deveRetornar404() throws Exception {
        when(service.cancelar(99L))
                .thenThrow(new ResourceNotFoundException("Sessão não encontrada: 99"));

        mvc.perform(patch("/sessoes/99/cancelar"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.erro").value("Sessão não encontrada: 99"));
    }

    @Test
    void cancelar_comTransicaoInvalida_deveRetornar422() throws Exception {
        when(service.cancelar(1L))
                .thenThrow(new BusinessException("Transição inválida: sessão REALIZADA não pode ser alterada para CANCELADA"));

        mvc.perform(patch("/sessoes/1/cancelar"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.erro").value("Transição inválida: sessão REALIZADA não pode ser alterada para CANCELADA"));
    }

    @Test
    void excluir_comSessaoExistente_deveRetornar204() throws Exception {
        mvc.perform(delete("/sessoes/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void excluir_comSessaoInexistente_deveRetornar404() throws Exception {
        org.mockito.Mockito.doThrow(new ResourceNotFoundException("Sessão não encontrada: 99"))
                .when(service).excluir(99L);

        mvc.perform(delete("/sessoes/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.erro").value("Sessão não encontrada: 99"));
    }
}
