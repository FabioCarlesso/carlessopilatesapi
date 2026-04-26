package com.carlesso.pilatesapi.controller;

import com.carlesso.pilatesapi.dto.ProfissionalPagamentoRelatorioDTO;
import com.carlesso.pilatesapi.dto.ProfissionalRequestDTO;
import com.carlesso.pilatesapi.dto.ProfissionalResponseDTO;
import com.carlesso.pilatesapi.dto.ProfissionalUpdateDTO;
import com.carlesso.pilatesapi.entity.enums.TipoContrato;
import com.carlesso.pilatesapi.service.ProfissionalService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProfissionalController.class)
class ProfissionalControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ProfissionalService service;

    @Autowired
    private ObjectMapper mapper;

    private ProfissionalResponseDTO response() {
        return new ProfissionalResponseDTO(1L, "Paula Mendes", "paula@email.com", "12345678900", "11999999999",
                TipoContrato.PJ, new BigDecimal("45.00"), LocalDate.of(2024, 1, 15), true);
    }

    @Test
    void cadastrar_deveRetornar201() throws Exception {
        var request = new ProfissionalRequestDTO("Paula Mendes", "paula@email.com", "12345678900", "11999999999",
                TipoContrato.PJ, new BigDecimal("45.00"), LocalDate.of(2024, 1, 15));
        when(service.cadastrar(any())).thenReturn(response());

        mvc.perform(post("/profissionais")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.nome").value("Paula Mendes"))
                .andExpect(jsonPath("$.tipoContrato").value("PJ"));
    }

    @Test
    void listar_deveRetornar200() throws Exception {
        when(service.listar(any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(response()), PageRequest.of(0, 10), 1));

        mvc.perform(get("/profissionais"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].tipoContrato").value("PJ"))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void listar_comFiltros_deveRepassarParametrosParaService() throws Exception {
        when(service.listar(any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(response()), PageRequest.of(0, 10), 1));

        mvc.perform(get("/profissionais")
                        .param("nome", "paula")
                        .param("email", "email.com")
                        .param("tipoContrato", "PJ")
                        .param("percentualPagamentoAula", "45.00")
                        .param("ativo", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].nome").value("Paula Mendes"));

        verify(service).listar(
                eq("paula"),
                eq("email.com"),
                eq(TipoContrato.PJ),
                eq(new BigDecimal("45.00")),
                eq(false),
                any());
    }

    @Test
    void buscar_quandoExistente_deveRetornar200() throws Exception {
        when(service.buscarPorId(1L)).thenReturn(response());

        mvc.perform(get("/profissionais/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.cpf").value("12345678900"));
    }

    @Test
    void buscar_quandoNaoExistente_deveRetornar404() throws Exception {
        when(service.buscarPorId(eq(99L))).thenThrow(new EntityNotFoundException("Profissional não encontrado: 99"));

        mvc.perform(get("/profissionais/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.erro").value("Profissional não encontrado: 99"));
    }

    @Test
    void atualizar_deveRetornar200() throws Exception {
        var update = new ProfissionalUpdateDTO("Novo Nome", null, null, null, null, null);
        when(service.atualizar(eq(1L), any())).thenReturn(new ProfissionalResponseDTO(1L, "Novo Nome", "paula@email.com",
                "12345678900", "11999999999", TipoContrato.PJ, new BigDecimal("45.00"), LocalDate.of(2024, 1, 15), true));

        mvc.perform(put("/profissionais/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Novo Nome"));
    }

    @Test
    void atualizar_quandoNaoExistente_deveRetornar404() throws Exception {
        when(service.atualizar(eq(99L), any()))
                .thenThrow(new EntityNotFoundException("Profissional não encontrado: 99"));

        mvc.perform(put("/profissionais/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new ProfissionalUpdateDTO("Nome", null, null, null, null, null))))
                .andExpect(status().isNotFound());
    }

    @Test
    void ativar_deveRetornar204SemCorpo() throws Exception {
        doNothing().when(service).ativar(1L);

        mvc.perform(patch("/profissionais/1/ativar"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    void ativar_quandoNaoExistente_deveRetornar404() throws Exception {
        doThrow(new EntityNotFoundException("Profissional não encontrado: 99"))
                .when(service).ativar(99L);

        mvc.perform(patch("/profissionais/99/ativar"))
                .andExpect(status().isNotFound());
    }

    @Test
    void inativar_deveRetornar204SemCorpo() throws Exception {
        doNothing().when(service).inativar(1L);

        mvc.perform(patch("/profissionais/1/inativar"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    void inativar_quandoNaoExistente_deveRetornar404() throws Exception {
        doThrow(new EntityNotFoundException("Profissional não encontrado: 99"))
                .when(service).inativar(99L);

        mvc.perform(patch("/profissionais/99/inativar"))
                .andExpect(status().isNotFound());
    }

    @Test
    void gerarRelatorioPagamento_deveRetornar200() throws Exception {
        var relatorio = new ProfissionalPagamentoRelatorioDTO(
                1L,
                "Paula Mendes",
                LocalDate.of(2025, 2, 1),
                LocalDate.of(2025, 2, 28),
                2,
                new BigDecimal("22.50"),
                List.of());
        when(service.gerarRelatorioPagamento(1L, LocalDate.of(2025, 2, 1), LocalDate.of(2025, 2, 28)))
                .thenReturn(relatorio);

        mvc.perform(get("/profissionais/1/relatorio-pagamento")
                        .param("inicio", "2025-02-01")
                        .param("fim", "2025-02-28"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profissionalId").value(1))
                .andExpect(jsonPath("$.totalAulas").value(2))
                .andExpect(jsonPath("$.totalPagamento").value(22.50));
    }

    @Test
    void gerarRelatorioPagamento_periodoInvalido_deveRetornar400() throws Exception {
        when(service.gerarRelatorioPagamento(1L, LocalDate.of(2025, 3, 1), LocalDate.of(2025, 2, 28)))
                .thenThrow(new IllegalArgumentException("Período inicial não pode ser maior que o período final"));

        mvc.perform(get("/profissionais/1/relatorio-pagamento")
                        .param("inicio", "2025-03-01")
                        .param("fim", "2025-02-28"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").exists());
    }
}
