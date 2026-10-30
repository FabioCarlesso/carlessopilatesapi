package com.carlesso.pilatesapi.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Todo {@code LocalTime} do contrato publicado é {@code string}/{@code time}, como trafega no JSON ({@code "09:00:00"}).
 *
 * <p>Sem o mapeamento global do {@code OpenApiConfig}, o springdoc expandia o tipo no objeto
 * {@code {hour, minute, second, nano}} em sessões, aulas, planos, bloqueios de agenda e lista de espera — e um cliente gerado a
 * partir do contrato mandaria esse objeto. Mesmo problema que o {@link OpenApiRemarcacaoContratoTest} prende campo a
 * campo, aqui resolvido na raiz.
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class OpenApiHorarioContratoTest {

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest
    @CsvSource({
        "SessaoPilatesResponseDTO, horario",
        "SessaoPilatesRequestDTO, horario",
        "SessaoPilatesUpdateDTO, horario",
        "BloqueioAgendaRequestDTO, horaInicio",
        "BloqueioAgendaResponseDTO, horaFim",
        "ListaEsperaRequestDTO, horaInicio",
        "ListaEsperaResponseDTO, horaFim",
        "AulaResponseDTO, horario",
        "AulaRemarcacaoRequestDTO, horario",
        "PlanoRequestDTO, horario",
        "PlanoResponseDTO, horario"
    })
    void campoLocalTime_publicaStringNoFormatoTime(String schema, String campo) throws Exception {
        String propriedade = "$.components.schemas." + schema + ".properties." + campo;
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(propriedade + ".type").value("string"))
                .andExpect(jsonPath(propriedade + ".format").value("time"));
    }

    @ParameterizedTest
    @CsvSource({"LocalTime"})
    void contrato_naoPublicaOSchemaObjetoDeLocalTime(String schema) throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas." + schema).doesNotExist());
    }
}
