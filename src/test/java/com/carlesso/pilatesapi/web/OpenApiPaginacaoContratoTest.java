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
 * Fixa o tipo dos itens das listagens paginadas no OpenAPI publicado.
 *
 * <p>Com o controller devolvendo {@code Page<T>}, o springdoc publicava um único {@code PagedModel} com
 * {@code content: object[]}, e o contrato não dizia o que vinha na lista. Devolvendo {@code PagedModel<T>} — o mesmo
 * objeto que o modo {@code via-dto} já serializava —, cada rota ganha o seu {@code PagedModel<Dto>} com
 * {@code content} tipado, sem mudar um byte da resposta.
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class OpenApiPaginacaoContratoTest {

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest
    @CsvSource({
        "/pacientes, PacienteResponseDTO",
        "/profissionais, ProfissionalResponseDTO",
        "/users, UserResponseDTO",
        "/pagamentos, PagamentoResponseDTO",
        "/pagamentos/paciente/{pacienteId}, PagamentoResponseDTO",
        "/aulas/paciente/{pacienteId}, AulaResponseDTO",
        "/sessoes/paciente/{pacienteId}, SessaoPilatesResponseDTO",
        "/avaliacoes-fisioterapeuticas/paciente/{pacienteId}, AvaliacaoFisioterapeuticaResponseDTO",
        "/reavaliacoes/paciente/{pacienteId}, ReavaliacaoResponseDTO",
        "/planos-tratamento/paciente/{pacienteId}, PlanoTratamentoResponseDTO",
        "/api/nfse-emitidas/paciente/{pacienteId}, NotaFiscalEmitidaResponseDTO"
    })
    void listagemPaginada_publicaPaginaComItensTipados(String rota, String dto) throws Exception {
        String pagina = "PagedModel" + dto;
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['" + rota + "'].get.responses['200'].content['*/*'].schema.$ref")
                        .value("#/components/schemas/" + pagina))
                .andExpect(jsonPath("$.components.schemas." + pagina + ".properties.content.items.$ref")
                        .value("#/components/schemas/" + dto))
                .andExpect(jsonPath("$.components.schemas." + pagina + ".properties.page.$ref")
                        .value("#/components/schemas/PageMetadata"));
    }
}
