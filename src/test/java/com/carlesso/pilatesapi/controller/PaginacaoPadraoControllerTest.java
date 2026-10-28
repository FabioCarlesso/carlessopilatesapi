package com.carlesso.pilatesapi.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carlesso.pilatesapi.service.ContatoCobrancaService;
import com.carlesso.pilatesapi.service.CustomUserDetailsService;
import com.carlesso.pilatesapi.service.JwtService;
import com.carlesso.pilatesapi.service.PacienteService;
import com.carlesso.pilatesapi.service.PagamentoService;
import com.carlesso.pilatesapi.service.ProfissionalService;
import com.carlesso.pilatesapi.service.RelatorioPagamentoExporterService;
import com.carlesso.pilatesapi.service.UserService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * O tamanho de página sem {@code size} na query vem de {@code spring.data.web.pageable.default-page-size}
 * ({@code APP_PAGINACAO_TAMANHO_PADRAO}). Os controllers declaram só a ordenação padrão, com {@code @SortDefault}: um
 * {@code @PageableDefault} fixaria {@code size=10} e ignoraria a variável em silêncio.
 */
@WebMvcTest({PacienteController.class, PagamentoController.class, ProfissionalController.class, UserController.class})
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = "spring.data.web.pageable.default-page-size=25")
class PaginacaoPadraoControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    PacienteService pacienteService;

    @MockitoBean
    PagamentoService pagamentoService;

    @MockitoBean
    ContatoCobrancaService contatoCobrancaService;

    @MockitoBean
    ProfissionalService profissionalService;

    @MockitoBean
    RelatorioPagamentoExporterService relatorioPagamentoExporterService;

    @MockitoBean
    UserService userService;

    @MockitoBean
    JwtService jwtService;

    @MockitoBean
    CustomUserDetailsService customUserDetailsService;

    @Test
    void pacientes_semSize_usaTamanhoConfiguradoEOrdenacaoPadrao() throws Exception {
        when(pacienteService.listar(any(), any(), any(), any(), any(), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/pacientes")).andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(pacienteService).listar(any(), any(), any(), any(), any(), captor.capture());
        assertPagina(captor.getValue(), Sort.by("nome"));
    }

    @Test
    void pagamentos_semSize_usaTamanhoConfiguradoEOrdenacaoPadrao() throws Exception {
        when(pagamentoService.listar(any(), any(), any(), any(), any(), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/pagamentos")).andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(pagamentoService).listar(any(), any(), any(), any(), any(), captor.capture());
        assertPagina(captor.getValue(), Sort.by("dataVencimento"));
    }

    @Test
    void profissionais_semSize_usaTamanhoConfiguradoEOrdenacaoPadrao() throws Exception {
        when(profissionalService.listar(any(), any(), any(), any(), any(), any()))
                .thenReturn(Page.empty());

        mockMvc.perform(get("/profissionais")).andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(profissionalService).listar(any(), any(), any(), any(), any(), captor.capture());
        assertPagina(captor.getValue(), Sort.by("nome"));
    }

    @Test
    void usuarios_semSize_usaTamanhoConfiguradoEOrdenacaoPadrao() throws Exception {
        when(userService.listar(any())).thenReturn(Page.empty());

        mockMvc.perform(get("/users")).andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(userService).listar(captor.capture());
        assertPagina(captor.getValue(), Sort.by("name"));
    }

    private void assertPagina(Pageable pageable, Sort ordenacaoEsperada) {
        assertThat(pageable.getPageSize()).isEqualTo(25);
        assertThat(pageable.getSort()).isEqualTo(ordenacaoEsperada);
    }
}
