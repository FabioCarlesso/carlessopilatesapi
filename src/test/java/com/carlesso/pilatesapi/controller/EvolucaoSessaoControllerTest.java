package com.carlesso.pilatesapi.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.carlesso.pilatesapi.dto.EvolucaoSessaoRequestDTO;
import com.carlesso.pilatesapi.dto.EvolucaoSessaoResponseDTO;
import com.carlesso.pilatesapi.dto.EvolucaoSessaoUpdateDTO;
import com.carlesso.pilatesapi.exception.ConflictException;
import com.carlesso.pilatesapi.exception.ResourceNotFoundException;
import com.carlesso.pilatesapi.service.CustomUserDetailsService;
import com.carlesso.pilatesapi.service.EvolucaoSessaoService;
import com.carlesso.pilatesapi.service.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EvolucaoSessaoController.class)
@AutoConfigureMockMvc(addFilters = false)
class EvolucaoSessaoControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private EvolucaoSessaoService service;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @Autowired
    private ObjectMapper mapper;

    private EvolucaoSessaoResponseDTO responseDTO() {
        return new EvolucaoSessaoResponseDTO(
                1L,
                1L,
                LocalDateTime.of(2026, 5, 10, 10, 30),
                "Reformer, Cadillac",
                "Reformer",
                null,
                5,
                2,
                "Boa evolução",
                null,
                "Manter exercícios",
                null,
                LocalDateTime.of(2026, 5, 10, 10, 30),
                null);
    }

    private EvolucaoSessaoRequestDTO requestDTO() {
        return new EvolucaoSessaoRequestDTO(
                1L,
                LocalDateTime.of(2026, 5, 10, 10, 30),
                "Reformer, Cadillac",
                "Reformer",
                null,
                5,
                2,
                "Boa evolução",
                null,
                "Manter exercícios",
                null);
    }

    @Test
    void criar_comDadosValidos_deveRetornar201ComHeaderLocation() throws Exception {
        when(service.criar(any())).thenReturn(responseDTO());

        mvc.perform(post("/evolucoes-sessao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(requestDTO())))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.sessaoId").value(1))
                .andExpect(jsonPath("$.dorAntes").value(5))
                .andExpect(jsonPath("$.dorDepois").value(2))
                .andExpect(jsonPath("$.respostaPaciente").value("Boa evolução"));
    }

    @Test
    void criar_semSessaoId_deveRetornar400() throws Exception {
        var dto = new EvolucaoSessaoRequestDTO(
                null, LocalDateTime.now(), null, null, null, null, null, null, null, null, null);

        mvc.perform(post("/evolucoes-sessao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void criar_semDataHoraRegistro_deveRetornar400() throws Exception {
        var dto = new EvolucaoSessaoRequestDTO(1L, null, null, null, null, null, null, null, null, null, null);

        mvc.perform(post("/evolucoes-sessao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void criar_comDorForaDaEscala_deveRetornar400() throws Exception {
        var dto = new EvolucaoSessaoRequestDTO(
                1L, LocalDateTime.now(), null, null, null, 11, null, null, null, null, null);

        mvc.perform(post("/evolucoes-sessao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void criar_comSessaoInexistente_deveRetornar404() throws Exception {
        when(service.criar(any())).thenThrow(new ResourceNotFoundException("Sessão não encontrada: 99"));

        mvc.perform(post("/evolucoes-sessao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(requestDTO())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.erro").value("Sessão não encontrada: 99"));
    }

    @Test
    void criar_comSessaoJaComEvolucao_deveRetornar409() throws Exception {
        when(service.criar(any())).thenThrow(new ConflictException("Sessão já possui evolução registrada: 1"));

        mvc.perform(post("/evolucoes-sessao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(requestDTO())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erro").value("Sessão já possui evolução registrada: 1"));
    }

    @Test
    void buscarPorId_quandoExistente_deveRetornar200() throws Exception {
        when(service.buscarPorId(1L)).thenReturn(responseDTO());

        mvc.perform(get("/evolucoes-sessao/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.exerciciosRealizados").value("Reformer, Cadillac"));
    }

    @Test
    void buscarPorId_quandoNaoExistente_deveRetornar404() throws Exception {
        when(service.buscarPorId(99L)).thenThrow(new ResourceNotFoundException("Evolução não encontrada: 99"));

        mvc.perform(get("/evolucoes-sessao/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.erro").value("Evolução não encontrada: 99"));
    }

    @Test
    void buscarPorSessao_quandoExistente_deveRetornar200() throws Exception {
        when(service.buscarPorSessao(1L)).thenReturn(responseDTO());

        mvc.perform(get("/evolucoes-sessao/sessao/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessaoId").value(1))
                .andExpect(jsonPath("$.dorAntes").value(5));
    }

    @Test
    void buscarPorSessao_comSessaoSemEvolucao_deveRetornar404() throws Exception {
        when(service.buscarPorSessao(99L))
                .thenThrow(new ResourceNotFoundException("Evolução não encontrada para a sessão: 99"));

        mvc.perform(get("/evolucoes-sessao/sessao/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.erro").value("Evolução não encontrada para a sessão: 99"));
    }

    @Test
    void atualizar_deveRetornar200ComDadosAtualizados() throws Exception {
        var updated = new EvolucaoSessaoResponseDTO(
                1L,
                1L,
                LocalDateTime.of(2026, 5, 10, 10, 30),
                "Reformer, Chair",
                "Reformer",
                "Mola 3",
                3,
                1,
                "Melhorou muito",
                null,
                "Manter exercícios",
                null,
                LocalDateTime.of(2026, 5, 10, 10, 30),
                LocalDateTime.of(2026, 5, 10, 11, 0));
        when(service.atualizar(eq(1L), any())).thenReturn(updated);

        var dto = new EvolucaoSessaoUpdateDTO(
                null, "Reformer, Chair", null, "Mola 3", 3, 1, "Melhorou muito", null, null, null);

        mvc.perform(put("/evolucoes-sessao/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exerciciosRealizados").value("Reformer, Chair"))
                .andExpect(jsonPath("$.cargasMolas").value("Mola 3"))
                .andExpect(jsonPath("$.dorAntes").value(3))
                .andExpect(jsonPath("$.dataAtualizacao").isNotEmpty());
    }

    @Test
    void atualizar_comDorForaDaEscala_deveRetornar400() throws Exception {
        var dto = new EvolucaoSessaoUpdateDTO(null, null, null, null, -1, null, null, null, null, null);

        mvc.perform(put("/evolucoes-sessao/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void atualizar_comEvolucaoInexistente_deveRetornar404() throws Exception {
        when(service.atualizar(eq(99L), any())).thenThrow(new ResourceNotFoundException("Evolução não encontrada: 99"));

        var dto = new EvolucaoSessaoUpdateDTO(null, "Novo exercício", null, null, null, null, null, null, null, null);

        mvc.perform(put("/evolucoes-sessao/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(dto)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.erro").value("Evolução não encontrada: 99"));
    }
}
