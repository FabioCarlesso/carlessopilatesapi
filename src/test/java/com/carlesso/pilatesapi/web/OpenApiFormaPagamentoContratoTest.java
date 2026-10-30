package com.carlesso.pilatesapi.web;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Fixa {@code formaPagamento} (#215) no OpenAPI publicado: o enum no corpo da confirmação, na resposta e no filtro da
 * listagem global. O frontend (carlessopilatesfe#132) monta o seletor a partir desses valores, e a forma é opcional na
 * confirmação até ele ser publicado.
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class OpenApiFormaPagamentoContratoTest {

    private static final String[] FORMAS = {"PIX", "CARTAO_CREDITO", "CARTAO_DEBITO", "DINHEIRO", "TRANSFERENCIA"};

    @Autowired
    private MockMvc mockMvc;

    @Test
    void confirmacaoListagemEResposta_publicamOEnumDaFormaDePagamento() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.PagamentoPagarRequestDTO.properties.formaPagamento.enum")
                        .value(contains(FORMAS)))
                .andExpect(jsonPath("$.components.schemas.PagamentoPagarRequestDTO.required")
                        .doesNotExist())
                .andExpect(jsonPath("$.components.schemas.PagamentoResponseDTO.properties.formaPagamento.enum")
                        .value(contains(FORMAS)))
                .andExpect(jsonPath("$.paths['/pagamentos'].get.parameters[?(@.name == 'formaPagamento')].required")
                        .value(not(hasItem(true))))
                .andExpect(
                        jsonPath("$.paths['/pagamentos'].get.parameters[?(@.name == 'formaPagamento')].schema.enum[*]")
                                .value(contains(FORMAS)));
    }
}
