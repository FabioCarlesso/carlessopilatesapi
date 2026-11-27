package com.carlesso.pilatesapi.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.carlesso.pilatesapi.dto.PontoDorResponseDTO;
import com.carlesso.pilatesapi.dto.PontosDorRegistroDTO;
import com.carlesso.pilatesapi.dto.PontosDorRequestDTO;
import com.carlesso.pilatesapi.entity.enums.OrigemPontoDor;
import com.carlesso.pilatesapi.entity.enums.VistaCorporal;
import com.carlesso.pilatesapi.exception.ResourceNotFoundException;
import com.carlesso.pilatesapi.service.CustomUserDetailsService;
import com.carlesso.pilatesapi.service.JwtService;
import com.carlesso.pilatesapi.service.PontoDorService;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PontoDorController.class)
@AutoConfigureMockMvc(addFilters = false)
class PontoDorControllerTest {

    private static final String PONTO_VALIDO =
            """
            {"vista":"FRENTE","x":0.42,"y":0.31,"desenhoVersao":1,"intensidade":7,"observacao":"Dor ao flexionar"}""";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PontoDorService service;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    private static PontoDorResponseDTO ponto() {
        return new PontoDorResponseDTO(10L, VistaCorporal.FRENTE, 0.42, 0.31, 1, 7, "Dor ao flexionar");
    }

    private static String corpo(String... pontos) {
        return "{\"pontos\":[" + String.join(",", pontos) + "]}";
    }

    @Test
    void substituirDaAvaliacao_comPontoValido_retornaAListaGravada() throws Exception {
        when(service.substituirDaAvaliacao(eq(1L), any(PontosDorRequestDTO.class)))
                .thenReturn(List.of(ponto()));

        mvc.perform(put("/avaliacoes-fisioterapeuticas/1/pontos-dor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(PONTO_VALIDO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].vista").value("FRENTE"))
                .andExpect(jsonPath("$[0].x").value(0.42))
                .andExpect(jsonPath("$[0].y").value(0.31))
                .andExpect(jsonPath("$[0].desenhoVersao").value(1))
                .andExpect(jsonPath("$[0].intensidade").value(7))
                .andExpect(jsonPath("$[0].observacao").value("Dor ao flexionar"));
    }

    @Test
    void substituirDaReavaliacao_comListaVazia_aceita() throws Exception {
        when(service.substituirDaReavaliacao(eq(2L), any(PontosDorRequestDTO.class)))
                .thenReturn(List.of());

        mvc.perform(put("/reavaliacoes/2/pontos-dor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"vista\":\"FRENTE\",\"x\":1.01,\"y\":0.5,\"desenhoVersao\":1}",
                "{\"vista\":\"FRENTE\",\"x\":-0.01,\"y\":0.5,\"desenhoVersao\":1}",
                "{\"vista\":\"FRENTE\",\"x\":0.5,\"y\":1.5,\"desenhoVersao\":1}",
                "{\"vista\":\"FRENTE\",\"x\":0.5,\"y\":-1,\"desenhoVersao\":1}",
                "{\"vista\":\"LADO_DIREITO\",\"x\":0.5,\"y\":0.5,\"desenhoVersao\":1}",
                "{\"x\":0.5,\"y\":0.5,\"desenhoVersao\":1}",
                "{\"vista\":\"FRENTE\",\"x\":0.5,\"y\":0.5,\"desenhoVersao\":0}",
                "{\"vista\":\"FRENTE\",\"x\":0.5,\"y\":0.5}",
                "{\"vista\":\"FRENTE\",\"x\":0.5,\"y\":0.5,\"desenhoVersao\":1,\"intensidade\":11}",
                "{\"vista\":\"FRENTE\",\"y\":0.5,\"desenhoVersao\":1}",
                "null"
            })
    void substituir_comPontoInvalido_retorna400(String ponto) throws Exception {
        mvc.perform(put("/avaliacoes-fisioterapeuticas/1/pontos-dor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(ponto)))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/reavaliacoes/1/pontos-dor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(ponto)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void substituir_comObservacaoAcimaDe200Caracteres_retorna400() throws Exception {
        String ponto = "{\"vista\":\"COSTAS\",\"x\":0.5,\"y\":0.5,\"desenhoVersao\":1,\"observacao\":\""
                + "a".repeat(201) + "\"}";

        mvc.perform(put("/avaliacoes-fisioterapeuticas/1/pontos-dor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(ponto)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void substituir_com51Pontos_retorna400() throws Exception {
        String pontos = IntStream.range(0, 51).mapToObj(i -> PONTO_VALIDO).collect(Collectors.joining(","));

        mvc.perform(put("/avaliacoes-fisioterapeuticas/1/pontos-dor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pontos\":[" + pontos + "]}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void substituir_com50Pontos_aceita() throws Exception {
        String pontos = IntStream.range(0, 50).mapToObj(i -> PONTO_VALIDO).collect(Collectors.joining(","));
        when(service.substituirDaAvaliacao(eq(1L), any(PontosDorRequestDTO.class)))
                .thenReturn(List.of());

        mvc.perform(put("/avaliacoes-fisioterapeuticas/1/pontos-dor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pontos\":[" + pontos + "]}"))
                .andExpect(status().isOk());
    }

    @Test
    void substituir_semALista_retorna400() throws Exception {
        mvc.perform(put("/avaliacoes-fisioterapeuticas/1/pontos-dor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void substituir_registroInexistente_retorna404() throws Exception {
        when(service.substituirDaReavaliacao(eq(99L), any(PontosDorRequestDTO.class)))
                .thenThrow(new ResourceNotFoundException("Reavaliação não encontrada: 99"));

        mvc.perform(put("/reavaliacoes/99/pontos-dor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(PONTO_VALIDO)))
                .andExpect(status().isNotFound());
    }

    @Test
    void listarDaAvaliacaoEDaReavaliacao_retornamOsPontos() throws Exception {
        when(service.listarDaAvaliacao(1L)).thenReturn(List.of(ponto()));
        when(service.listarDaReavaliacao(2L)).thenReturn(List.of());

        mvc.perform(get("/avaliacoes-fisioterapeuticas/1/pontos-dor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].vista").value("FRENTE"));
        mvc.perform(get("/reavaliacoes/2/pontos-dor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void historicoDoPaciente_retornaOsRegistrosAgrupados() throws Exception {
        when(service.historicoDoPaciente(5L))
                .thenReturn(List.of(
                        new PontosDorRegistroDTO(
                                OrigemPontoDor.AVALIACAO, 1L, LocalDate.of(2026, 10, 1), List.of(ponto())),
                        new PontosDorRegistroDTO(
                                OrigemPontoDor.REAVALIACAO, 2L, LocalDate.of(2026, 11, 1), List.of(ponto()))));

        mvc.perform(get("/pacientes/5/pontos-dor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].origem").value("AVALIACAO"))
                .andExpect(jsonPath("$[0].registroId").value(1))
                .andExpect(jsonPath("$[0].data").value("2026-10-01"))
                .andExpect(jsonPath("$[0].pontos[0].desenhoVersao").value(1))
                .andExpect(jsonPath("$[1].origem").value("REAVALIACAO"))
                .andExpect(jsonPath("$[1].data").value("2026-11-01"));
    }

    @Test
    void historicoDoPaciente_pacienteInexistente_retorna404() throws Exception {
        when(service.historicoDoPaciente(99L)).thenThrow(new ResourceNotFoundException("Paciente não encontrado: 99"));

        mvc.perform(get("/pacientes/99/pontos-dor")).andExpect(status().isNotFound());
    }
}
