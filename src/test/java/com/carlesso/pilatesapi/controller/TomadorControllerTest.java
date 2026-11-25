package com.carlesso.pilatesapi.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.carlesso.pilatesapi.dto.TomadorPacienteDTO;
import com.carlesso.pilatesapi.dto.TomadorResponseDTO;
import com.carlesso.pilatesapi.service.CustomUserDetailsService;
import com.carlesso.pilatesapi.service.JwtService;
import com.carlesso.pilatesapi.service.TomadorService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TomadorController.class)
@AutoConfigureMockMvc(addFilters = false)
class TomadorControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private TomadorService service;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    private TomadorResponseDTO response() {
        return new TomadorResponseDTO(3L, "Empresa Ltda", "11222333000181", null, null, null, true, 2L, 0L);
    }

    @Test
    void cadastrar_deveRetornar201ComLocation() throws Exception {
        when(service.cadastrar(any())).thenReturn(response());

        mvc.perform(post("/tomadores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Empresa Ltda\",\"cpfCnpj\":\"11.222.333/0001-81\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/tomadores/3")))
                .andExpect(jsonPath("$.quantidadePacientes").value(2));
    }

    @Test
    void cadastrar_semNome_deveRetornar400() throws Exception {
        mvc.perform(post("/tomadores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cpfCnpj\":\"11222333000181\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listarPacientes_deveRetornarOsVinculados() throws Exception {
        when(service.listarPacientes(3L)).thenReturn(List.of(new TomadorPacienteDTO(7L, "Ana", true, true)));

        mvc.perform(get("/tomadores/3/pacientes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nome").value("Ana"));
    }

    @Test
    void inativar_deveRetornar204() throws Exception {
        mvc.perform(patch("/tomadores/3/inativar")).andExpect(status().isNoContent());
    }
}
