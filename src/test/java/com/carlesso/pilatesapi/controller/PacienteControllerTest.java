package com.carlesso.pilatesapi.controller;

import com.carlesso.pilatesapi.dto.EnderecoDTO;
import com.carlesso.pilatesapi.dto.PacienteRequestDTO;
import com.carlesso.pilatesapi.dto.PacienteResponseDTO;
import com.carlesso.pilatesapi.dto.PacienteUpdateDTO;
import com.carlesso.pilatesapi.service.PacienteService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PacienteController.class)
class PacienteControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PacienteService service;

    @Autowired
    private ObjectMapper mapper;

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private PacienteResponseDTO responseDTO() {
        return new PacienteResponseDTO(
                1L, "Maria Souza", "maria@email.com", "12345678900",
                "11912345678", LocalDate.of(1990, 5, 20),
                new EnderecoDTO("Rua das Flores", "42", "Centro", "São Paulo", "SP", "01001000"),
                true
        );
    }

    private PacienteRequestDTO requestDTO() {
        return new PacienteRequestDTO(
                "Maria Souza", "maria@email.com", "12345678900",
                "11912345678", LocalDate.of(1990, 5, 20),
                new EnderecoDTO("Rua das Flores", "42", "Centro", "São Paulo", "SP", "01001000")
        );
    }

    // -------------------------------------------------------------------------
    // POST /pacientes
    // -------------------------------------------------------------------------

    @Test
    void cadastrar_comDadosValidos_deveRetornar201ComHeaderLocation() throws Exception {
        when(service.cadastrar(any())).thenReturn(responseDTO());

        mvc.perform(post("/pacientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(requestDTO())))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.nome").value("Maria Souza"))
                .andExpect(jsonPath("$.email").value("maria@email.com"))
                .andExpect(jsonPath("$.ativo").value(true));
    }

    @Test
    void cadastrar_semNome_deveRetornar400() throws Exception {
        var dto = new PacienteRequestDTO(null, "maria@email.com", "12345678900", null, null, null);

        mvc.perform(post("/pacientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cadastrar_semCpf_deveRetornar400() throws Exception {
        var dto = new PacienteRequestDTO("Maria", "maria@email.com", null, null, null, null);

        mvc.perform(post("/pacientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cadastrar_emailInvalido_deveRetornar400() throws Exception {
        var dto = new PacienteRequestDTO("Maria", "nao-e-email", "12345678900", null, null, null);

        mvc.perform(post("/pacientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cadastrar_bodyVazio_deveRetornar400() throws Exception {
        mvc.perform(post("/pacientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------------
    // GET /pacientes
    // -------------------------------------------------------------------------

    @Test
    void listar_deveRetornar200ComPaginaDeResultados() throws Exception {
        var page = new PageImpl<>(List.of(responseDTO()), PageRequest.of(0, 10), 1);
        when(service.listar(any())).thenReturn(page);

        mvc.perform(get("/pacientes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].nome").value("Maria Souza"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void listar_semPacientes_deveRetornar200ComPaginaVazia() throws Exception {
        when(service.listar(any())).thenReturn(new PageImpl<>(List.of()));

        mvc.perform(get("/pacientes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content").isEmpty());
    }

    // -------------------------------------------------------------------------
    // GET /pacientes/{id}
    // -------------------------------------------------------------------------

    @Test
    void buscar_quandoExistente_deveRetornar200ComDados() throws Exception {
        when(service.buscarPorId(1L)).thenReturn(responseDTO());

        mvc.perform(get("/pacientes/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.cpf").value("12345678900"));
    }

    @Test
    void buscar_quandoNaoExistente_deveRetornar404() throws Exception {
        when(service.buscarPorId(99L))
                .thenThrow(new EntityNotFoundException("Paciente não encontrado: 99"));

        mvc.perform(get("/pacientes/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.erro").value("Paciente não encontrado: 99"));
    }

    // -------------------------------------------------------------------------
    // PUT /pacientes/{id}
    // -------------------------------------------------------------------------

    @Test
    void atualizar_deveRetornar200ComDadosAtualizados() throws Exception {
        var update = new PacienteUpdateDTO("Novo Nome", null, null, null, null);
        var updated = new PacienteResponseDTO(1L, "Novo Nome", "maria@email.com",
                "12345678900", "11912345678", LocalDate.of(1990, 5, 20), null, true);
        when(service.atualizar(eq(1L), any())).thenReturn(updated);

        mvc.perform(put("/pacientes/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Novo Nome"))
                .andExpect(jsonPath("$.email").value("maria@email.com"));
    }

    @Test
    void atualizar_quandoNaoExistente_deveRetornar404() throws Exception {
        when(service.atualizar(eq(99L), any()))
                .thenThrow(new EntityNotFoundException("Paciente não encontrado: 99"));

        mvc.perform(put("/pacientes/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new PacienteUpdateDTO("Nome", null, null, null, null))))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------------------
    // DELETE /pacientes/{id}
    // -------------------------------------------------------------------------

    @Test
    void inativar_deveRetornar204SemCorpo() throws Exception {
        doNothing().when(service).inativar(1L);

        mvc.perform(delete("/pacientes/1"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    void inativar_quandoNaoExistente_deveRetornar404() throws Exception {
        doThrow(new EntityNotFoundException("Paciente não encontrado: 99"))
                .when(service).inativar(99L);

        mvc.perform(delete("/pacientes/99"))
                .andExpect(status().isNotFound());
    }
}
