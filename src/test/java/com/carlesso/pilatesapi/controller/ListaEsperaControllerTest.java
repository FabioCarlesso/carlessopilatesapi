package com.carlesso.pilatesapi.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carlesso.pilatesapi.dto.ListaEsperaRequestDTO;
import com.carlesso.pilatesapi.dto.ListaEsperaResponseDTO;
import com.carlesso.pilatesapi.exception.BusinessException;
import com.carlesso.pilatesapi.exception.ConflictException;
import com.carlesso.pilatesapi.exception.ResourceNotFoundException;
import com.carlesso.pilatesapi.service.CustomUserDetailsService;
import com.carlesso.pilatesapi.service.JwtService;
import com.carlesso.pilatesapi.service.ListaEsperaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ListaEsperaController.class)
@AutoConfigureMockMvc(addFilters = false)
class ListaEsperaControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    ListaEsperaService service;

    @MockitoBean
    JwtService jwtService;

    @MockitoBean
    CustomUserDetailsService customUserDetailsService;

    @Test
    void criar_deveRetornar201ComLocation() throws Exception {
        when(service.criar(any())).thenReturn(entrada(5L));

        mockMvc.perform(post("/lista-espera")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(corpo())))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/lista-espera/5"))
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.pacienteId").value(1))
                .andExpect(jsonPath("$.pacienteNome").value("Ana"))
                .andExpect(jsonPath("$.diaSemana").value("MONDAY"))
                .andExpect(jsonPath("$.horaInicio").value("08:00:00"))
                .andExpect(jsonPath("$.horaFim").value("09:00:00"))
                .andExpect(jsonPath("$.dataEntrada").value("2026-08-10T09:00:00"));
    }

    /** A ordem de chegada não é negociável com o cliente: mandar `dataEntrada` no corpo não muda nada. */
    @Test
    void criar_comDataEntradaNoCorpo_deveIgnorarOValorEnviado() throws Exception {
        when(service.criar(any())).thenReturn(entrada(5L));
        var corpo = corpo();
        corpo.put("dataEntrada", "2020-01-01T00:00:00");

        mockMvc.perform(post("/lista-espera")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(corpo)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dataEntrada").value("2026-08-10T09:00:00"));

        ArgumentCaptor<ListaEsperaRequestDTO> recebido = ArgumentCaptor.forClass(ListaEsperaRequestDTO.class);
        verify(service).criar(recebido.capture());
        assertThat(recebido.getValue().pacienteId()).isEqualTo(1L);
    }

    @Test
    void criar_semPacienteId_deveRetornar400ComCampo() throws Exception {
        var corpo = corpo();
        corpo.remove("pacienteId");

        mockMvc.perform(post("/lista-espera")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(corpo)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Dados inválidos"))
                .andExpect(jsonPath("$.campos[?(@.campo == 'pacienteId')].mensagem")
                        .value("pacienteId é obrigatório"));
    }

    @Test
    void criar_semDiaSemana_deveRetornar400ComCampo() throws Exception {
        var corpo = corpo();
        corpo.remove("diaSemana");

        mockMvc.perform(post("/lista-espera")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(corpo)))
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.campos[?(@.campo == 'diaSemana')].mensagem").value("diaSemana é obrigatório"));
    }

    @Test
    void criar_faixaInvertida_deveRetornar400ComMensagemDoServico() throws Exception {
        when(service.criar(any())).thenThrow(new IllegalArgumentException("horaFim deve ser posterior a horaInicio"));

        mockMvc.perform(post("/lista-espera")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(corpo())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("horaFim deve ser posterior a horaInicio"));
    }

    @Test
    void criar_pacienteInexistente_deveRetornar404() throws Exception {
        when(service.criar(any())).thenThrow(new ResourceNotFoundException("Paciente não encontrado: 9"));

        mockMvc.perform(post("/lista-espera")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(corpo())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Paciente não encontrado: 9"));
    }

    @Test
    void criar_pacienteInativo_deveRetornar422() throws Exception {
        when(service.criar(any()))
                .thenThrow(new BusinessException("Paciente inativo não aceita novos registros clínicos: 1"));

        mockMvc.perform(post("/lista-espera")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(corpo())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Paciente inativo não aceita novos registros clínicos: 1"));
    }

    @Test
    void criar_entradaDuplicada_deveRetornar409() throws Exception {
        when(service.criar(any()))
                .thenThrow(
                        new ConflictException("Paciente já está na lista de espera para esse dia e faixa de horário"));

        mockMvc.perform(post("/lista-espera")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(corpo())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("Paciente já está na lista de espera para esse dia e faixa de horário"));
    }

    @Test
    void listar_semFiltros_deveRetornar200ComAFilaInteira() throws Exception {
        when(service.listar(null, null, null, null)).thenReturn(List.of(entrada(5L), entrada(6L)));

        mockMvc.perform(get("/lista-espera"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(5))
                .andExpect(jsonPath("$[1].id").value(6));
    }

    @Test
    void listar_comFiltros_devePassarOsParametrosAoServico() throws Exception {
        when(service.listar(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(9, 0), 1L))
                .thenReturn(List.of(entrada(5L)));

        mockMvc.perform(get("/lista-espera")
                        .param("diaSemana", "MONDAY")
                        .param("horaInicio", "08:00")
                        .param("horaFim", "09:00")
                        .param("pacienteId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(5))
                .andExpect(jsonPath("$[0].diaSemana").value("MONDAY"));
    }

    @Test
    void listar_faixaPelaMetade_deveRetornar400() throws Exception {
        when(service.listar(any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("horaInicio e horaFim devem ser informadas juntas; omita as "
                        + "duas para não filtrar por horário"));

        mockMvc.perform(get("/lista-espera").param("horaInicio", "08:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail")
                        .value("horaInicio e horaFim devem ser informadas juntas; omita as "
                                + "duas para não filtrar por horário"));
    }

    @Test
    void listar_diaSemanaInvalido_deveRetornar400() throws Exception {
        mockMvc.perform(get("/lista-espera").param("diaSemana", "SEGUNDA")).andExpect(status().isBadRequest());
    }

    /** O {@code Location} do 201 precisa apontar para algo recuperável — antes desta rota o GET dava 405. */
    @Test
    void buscar_naUrlDoLocation_deveRetornar200() throws Exception {
        when(service.buscarPorId(5L)).thenReturn(entrada(5L));

        mockMvc.perform(get("/lista-espera/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.pacienteNome").value("Ana"));
    }

    @Test
    void buscar_removidaOuInexistente_deveRetornar404() throws Exception {
        when(service.buscarPorId(9L))
                .thenThrow(new ResourceNotFoundException("Entrada da lista de espera não encontrada: 9"));

        mockMvc.perform(get("/lista-espera/9"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Entrada da lista de espera não encontrada: 9"));
    }

    @Test
    void remover_deveRetornar204() throws Exception {
        mockMvc.perform(delete("/lista-espera/5")).andExpect(status().isNoContent());

        verify(service).remover(5L);
    }

    @Test
    void remover_inexistente_deveRetornar404() throws Exception {
        doThrow(new ResourceNotFoundException("Entrada da lista de espera não encontrada: 9"))
                .when(service)
                .remover(9L);

        mockMvc.perform(delete("/lista-espera/9"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Entrada da lista de espera não encontrada: 9"));
    }

    private Map<String, Object> corpo() {
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("pacienteId", 1);
        corpo.put("diaSemana", "MONDAY");
        corpo.put("horaInicio", "08:00");
        corpo.put("horaFim", "09:00");
        return corpo;
    }

    private ListaEsperaResponseDTO entrada(Long id) {
        return new ListaEsperaResponseDTO(
                id,
                1L,
                "Ana",
                DayOfWeek.MONDAY,
                LocalTime.of(8, 0),
                LocalTime.of(9, 0),
                LocalDateTime.of(2026, 8, 10, 9, 0),
                null);
    }
}
