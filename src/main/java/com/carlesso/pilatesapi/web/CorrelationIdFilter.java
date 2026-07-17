package com.carlesso.pilatesapi.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gera (ou propaga) um correlation-id por requisição, publicando-o no MDC sob a
 * chave {@link #MDC_KEY} para amarrar os logs de uma mesma requisição ponta a
 * ponta. O identificador chega/parte pelo header {@link #HEADER_NAME}, sempre
 * devolvido na resposta para correlação com o frontend.
 *
 * <p>Executa antes de qualquer outro filtro ({@link Ordered#HIGHEST_PRECEDENCE})
 * para que autenticação e regras de negócio já enxerguem o id nos logs.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    private static final int MAX_LENGTH = 64;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String correlationId = resolveCorrelationId(request.getHeader(HEADER_NAME));
        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER_NAME, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private String resolveCorrelationId(String headerValue) {
        if (!StringUtils.hasText(headerValue)) {
            return UUID.randomUUID().toString();
        }
        // Sanitiza o valor recebido para evitar log injection e limita o tamanho.
        String sanitized = headerValue.replaceAll("[^\\w-]", "").trim();
        if (sanitized.isEmpty()) {
            return UUID.randomUUID().toString();
        }
        return sanitized.length() > MAX_LENGTH ? sanitized.substring(0, MAX_LENGTH) : sanitized;
    }
}
