package com.carlesso.pilatesapi.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Fixa a forma do corpo de {@code PATCH /aulas/{id}/remarcar} no OpenAPI publicado.
 *
 * <p>Existe por causa de {@code horario}: se o springdoc expandir {@code LocalTime} no objeto
 * {@code {hour, minute, second, nano}}, um cliente gerado a partir do contrato manda esse objeto em vez de
 * {@code "08:00"}. O erro é invisível na suíte comum — só aparece no JSON do schema —, então é aqui que ele fica preso.
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class OpenApiRemarcacaoContratoTest {

    private static final String SCHEMA = "$.components.schemas.AulaRemarcacaoRequestDTO";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void corpoDaRemarcacao_publicaDataEHorarioComOsTiposCertos() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(SCHEMA + ".properties.data.type").value("string"))
                .andExpect(jsonPath(SCHEMA + ".properties.data.format").value("date"))
                .andExpect(jsonPath(SCHEMA + ".properties.horario.type").value("string"))
                .andExpect(jsonPath(SCHEMA + ".properties.horario.format").value("time"))
                .andExpect(jsonPath(SCHEMA + ".required").value("data"));
    }
}
