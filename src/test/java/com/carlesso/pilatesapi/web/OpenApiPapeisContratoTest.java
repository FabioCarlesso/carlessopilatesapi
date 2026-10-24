package com.carlesso.pilatesapi.web;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * O cadastro de usuário recusa {@code PACIENTE} com 422 (#173), então o OpenAPI de entrada só anuncia os papéis que
 * {@code GET /users/roles} oferece — sem isso o Swagger sugeria um valor que a API rejeita.
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class OpenApiPapeisContratoTest {

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest
    @ValueSource(strings = {"UserRequestDTO", "UserUpdateDTO"})
    void papeisDeEntrada_naoAnunciamPapelReservado(String dto) throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas." + dto + ".properties.roles.items.enum[*]")
                        .value(containsInAnyOrder("ADMIN", "PROFISSIONAL", "RECEPCAO", "FINANCEIRO")));
    }
}
