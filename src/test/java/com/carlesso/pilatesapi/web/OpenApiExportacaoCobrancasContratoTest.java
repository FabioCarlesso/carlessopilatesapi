package com.carlesso.pilatesapi.web;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Fixa o {@code sort} de {@code GET /pagamentos/xlsx} (#232) no OpenAPI publicado.
 *
 * <p>Sem {@code @ParameterObject}, o springdoc publicava o {@code Sort} como um objeto obrigatório, e o "Try it out" do
 * Swagger mandava {@code sort=["string"]}, que a API recusa com {@code 400}. O parâmetro tem de sair como na listagem:
 * opcional, lista de {@code campo,direção}.
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class OpenApiExportacaoCobrancasContratoTest {

    private static final String SORT = "$.paths['/pagamentos/xlsx'].get.parameters[?(@.name == 'sort')]";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void exportacaoCobrancas_publicaSortOpcionalComoListaDeTexto() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(SORT, hasSize(1)))
                .andExpect(jsonPath(SORT + ".in", contains("query")))
                .andExpect(jsonPath(SORT + ".required", contains(false)))
                .andExpect(jsonPath(SORT + ".schema.type", contains("array")))
                .andExpect(jsonPath(SORT + ".schema.items.type", contains("string")));
    }
}
