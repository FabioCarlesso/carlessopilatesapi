package com.carlesso.pilatesapi.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * O contrato publicado descreve o locking otimista da #101: a {@code version} dos updates é opcional e nasce com
 * exemplo {@code 0}, e todo update que a aceita declara o {@code 409} — com exemplo {@code concurrent-modification} —
 * sem perder o {@code 200}.
 *
 * <p>Sem o {@code @Schema}, o "Try it out" do Swagger preenchia {@code version} com {@code 9007199254740991} — o
 * maior inteiro seguro do JavaScript —, e todo update de exemplo voltava {@code 409} sem que o {@code 409} constasse
 * do contrato.
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class OpenApiVersaoContratoTest {

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest
    @ValueSource(
            strings = {
                "PacienteUpdateDTO",
                "ProfissionalUpdateDTO",
                "AnamneseUpdateDTO",
                "AvaliacaoFisioterapeuticaUpdateDTO",
                "PlanoTratamentoUpdateDTO",
                "SessaoPilatesUpdateDTO",
                "EvolucaoSessaoUpdateDTO",
                "ReavaliacaoUpdateDTO",
                "UserUpdateDTO",
                "PreferenciasUsuarioRequestDTO"
            })
    void versionDoUpdate_publicaExemploZeroEOpcional(String schema) throws Exception {
        String dto = "$.components.schemas." + schema;
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(dto + ".properties.version.example").value(0))
                .andExpect(jsonPath(dto + ".properties.version.description").isNotEmpty())
                .andExpect(jsonPath(dto + ".required[?(@ == 'version')]").doesNotExist());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "/pacientes/{id}",
                "/profissionais/{id}",
                "/anamneses/{id}",
                "/avaliacoes-fisioterapeuticas/{id}",
                "/planos-tratamento/{id}",
                "/sessoes/{id}",
                "/evolucoes-sessao/{id}",
                "/reavaliacoes/{id}",
                "/users/{id}",
                "/users/me/preferencias"
            })
    void updateComVersion_declara409ComExemploDeConcorrenciaSemPerderO200(String rota) throws Exception {
        String respostas = "$.paths['" + rota + "'].put.responses";
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(respostas + "['200']").exists())
                .andExpect(jsonPath(respostas + "['409'].content['application/problem+json'].example.type")
                        .value("https://carlessopilates.com.br/problemas/concurrent-modification"));
    }

    /** Rota sem {@code version} no corpo segue com o exemplo genérico do 409. */
    @Test
    void conflitoSemVersion_mantemExemploGenerico() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/aulas/{id}/remarcar'].patch.responses['409']"
                                + ".content['application/problem+json'].example.type")
                        .value("https://carlessopilates.com.br/problemas/conflict"));
    }
}
