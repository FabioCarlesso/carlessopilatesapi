package com.carlesso.pilatesapi.controller;

import com.carlesso.pilatesapi.dto.AvaliacaoFisioterapeuticaRequestDTO;
import com.carlesso.pilatesapi.dto.AvaliacaoFisioterapeuticaResponseDTO;
import com.carlesso.pilatesapi.dto.AvaliacaoFisioterapeuticaUpdateDTO;
import com.carlesso.pilatesapi.exception.ResourceNotFoundException;
import com.carlesso.pilatesapi.service.AvaliacaoFisioterapeuticaService;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AvaliacaoFisioterapeuticaController.class)
@AutoConfigureMockMvc(addFilters = false)
class AvaliacaoFisioterapeuticaControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AvaliacaoFisioterapeuticaService service;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @Autowired
    private ObjectMapper mapper;

    private AvaliacaoFisioterapeuticaResponseDTO responseDTO() {
        return new AvaliacaoFisioterapeuticaResponseDTO(
                1L, 1L, "Maria Souza",
                LocalDate.of(2026, 4, 20),
                "Dor ao agachar", "Anteriorização de cabeça", "Restrição em quadril direito",
                "Glúteo médio grau 4", "Encurtamento de cadeia posterior", "Instável em apoio unipodal",
                "Boa coordenação", "Respiração apical", 6,
                "Agachamento, ponte, apoio unipodal", "Disfunção lombopélvica",
                "Reavaliar em 30 dias", LocalDateTime.of(2026, 4, 20, 10, 0), null
        );
    }

    private AvaliacaoFisioterapeuticaRequestDTO requestDTO() {
        return new AvaliacaoFisioterapeuticaRequestDTO(
                1L,
                LocalDate.of(2026, 4, 20),
                "Dor ao agachar",
                "Anteriorização de cabeça",
                "Restrição em quadril direito",
                "Glúteo médio grau 4",
                "Encurtamento de cadeia posterior",
                "Instável em apoio unipodal",
                "Boa coordenação",
                "Respiração apical",
                6,
                "Agachamento, ponte, apoio unipodal",
                "Disfunção lombopélvica",
                "Reavaliar em 30 dias"
        );
    }

    @Test
    void criar_comDadosValidos_deveRetornar201ComHeaderLocation() throws Exception {
        when(service.criar(any())).thenReturn(responseDTO());

        mvc.perform(post("/avaliacoes-fisioterapeuticas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(requestDTO())))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.pacienteId").value(1))
                .andExpect(jsonPath("$.nomePaciente").value("Maria Souza"))
                .andExpect(jsonPath("$.escalaDor").value(6));
    }

    @Test
    void criar_semPacienteId_deveRetornar400() throws Exception {
        var dto = new AvaliacaoFisioterapeuticaRequestDTO(
                null, LocalDate.of(2026, 4, 20), "Dor", null, null, null, null,
                null, null, null, 5, null, "Diagnóstico", null
        );

        mvc.perform(post("/avaliacoes-fisioterapeuticas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void criar_semDataAvaliacao_deveRetornar400() throws Exception {
        var dto = new AvaliacaoFisioterapeuticaRequestDTO(
                1L, null, "Dor", null, null, null, null,
                null, null, null, 5, null, "Diagnóstico", null
        );

        mvc.perform(post("/avaliacoes-fisioterapeuticas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void criar_comEscalaDorForaDoIntervalo_deveRetornar400() throws Exception {
        var dto = new AvaliacaoFisioterapeuticaRequestDTO(
                1L, LocalDate.of(2026, 4, 20), "Dor", null, null, null, null,
                null, null, null, 11, null, "Diagnóstico", null
        );

        mvc.perform(post("/avaliacoes-fisioterapeuticas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void criar_comPacienteInexistente_deveRetornar404() throws Exception {
        when(service.criar(any())).thenThrow(new ResourceNotFoundException("Paciente não encontrado: 99"));

        mvc.perform(post("/avaliacoes-fisioterapeuticas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(requestDTO())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.erro").value("Paciente não encontrado: 99"));
    }

    @Test
    void buscarPorId_quandoExistente_deveRetornar200() throws Exception {
        when(service.buscarPorId(1L)).thenReturn(responseDTO());

        mvc.perform(get("/avaliacoes-fisioterapeuticas/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.queixaFuncional").value("Dor ao agachar"));
    }

    @Test
    void buscarPorId_quandoNaoExistente_deveRetornar404() throws Exception {
        when(service.buscarPorId(99L))
                .thenThrow(new ResourceNotFoundException("Avaliação fisioterapêutica não encontrada: 99"));

        mvc.perform(get("/avaliacoes-fisioterapeuticas/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.erro").value("Avaliação fisioterapêutica não encontrada: 99"));
    }

    @Test
    void listarPorPaciente_deveRetornar200ComAvaliacoes() throws Exception {
        when(service.listarPorPaciente(1L)).thenReturn(List.of(responseDTO()));

        mvc.perform(get("/avaliacoes-fisioterapeuticas/paciente/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].pacienteId").value(1))
                .andExpect(jsonPath("$[0].dataAvaliacao").value("2026-04-20"));
    }

    @Test
    void atualizar_deveRetornar200ComDadosAtualizados() throws Exception {
        var updated = new AvaliacaoFisioterapeuticaResponseDTO(
                1L, 1L, "Maria Souza",
                LocalDate.of(2026, 5, 5),
                "Dor reduzida ao agachar", "Anteriorização de cabeça", "Restrição em quadril direito",
                "Glúteo médio grau 4", "Encurtamento de cadeia posterior", "Instável em apoio unipodal",
                "Boa coordenação", "Respiração apical", 3,
                "Agachamento, ponte, apoio unipodal", "Disfunção lombopélvica",
                "Evoluiu com exercícios", LocalDateTime.of(2026, 4, 20, 10, 0),
                LocalDateTime.of(2026, 5, 5, 9, 0)
        );
        when(service.atualizar(eq(1L), any())).thenReturn(updated);

        var dto = new AvaliacaoFisioterapeuticaUpdateDTO(
                LocalDate.of(2026, 5, 5), "Dor reduzida ao agachar", null, null, null,
                null, null, null, null, 3, null, null, "Evoluiu com exercícios"
        );

        mvc.perform(put("/avaliacoes-fisioterapeuticas/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataAvaliacao").value("2026-05-05"))
                .andExpect(jsonPath("$.escalaDor").value(3))
                .andExpect(jsonPath("$.dataAtualizacao").isNotEmpty());
    }

    @Test
    void atualizar_comEscalaDorNegativa_deveRetornar400() throws Exception {
        var dto = new AvaliacaoFisioterapeuticaUpdateDTO(
                null, null, null, null, null, null, null, null, null, -1, null, null, null
        );

        mvc.perform(put("/avaliacoes-fisioterapeuticas/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void atualizar_comQueixaFuncionalEmBranco_deveRetornar400() throws Exception {
        var dto = new AvaliacaoFisioterapeuticaUpdateDTO(
                null, "   ", null, null, null, null, null, null, null, null, null, null, null
        );

        mvc.perform(put("/avaliacoes-fisioterapeuticas/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void atualizar_comAvaliacaoInexistente_deveRetornar404() throws Exception {
        when(service.atualizar(eq(99L), any()))
                .thenThrow(new ResourceNotFoundException("Avaliação fisioterapêutica não encontrada: 99"));

        var dto = new AvaliacaoFisioterapeuticaUpdateDTO(
                LocalDate.of(2026, 5, 5), null, null, null, null, null, null, null, null, null, null, null, null
        );

        mvc.perform(put("/avaliacoes-fisioterapeuticas/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(dto)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.erro").value("Avaliação fisioterapêutica não encontrada: 99"));
    }
}
