package com.carlesso.pilatesapi.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.carlesso.pilatesapi.dto.AvaliacaoPosturalRequestDTO;
import com.carlesso.pilatesapi.dto.AvaliacaoPosturalResponseDTO;
import com.carlesso.pilatesapi.dto.AvaliacaoPosturalUpdateDTO;
import com.carlesso.pilatesapi.dto.LandmarkDTO;
import com.carlesso.pilatesapi.dto.MetricasPosturaisDTO;
import com.carlesso.pilatesapi.entity.enums.CodigoLandmark;
import com.carlesso.pilatesapi.entity.enums.StatusAvaliacaoPostural;
import com.carlesso.pilatesapi.entity.enums.VistaPostural;
import com.carlesso.pilatesapi.exception.BusinessException;
import com.carlesso.pilatesapi.exception.ConflictException;
import com.carlesso.pilatesapi.exception.ResourceNotFoundException;
import com.carlesso.pilatesapi.service.AvaliacaoPosturalService;
import com.carlesso.pilatesapi.service.CustomUserDetailsService;
import com.carlesso.pilatesapi.service.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AvaliacaoPosturalController.class)
@AutoConfigureMockMvc(addFilters = false)
class AvaliacaoPosturalControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AvaliacaoPosturalService service;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @Autowired
    private ObjectMapper mapper;

    private AvaliacaoPosturalResponseDTO responseDTO(StatusAvaliacaoPostural status) {
        return new AvaliacaoPosturalResponseDTO(
                10L,
                1L,
                VistaPostural.FRENTE,
                status,
                new BigDecimal("0.502"),
                null,
                null,
                "Elevação de ombro D compatível com queixa funcional.",
                true,
                List.of(
                        new LandmarkDTO(CodigoLandmark.OMBRO_ESQ, new BigDecimal("0.401"), new BigDecimal("0.232")),
                        new LandmarkDTO(CodigoLandmark.OMBRO_DIR, new BigDecimal("0.603"), new BigDecimal("0.247"))),
                new MetricasPosturaisDTO(null, new BigDecimal("2.30"), null, null, new BigDecimal("0.0100"), null),
                LocalDateTime.of(2026, 7, 20, 10, 0),
                null);
    }

    private AvaliacaoPosturalUpdateDTO updateDTO(List<LandmarkDTO> landmarks) {
        return new AvaliacaoPosturalUpdateDTO(new BigDecimal("0.502"), null, null, "Observação", landmarks);
    }

    @Test
    void criar_comDadosValidos_deveRetornar201ComHeaderLocation() throws Exception {
        when(service.criar(any())).thenReturn(responseDTO(StatusAvaliacaoPostural.RASCUNHO));

        mvc.perform(post("/avaliacoes-posturais")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new AvaliacaoPosturalRequestDTO(1L, VistaPostural.FRENTE))))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.vista").value("FRENTE"))
                .andExpect(jsonPath("$.status").value("RASCUNHO"))
                .andExpect(jsonPath("$.temFoto").value(true))
                .andExpect(jsonPath("$.metricas.desnivelOmbrosGraus").value(2.30))
                .andExpect(jsonPath("$.metricas.desnivelQuadrilGraus").doesNotExist());
    }

    @Test
    void criar_semAvaliacaoFisioterapeuticaId_deveRetornar400() throws Exception {
        mvc.perform(post("/avaliacoes-posturais")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                mapper.writeValueAsString(new AvaliacaoPosturalRequestDTO(null, VistaPostural.FRENTE))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void criar_comVistaInvalida_deveRetornar400() throws Exception {
        mvc.perform(post("/avaliacoes-posturais")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"avaliacaoFisioterapeuticaId\":1,\"vista\":\"DIAGONAL\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void criar_comAvaliacaoInexistente_deveRetornar404() throws Exception {
        when(service.criar(any()))
                .thenThrow(new ResourceNotFoundException("Avaliação fisioterapêutica não encontrada: 99"));

        mvc.perform(post("/avaliacoes-posturais")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new AvaliacaoPosturalRequestDTO(99L, VistaPostural.FRENTE))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.erro").value("Avaliação fisioterapêutica não encontrada: 99"));
    }

    @Test
    void criar_comVistaDuplicada_deveRetornar409() throws Exception {
        when(service.criar(any()))
                .thenThrow(new ConflictException("Avaliação já possui análise postural ativa da vista FRENTE"));

        mvc.perform(post("/avaliacoes-posturais")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new AvaliacaoPosturalRequestDTO(1L, VistaPostural.FRENTE))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erro").value("Avaliação já possui análise postural ativa da vista FRENTE"));
    }

    @Test
    void buscarPorId_quandoExistente_deveRetornar200ComMetricas() throws Exception {
        when(service.buscarPorId(10L)).thenReturn(responseDTO(StatusAvaliacaoPostural.RASCUNHO));

        mvc.perform(get("/avaliacoes-posturais/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.landmarks[0].codigo").value("OMBRO_ESQ"))
                .andExpect(jsonPath("$.landmarks[0].x").value(0.401))
                .andExpect(jsonPath("$.metricas.desvioPrumoNormalizado").value(0.0100))
                .andExpect(jsonPath("$.metricas.desvioPrumoCm").doesNotExist());
    }

    @Test
    void buscarPorId_quandoInexistenteOuCancelada_deveRetornar404() throws Exception {
        when(service.buscarPorId(99L)).thenThrow(new ResourceNotFoundException("Análise postural não encontrada: 99"));

        mvc.perform(get("/avaliacoes-posturais/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.erro").value("Análise postural não encontrada: 99"));
    }

    @Test
    void listarPorAvaliacaoFisioterapeutica_deveRetornar200() throws Exception {
        when(service.listarPorAvaliacaoFisioterapeutica(1L))
                .thenReturn(List.of(responseDTO(StatusAvaliacaoPostural.RASCUNHO)));

        mvc.perform(get("/avaliacoes-posturais/avaliacao-fisioterapeutica/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].avaliacaoFisioterapeuticaId").value(1))
                .andExpect(jsonPath("$[0].vista").value("FRENTE"));
    }

    @Test
    void atualizar_comLandmarksValidos_deveRetornar200() throws Exception {
        when(service.atualizar(eq(10L), any())).thenReturn(responseDTO(StatusAvaliacaoPostural.RASCUNHO));

        var dto = updateDTO(
                List.of(new LandmarkDTO(CodigoLandmark.OMBRO_ESQ, new BigDecimal("0.401"), new BigDecimal("0.232"))));

        mvc.perform(put("/avaliacoes-posturais/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linhaPrumoX").value(0.502))
                .andExpect(jsonPath("$.metricas.desnivelOmbrosGraus").value(2.30));
    }

    @Test
    void atualizar_comCoordenadaForaDoIntervalo_deveRetornar400() throws Exception {
        var dto = updateDTO(
                List.of(new LandmarkDTO(CodigoLandmark.OMBRO_ESQ, new BigDecimal("1.2"), new BigDecimal("0.232"))));

        mvc.perform(put("/avaliacoes-posturais/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void atualizar_comLinhaPrumoForaDoIntervalo_deveRetornar400() throws Exception {
        var dto = new AvaliacaoPosturalUpdateDTO(new BigDecimal("1.5"), null, null, null, null);

        mvc.perform(put("/avaliacoes-posturais/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void atualizar_comCalibracaoNegativa_deveRetornar400() throws Exception {
        var dto = new AvaliacaoPosturalUpdateDTO(null, new BigDecimal("-1"), null, null, null);

        mvc.perform(put("/avaliacoes-posturais/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void atualizar_comLandmarkDeOutraVista_deveRetornar422() throws Exception {
        when(service.atualizar(eq(10L), any()))
                .thenThrow(new BusinessException("Ponto ORELHA não pertence à vista FRENTE"));

        var dto = updateDTO(
                List.of(new LandmarkDTO(CodigoLandmark.ORELHA, new BigDecimal("0.4"), new BigDecimal("0.2"))));

        mvc.perform(put("/avaliacoes-posturais/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(dto)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.erro").value("Ponto ORELHA não pertence à vista FRENTE"));
    }

    @Test
    void atualizar_emAnaliseConcluida_deveRetornar422() throws Exception {
        when(service.atualizar(eq(10L), any()))
                .thenThrow(new BusinessException("Análise postural concluída não pode ser alterada: 10"));

        mvc.perform(put("/avaliacoes-posturais/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(updateDTO(null))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void concluir_comAnaliseCompleta_deveRetornar200ComStatusConcluida() throws Exception {
        when(service.concluir(10L)).thenReturn(responseDTO(StatusAvaliacaoPostural.CONCLUIDA));

        mvc.perform(patch("/avaliacoes-posturais/10/concluir"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONCLUIDA"));
    }

    @Test
    void concluir_comPontosIncompletos_deveRetornar422() throws Exception {
        when(service.concluir(10L))
                .thenThrow(new BusinessException("Pontos obrigatórios não marcados: [TORNOZELO_ESQ]"));

        mvc.perform(patch("/avaliacoes-posturais/10/concluir"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.erro").value("Pontos obrigatórios não marcados: [TORNOZELO_ESQ]"));
    }

    @Test
    void concluir_comAnaliseInexistente_deveRetornar404() throws Exception {
        when(service.concluir(99L)).thenThrow(new ResourceNotFoundException("Análise postural não encontrada: 99"));

        mvc.perform(patch("/avaliacoes-posturais/99/concluir")).andExpect(status().isNotFound());
    }

    @Test
    void cancelar_deveRetornar200() throws Exception {
        when(service.cancelar(10L)).thenReturn(responseDTO(StatusAvaliacaoPostural.RASCUNHO));

        mvc.perform(patch("/avaliacoes-posturais/10/cancelar")).andExpect(status().isOk());
    }

    @Test
    void cancelar_comAnaliseInexistente_deveRetornar404() throws Exception {
        when(service.cancelar(99L)).thenThrow(new ResourceNotFoundException("Análise postural não encontrada: 99"));

        mvc.perform(patch("/avaliacoes-posturais/99/cancelar")).andExpect(status().isNotFound());
    }
}
