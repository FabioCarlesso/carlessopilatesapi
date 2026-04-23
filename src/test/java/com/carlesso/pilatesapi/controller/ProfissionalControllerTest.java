package com.carlesso.pilatesapi.controller;

import com.carlesso.pilatesapi.dto.ProfissionalRequestDTO;
import com.carlesso.pilatesapi.dto.ProfissionalResponseDTO;
import com.carlesso.pilatesapi.dto.ProfissionalUpdateDTO;
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
import static org.mockito.Mockito.doNothing;
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
                "PJ", new BigDecimal("45.00"), LocalDate.of(2024, 1, 15), true);
    }

    @Test
    void cadastrar_deveRetornar201() throws Exception {
        var request = new ProfissionalRequestDTO("Paula Mendes", "paula@email.com", "12345678900", "11999999999",
                "PJ", new BigDecimal("45.00"), LocalDate.of(2024, 1, 15));
        when(service.cadastrar(any())).thenReturn(response());

        mvc.perform(post("/profissionais")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.nome").value("Paula Mendes"));
    }

    @Test
    void listar_deveRetornar200() throws Exception {
        when(service.listar(any())).thenReturn(new PageImpl<>(List.of(response()), PageRequest.of(0, 10), 1));

        mvc.perform(get("/profissionais"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].tipoContrato").value("PJ"));
    }

    @Test
    void buscar_naoEncontrado_deveRetornar404() throws Exception {
        when(service.buscarPorId(eq(99L))).thenThrow(new EntityNotFoundException("Profissional não encontrado: 99"));

        mvc.perform(get("/profissionais/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void atualizar_deveRetornar200() throws Exception {
        var update = new ProfissionalUpdateDTO("Novo Nome", null, null, null, null, null);
        when(service.atualizar(eq(1L), any())).thenReturn(new ProfissionalResponseDTO(1L, "Novo Nome", "paula@email.com",
                "12345678900", "11999999999", "PJ", new BigDecimal("45.00"), LocalDate.of(2024, 1, 15), true));

        mvc.perform(put("/profissionais/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Novo Nome"));
    }

    @Test
    void inativar_deveRetornar204() throws Exception {
        doNothing().when(service).inativar(1L);

        mvc.perform(patch("/profissionais/1/inativar"))
                .andExpect(status().isNoContent());
    }
}
