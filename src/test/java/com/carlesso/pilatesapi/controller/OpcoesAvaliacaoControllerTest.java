package com.carlesso.pilatesapi.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carlesso.pilatesapi.dto.OpcoesAvaliacaoResponseDTO;
import com.carlesso.pilatesapi.service.CustomUserDetailsService;
import com.carlesso.pilatesapi.service.JwtService;
import com.carlesso.pilatesapi.service.OpcoesAvaliacaoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OpcoesAvaliacaoController.class)
@AutoConfigureMockMvc(addFilters = false)
class OpcoesAvaliacaoControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private OpcoesAvaliacaoService service;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    void catalogo_devolveOsCamposNoFormatoDoContrato() throws Exception {
        when(service.catalogo()).thenReturn(OpcoesAvaliacaoResponseDTO.deTodosOsCampos());

        mvc.perform(get("/avaliacoes/opcoes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.campos.length()").value(9))
                .andExpect(jsonPath("$.campos[0].campo").value("regioesDor"))
                .andExpect(jsonPath("$.campos[0].rotulo").value("Regiões de dor/queixa"))
                .andExpect(jsonPath("$.campos[0].selecao").value("MULTIPLA"))
                .andExpect(jsonPath("$.campos[0].comResultado").value(false))
                .andExpect(jsonPath("$.campos[0].campoComplemento").value("regioesDorComplemento"))
                .andExpect(jsonPath("$.campos[0].opcoes[0].valor").value("CERVICAL"))
                .andExpect(jsonPath("$.campos[0].opcoes[0].rotulo").value("Cervical"))
                .andExpect(jsonPath("$.campos[0].opcoes[0].ativa").value(true))
                .andExpect(jsonPath("$.campos[?(@.campo == 'nivelAtividadeFisicaOpcao')].selecao")
                        .value("UNICA"))
                .andExpect(jsonPath("$.campos[?(@.campo == 'testesFuncionais')].comResultado")
                        .value(true))
                .andExpect(jsonPath("$.campos[?(@.campo == 'testesFuncionais')].opcoes[3].rotulo")
                        .value("Lasègue"));
    }
}
