package com.carlesso.pilatesapi.web;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Filtro opcional de relatório não pode publicar {@code example} no OpenAPI.
 *
 * <p>O "Try it out" do Swagger preenche o exemplo no campo, e a consulta sai filtrada sem o usuário pedir: com
 * {@code taxaMaxima=75} pré-preenchido, o relatório de frequência (#233) escondia quem tinha 75% ou mais. O valor de
 * exemplo vai na descrição.
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class OpenApiFiltrosOpcionaisRelatoriosContratoTest {

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest
    @ValueSource(strings = {"/api/relatorios/frequencia", "/api/relatorios/frequencia/xlsx"})
    void taxaMaxima_naoPublicaExemploQueOSwaggerPreencheria(String rota) throws Exception {
        String taxa = "$.paths['" + rota + "'].get.parameters[?(@.name == 'taxaMaxima')]";
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(taxa, hasSize(1)))
                .andExpect(jsonPath(taxa + ".example", everyItem(nullValue())));
    }
}
