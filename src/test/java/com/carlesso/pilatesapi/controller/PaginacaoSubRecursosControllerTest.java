package com.carlesso.pilatesapi.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carlesso.pilatesapi.dto.AulaResponseDTO;
import com.carlesso.pilatesapi.service.AulaService;
import com.carlesso.pilatesapi.service.AvaliacaoFisioterapeuticaService;
import com.carlesso.pilatesapi.service.ContatoCobrancaService;
import com.carlesso.pilatesapi.service.CustomUserDetailsService;
import com.carlesso.pilatesapi.service.JwtService;
import com.carlesso.pilatesapi.service.NotaFiscalEmitidaService;
import com.carlesso.pilatesapi.service.PagamentoService;
import com.carlesso.pilatesapi.service.PlanoTratamentoService;
import com.carlesso.pilatesapi.service.ReavaliacaoService;
import com.carlesso.pilatesapi.service.SessaoPilatesService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Listagens por paciente (issue #100): sem parâmetros, cada rota pede ao serviço a página do tamanho configurado em
 * {@code APP_PAGINACAO_TAMANHO_PADRAO} e a ordenação padrão da rota, do mais recente para o mais antigo; com
 * {@code page}, {@code size} e {@code sort}, os valores chegam ao serviço como vieram. A resposta segue o formato de
 * {@code /pacientes}.
 */
@WebMvcTest({
    AulaController.class,
    AvaliacaoFisioterapeuticaController.class,
    NotaFiscalEmitidaController.class,
    PagamentoController.class,
    PlanoTratamentoController.class,
    ReavaliacaoController.class,
    SessaoPilatesController.class
})
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = "spring.data.web.pageable.default-page-size=25")
class PaginacaoSubRecursosControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AulaService aulaService;

    @MockitoBean
    AvaliacaoFisioterapeuticaService avaliacaoService;

    @MockitoBean
    NotaFiscalEmitidaService notaFiscalService;

    @MockitoBean
    PagamentoService pagamentoService;

    @MockitoBean
    ContatoCobrancaService contatoCobrancaService;

    @MockitoBean
    PlanoTratamentoService planoTratamentoService;

    @MockitoBean
    ReavaliacaoService reavaliacaoService;

    @MockitoBean
    SessaoPilatesService sessaoService;

    @MockitoBean
    JwtService jwtService;

    @MockitoBean
    CustomUserDetailsService customUserDetailsService;

    @Test
    void aulas_semParametros_usaTamanhoConfiguradoEDataDesc() throws Exception {
        when(aulaService.buscarPorPaciente(eq(1L), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/aulas/paciente/1")).andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(aulaService).buscarPorPaciente(eq(1L), captor.capture());
        assertPaginaPadrao(captor.getValue(), "data");
    }

    @Test
    void pagamentos_semParametros_usaTamanhoConfiguradoEVencimentoDesc() throws Exception {
        when(pagamentoService.listarPorPaciente(eq(1L), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/pagamentos/paciente/1")).andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(pagamentoService).listarPorPaciente(eq(1L), captor.capture());
        assertPaginaPadrao(captor.getValue(), "dataVencimento");
    }

    @Test
    void sessoes_semParametros_usaTamanhoConfiguradoEDataDesc() throws Exception {
        when(sessaoService.listarPorPaciente(eq(1L), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/sessoes/paciente/1")).andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(sessaoService).listarPorPaciente(eq(1L), captor.capture());
        assertPaginaPadrao(captor.getValue(), "data");
    }

    @Test
    void avaliacoes_semParametros_usaTamanhoConfiguradoEDataAvaliacaoDesc() throws Exception {
        when(avaliacaoService.listarPorPaciente(eq(1L), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/avaliacoes-fisioterapeuticas/paciente/1")).andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(avaliacaoService).listarPorPaciente(eq(1L), captor.capture());
        assertPaginaPadrao(captor.getValue(), "dataAvaliacao");
    }

    @Test
    void reavaliacoes_semParametros_usaTamanhoConfiguradoEDataReavaliacaoDesc() throws Exception {
        when(reavaliacaoService.listarPorPaciente(eq(1L), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/reavaliacoes/paciente/1")).andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(reavaliacaoService).listarPorPaciente(eq(1L), captor.capture());
        assertPaginaPadrao(captor.getValue(), "dataReavaliacao");
    }

    @Test
    void planosTratamento_semParametros_usaTamanhoConfiguradoEDataInicioDesc() throws Exception {
        when(planoTratamentoService.listarPorPaciente(eq(1L), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/planos-tratamento/paciente/1")).andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(planoTratamentoService).listarPorPaciente(eq(1L), captor.capture());
        assertPaginaPadrao(captor.getValue(), "dataInicio");
    }

    @Test
    void notasFiscais_semParametros_usaTamanhoConfiguradoECompetenciaDesc() throws Exception {
        when(notaFiscalService.listarPorPaciente(eq(1L), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/nfse-emitidas/paciente/1")).andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(notaFiscalService).listarPorPaciente(eq(1L), captor.capture());
        assertPaginaPadrao(captor.getValue(), "competencia");
    }

    @Test
    void parametrosExplicitos_chegamAoServicoComoVieram() throws Exception {
        when(aulaService.buscarPorPaciente(eq(1L), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/aulas/paciente/1")
                        .param("page", "2")
                        .param("size", "5")
                        .param("sort", "realizada,asc"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(aulaService).buscarPorPaciente(eq(1L), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(captor.getValue().getPageSize()).isEqualTo(5);
        assertThat(captor.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.ASC, "realizada"));
    }

    @Test
    void resposta_segueFormatoDePaginaComMetadados() throws Exception {
        var aula = new AulaResponseDTO(7L, 1L, "Ana", null, null, 3L, LocalDate.of(2026, 5, 10), false);
        when(aulaService.buscarPorPaciente(eq(1L), any()))
                .thenReturn(new PageImpl<>(List.of(aula), PageRequest.of(1, 10), 11));

        mockMvc.perform(get("/aulas/paciente/1").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(7))
                .andExpect(jsonPath("$.page.number").value(1))
                .andExpect(jsonPath("$.page.size").value(10))
                .andExpect(jsonPath("$.page.totalElements").value(11))
                .andExpect(jsonPath("$.page.totalPages").value(2));
    }

    @Test
    void campoDeOrdenacaoRecusadoPeloServico_retorna400() throws Exception {
        when(sessaoService.listarPorPaciente(eq(1L), any()))
                .thenThrow(new IllegalArgumentException("Campo de ordenação inválido: observacoes"));

        mockMvc.perform(get("/sessoes/paciente/1").param("sort", "observacoes"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Campo de ordenação inválido: observacoes"));
    }

    private void assertPaginaPadrao(Pageable pageable, String campoData) {
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(25);
        assertThat(pageable.getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, campoData));
    }
}
