package com.carlesso.pilatesapi.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/** Fixa no OpenAPI publicado que as respostas de erro são {@code application/problem+json} com o schema da API. */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class OpenApiProblemaContratoTest {

    private static final String SCHEMA = "$.components.schemas.Problema";
    private static final String PROBLEM_JSON = "['application/problem+json']";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void schemaProblema_publicaCamposDaRfcETimestamp() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(SCHEMA + ".properties.type.format").value("uri"))
                .andExpect(jsonPath(SCHEMA + ".properties.detail.type").value("string"))
                .andExpect(jsonPath(SCHEMA + ".properties.timestamp.format").value("date-time"))
                .andExpect(
                        jsonPath(SCHEMA + ".properties.campos.items.$ref").value("#/components/schemas/CampoInvalido"));
    }

    @Test
    void respostasDeErroDeclaradas_apontamParaOSchemaProblema() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.paths['/pacientes'].post.responses['400'].content" + PROBLEM_JSON + ".schema.$ref")
                                .value("#/components/schemas/Problema"))
                .andExpect(
                        jsonPath("$.paths['/pacientes'].post.responses['500'].content" + PROBLEM_JSON + ".schema.$ref")
                                .value("#/components/schemas/Problema"));
    }
}
