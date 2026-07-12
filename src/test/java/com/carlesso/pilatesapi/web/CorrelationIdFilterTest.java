package com.carlesso.pilatesapi.web;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void semHeader_geraCorrelationIdEDevolveNaResposta() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> mdcDuranteRequisicao = new AtomicReference<>();

        FilterChain chain = mock(FilterChain.class);
        doAnswer(invocation -> {
            mdcDuranteRequisicao.set(MDC.get(CorrelationIdFilter.MDC_KEY));
            return null;
        }).when(chain).doFilter(any(), any());

        filter.doFilter(request, response, chain);

        String responseId = response.getHeader(CorrelationIdFilter.HEADER_NAME);
        assertThat(responseId).isNotBlank();
        assertThat(mdcDuranteRequisicao.get()).isEqualTo(responseId);
        verify(chain).doFilter(request, response);
    }

    @Test
    void comHeader_propagaMesmoCorrelationId() throws Exception {
        String requestId = UUID.randomUUID().toString();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER_NAME, requestId);
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(CorrelationIdFilter.HEADER_NAME)).isEqualTo(requestId);
    }

    @Test
    void headerComCaracteresInvalidos_ehSanitizado() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER_NAME, "abc\r\ndef ghi");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, mock(FilterChain.class));

        assertThat(response.getHeader(CorrelationIdFilter.HEADER_NAME)).isEqualTo("abcdefghi");
    }

    @Test
    void limpaMdcAoFinalDaRequisicao() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, mock(FilterChain.class));

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }
}
