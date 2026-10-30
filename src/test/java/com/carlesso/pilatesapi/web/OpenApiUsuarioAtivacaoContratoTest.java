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
 * A inativação de usuários passou para {@code PATCH /users/{id}/inativar} (#211); o {@code DELETE} continua de pé
 * por compatibilidade, mas o contrato precisa avisar que ele é legado para o frontend migrar.
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class OpenApiUsuarioAtivacaoContratoTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void deleteDeUsuario_apareceComoDeprecated() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/users/{id}'].delete.deprecated").value(true))
                .andExpect(jsonPath("$.paths['/users/{id}'].get.deprecated").doesNotExist());
    }

    @Test
    void rotasDeAtivacao_saoPublicadasSemDeprecated() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/users/{id}/inativar'].patch.responses['204']")
                        .exists())
                .andExpect(jsonPath("$.paths['/users/{id}/inativar'].patch.responses['422']")
                        .exists())
                .andExpect(jsonPath("$.paths['/users/{id}/inativar'].patch.deprecated")
                        .doesNotExist())
                .andExpect(jsonPath("$.paths['/users/{id}/ativar'].patch.responses['204']")
                        .exists())
                .andExpect(jsonPath("$.paths['/users/{id}/ativar'].patch.deprecated")
                        .doesNotExist());
    }
}
