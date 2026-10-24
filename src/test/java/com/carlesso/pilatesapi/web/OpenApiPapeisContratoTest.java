package com.carlesso.pilatesapi.web;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carlesso.pilatesapi.entity.enums.Role;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
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
    void papeisDeEntrada_anunciamSoOsPapeisVisiveis(String dto) throws Exception {
        // Esperado derivado do enum: um papel visível novo sem entrar no allowableValues dos DTOs quebra aqui.
        Object[] visiveis = Arrays.stream(Role.values())
                .filter(Role::isVisivel)
                .map(Role::name)
                .toArray();

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas." + dto + ".properties.roles.items.enum[*]")
                        .value(containsInAnyOrder(visiveis)))
                .andExpect(jsonPath("$.components.schemas." + dto + ".properties.roles.items.type")
                        .value("string"));
    }

    @Test
    void papeisDaEdicao_mantemADescricao() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.components.schemas.UserUpdateDTO.properties.roles.description")
                        .value("Substitui o conjunto inteiro de papéis. Ausente: papéis inalterados"));
    }

    @Test
    void receitaDoResumo_eAnulavelNoContrato() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.components.schemas.PagamentosResumo.properties.receitaMesAtual.type[*]")
                        .value(containsInAnyOrder("number", "null")));
    }
}
