package com.carlesso.pilatesapi.controller;

import com.carlesso.pilatesapi.dto.PreferenciasUsuarioRequestDTO;
import com.carlesso.pilatesapi.dto.PreferenciasUsuarioResponseDTO;
import com.carlesso.pilatesapi.entity.enums.IdiomaPreferencia;
import com.carlesso.pilatesapi.entity.enums.TemaPreferencia;
import com.carlesso.pilatesapi.service.CustomUserDetailsService;
import com.carlesso.pilatesapi.service.JwtService;
import com.carlesso.pilatesapi.service.PreferenciasUsuarioService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PreferenciasUsuarioController.class)
@AutoConfigureMockMvc(addFilters = false)
class PreferenciasUsuarioControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PreferenciasUsuarioService service;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @Autowired
    private ObjectMapper mapper;

    private Authentication userAuth(String email) {
        return new UsernamePasswordAuthenticationToken(
                email, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    @Test
    void consultar_deveRetornar200ComPreferencias() throws Exception {
        var response = new PreferenciasUsuarioResponseDTO(
                IdiomaPreferencia.PT_BR, TemaPreferencia.CLARO, true, false);
        when(service.buscarPorEmail("user@email.com")).thenReturn(response);

        mvc.perform(get("/users/me/preferencias")
                        .principal(userAuth("user@email.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idioma").value("PT_BR"))
                .andExpect(jsonPath("$.tema").value("CLARO"))
                .andExpect(jsonPath("$.notificacoesEmail").value(true))
                .andExpect(jsonPath("$.notificacoesPush").value(false));
    }

    @Test
    void atualizar_comDadosValidos_deveRetornar200() throws Exception {
        var dto = new PreferenciasUsuarioRequestDTO(
                IdiomaPreferencia.EN_US, TemaPreferencia.ESCURO, false, true);
        var response = new PreferenciasUsuarioResponseDTO(
                IdiomaPreferencia.EN_US, TemaPreferencia.ESCURO, false, true);
        when(service.atualizarPorEmail(eq("user@email.com"), any())).thenReturn(response);

        mvc.perform(put("/users/me/preferencias")
                        .principal(userAuth("user@email.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idioma").value("EN_US"))
                .andExpect(jsonPath("$.tema").value("ESCURO"))
                .andExpect(jsonPath("$.notificacoesEmail").value(false))
                .andExpect(jsonPath("$.notificacoesPush").value(true));

        verify(service).atualizarPorEmail(eq("user@email.com"), any());
    }

    @Test
    void atualizar_comIdiomaInvalido_deveRetornar400() throws Exception {
        String payload = """
                {
                  "idioma": "INVALIDO",
                  "tema": "CLARO",
                  "notificacoesEmail": true,
                  "notificacoesPush": true
                }
                """;

        mvc.perform(put("/users/me/preferencias")
                        .principal(userAuth("user@email.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());

        verify(service, never()).atualizarPorEmail(any(), any());
    }

    @Test
    void atualizar_comTemaInvalido_deveRetornar400() throws Exception {
        String payload = """
                {
                  "idioma": "PT_BR",
                  "tema": "NEON",
                  "notificacoesEmail": true,
                  "notificacoesPush": true
                }
                """;

        mvc.perform(put("/users/me/preferencias")
                        .principal(userAuth("user@email.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());

        verify(service, never()).atualizarPorEmail(any(), any());
    }

    @Test
    void atualizar_comCampoObrigatorioFaltando_deveRetornar400() throws Exception {
        String payload = """
                {
                  "idioma": "PT_BR",
                  "tema": "CLARO"
                }
                """;

        mvc.perform(put("/users/me/preferencias")
                        .principal(userAuth("user@email.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());

        verify(service, never()).atualizarPorEmail(any(), any());
    }
}
