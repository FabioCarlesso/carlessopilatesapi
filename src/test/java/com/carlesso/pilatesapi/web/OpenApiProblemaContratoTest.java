package com.carlesso.pilatesapi.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carlesso.pilatesapi.exception.TipoProblema;
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

    @Test
    void errosDeAutorizacao_documentadosNasRotasProtegidas() throws Exception {
        String anamnese = "$.paths['/anamneses/{id}'].get.responses";
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(anamnese + "['403'].content" + PROBLEM_JSON + ".example.type")
                        .value(TipoProblema.ACESSO_NEGADO.uri().toString()))
                .andExpect(jsonPath(anamnese + "['401'].content" + PROBLEM_JSON + ".example.type")
                        .value(TipoProblema.NAO_AUTENTICADO.uri().toString()))
                // /users/me aceita qualquer autenticado e o login é público: nenhum dos dois responde 403. O 401 do
                // login é o de credenciais inválidas, declarado pelo próprio controller.
                .andExpect(jsonPath("$.paths['/users/me'].get.responses['403']").doesNotExist())
                .andExpect(jsonPath("$.paths['/users/me'].get.responses['401']").exists())
                .andExpect(
                        jsonPath("$.paths['/auth/login'].post.responses['403']").doesNotExist())
                .andExpect(jsonPath("$.paths['/auth/login'].post.responses['401'].description")
                        .value("Credenciais inválidas"));
    }

    @Test
    void exemploDeCadaResposta_condizComOCodigo() throws Exception {
        String resposta404 = "$.paths['/pacientes/{id}'].get.responses['404'].content" + PROBLEM_JSON + ".example";
        String resposta400 = "$.paths['/pacientes'].post.responses['400'].content" + PROBLEM_JSON + ".example";
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(resposta404 + ".type")
                        .value(TipoProblema.NAO_ENCONTRADO.uri().toString()))
                .andExpect(jsonPath(resposta404 + ".status").value(404))
                .andExpect(jsonPath(resposta404 + ".campos").doesNotExist())
                .andExpect(jsonPath(resposta404 + ".instance").value("/pacientes/1"))
                .andExpect(jsonPath(resposta400 + ".instance").value("/pacientes"))
                .andExpect(jsonPath(resposta400 + ".type")
                        .value(TipoProblema.VALIDACAO.uri().toString()))
                .andExpect(jsonPath(resposta400 + ".campos[0].campo").value("nome"));
    }
}
